package dev.gaferneira.notificapp.features.inbox.viewmodel

import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.preferences.InboxFilterSettings
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import dev.gaferneira.notificapp.features.inbox.contract.InboxEffect
import dev.gaferneira.notificapp.features.inbox.contract.InboxEvent
import dev.gaferneira.notificapp.features.inbox.contract.InboxListItem
import dev.gaferneira.notificapp.features.inbox.contract.InboxUiState
import dev.gaferneira.notificapp.features.inbox.contract.NotificationItem
import dev.gaferneira.notificapp.util.timeAgo
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter as Status

/**
 * ViewModel for the Inbox Screen with Pagination.
 *
 * Implements MVI pattern per ADR 001:
 * - StateFlow for UI state (filter configuration)
 * - Separate Flow<PagingData<InboxListItem>> for paginated content with headers
 * - Channel for effects
 * - Centralized event handling via onEvent()
 *
 * Pagination Strategy:
 * - App and status filters are applied at the database level (SQL WHERE clauses)
 * - Search query filtering is applied in-memory via PagingData.filter operator
 * - Pager is recreated when filters change via flatMapLatest
 * - cachedIn(viewModelScope) preserves data across configuration changes
 * - Time headers are inserted via PagingData.insertSeparators() every 2 hours
 *
 * Spec: openspec/specs/inbox/001-inbox-screen.md
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val listenerStatus: NotificationListenerStatusProvider,
    private val notificationRepository: NotificationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<InboxUiState, InboxEvent, InboxEffect>(InboxUiState()) {

    /**
     * The paginated notification stream with 2-hour time headers.
     * Recreated whenever filters change via flatMapLatest.
     */
    val notifications: Flow<PagingData<InboxListItem>> =
        uiState
            .map { Triple(it.searchQuery, it.selectedApps, it.statusFilter) }
            .distinctUntilChanged()
            .flatMapLatest { (searchQuery, selectedApps, statusFilter) ->
                // Convert Status to Boolean? for repository
                val isProcessed = when (statusFilter) {
                    Status.PROCESSED -> true
                    Status.UNPROCESSED -> false
                    Status.ALL -> null
                }

                // Use search query if provided, otherwise use filtered paged
                val pagingFlow = if (searchQuery.isBlank()) {
                    notificationRepository.observeNotificationsPaged(
                        packageNames = selectedApps,
                        isProcessed = isProcessed,
                    )
                } else {
                    notificationRepository.searchNotificationsPaged(
                        query = searchQuery,
                        packageNames = selectedApps,
                        isProcessed = isProcessed,
                    )
                }

                // Map domain model to UI model and insert time headers
                pagingFlow.map { pagingData ->
                    pagingData
                        .map { it.toNotificationItem() }
                        .map { InboxListItem.NotificationRow(it) }
                        .insertSeparators { before, after ->
                            when {
                                // First item - no header (user request)
                                before == null && after != null -> null
                                // Different 2-hour window - add header
                                before != null &&
                                    after != null &&
                                    !areInSame2HourWindow(before.notification.timestamp, after.notification.timestamp) -> {
                                    val timestamp = after.notification.timestamp
                                    InboxListItem.TimeHeader(
                                        timestamp = roundUpTo2HourWindow(timestamp),
                                        label = formatTimeHeader(timestamp),
                                    )
                                }
                                // Same window or no after - no header
                                else -> null
                            }
                        }
                }
            }
            .cachedIn(viewModelScope)

    init {
        loadSavedFilters()
        checkNotificationListenerStatus()
    }

    /**
     * Load saved filter preferences from the repository.
     */
    private fun loadSavedFilters() {
        viewModelScope.launch {
            userPreferencesRepository.observeInboxFilters()
                .collect { filters ->
                    setState {
                        copy(
                            selectedApps = filters.selectedApps,
                            statusFilter = filters.statusFilter,
                        )
                    }
                }
        }
    }

    override fun onEvent(event: InboxEvent) {
        when (event) {
            is InboxEvent.OnSearchQueryChange -> updateSearchQuery(event.query)
            is InboxEvent.OnAppFilterChange -> updateAppFilter(event.packageNames, event.statusFilter)
            is InboxEvent.OnNotificationClick -> onNotificationClick(event.notificationId)
            is InboxEvent.OnResume -> checkNotificationListenerStatus()
        }
    }

    private fun checkNotificationListenerStatus() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val isListenerActive = listenerStatus.isEnabled()
                setState { copy(isNotificationListenerActive = isListenerActive) }
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to check notification listener status")
            }
        }
    }

    private fun updateSearchQuery(query: String) {
        setState {
            copy(searchQuery = query)
        }
    }

    private fun updateAppFilter(
        packageNames: List<String>,
        newStatusFilter: Status,
    ) {
        viewModelScope.launch {
            val filters = InboxFilterSettings(
                selectedApps = packageNames,
                statusFilter = newStatusFilter,
            )
            userPreferencesRepository.setInboxFilters(filters)
                .onFailure { e ->
                    Timber.e(e, "Failed to save inbox filters")
                }
        }
    }

    private fun onNotificationClick(notificationId: String) {
        sendEffect(InboxEffect.NavigateToNotificationDetail(notificationId))
    }
}

/**
 * Convert domain Notification to UI NotificationItem.
 */
private fun Notification.toNotificationItem(): NotificationItem = NotificationItem(
    id = this.id,
    appName = this.appName,
    appPackageName = this.packageName,
    title = this.title,
    content = this.content,
    timestamp = this.timestamp,
    formattedTime = Date(this.timestamp).timeAgo(showMinutesHours = true),
    isProcessed = this.isProcessed,
    appliedRulesCount = this.appliedRulesCount,
)

/**
 * Round UP a timestamp to the next 2-hour window.
 * For example: 18:34 -> 20:00, 19:15 -> 20:00, 20:01 -> 22:00
 */
private fun roundUpTo2HourWindow(timestamp: Long): Long {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour = calendar.get(Calendar.HOUR_OF_DAY)
    val roundedHour = ((hour + 2) / 2) * 2 // Round up to next even hour
    calendar.set(Calendar.HOUR_OF_DAY, roundedHour)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    return calendar.timeInMillis
}

/**
 * Check if two timestamps are in the same 2-hour window (using round-up).
 */
private fun areInSame2HourWindow(timestamp1: Long, timestamp2: Long): Boolean = roundUpTo2HourWindow(timestamp1) == roundUpTo2HourWindow(timestamp2)

private val timeHeaderFormatter = SimpleDateFormat("d MMMM HH:mm", Locale.getDefault())

/**
 * Format a timestamp as a time header for its next 2-hour window (e.g., "10 March 20:00").
 */
private fun formatTimeHeader(timestamp: Long): String {
    val windowStart = roundUpTo2HourWindow(timestamp)
    return synchronized(timeHeaderFormatter) { timeHeaderFormatter.format(Date(windowStart)) }
}
