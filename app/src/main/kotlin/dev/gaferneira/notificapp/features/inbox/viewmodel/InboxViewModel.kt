package dev.gaferneira.notificapp.features.inbox.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import dev.gaferneira.notificapp.features.inbox.contract.InboxEffect
import dev.gaferneira.notificapp.features.inbox.contract.InboxEvent
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilter
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
@HiltViewModel(assistedFactory = InboxViewModel.Factory::class)
class InboxViewModel @AssistedInject constructor(
    @Assisted initialStatus: NotificationStatusFilter?,
    private val savedStateHandle: SavedStateHandle,
    private val listenerStatus: NotificationListenerStatusProvider,
    private val notificationRepository: NotificationRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<InboxUiState, InboxEvent, InboxEffect>(
    InboxUiState(filter = savedStateHandle.resolveStatusOverride(initialStatus)?.let { InboxFilter(status = it) } ?: InboxFilter()),
) {

    @AssistedFactory
    interface Factory {
        /** [initialStatus] is the route's per-visit status override; null keeps the saved filter. */
        fun create(initialStatus: NotificationStatusFilter?): InboxViewModel
    }

    /**
     * The paginated notification stream with 2-hour time headers.
     * Recreated whenever filters change via flatMapLatest.
     */
    val notifications: Flow<PagingData<InboxListItem>> =
        uiState
            .map { Triple(it.searchQuery, it.filter.selectedApps.sorted(), it.filter.status) }
            .distinctUntilChanged()
            .flatMapLatest { (searchQuery, selectedApps, statusFilter) ->
                val isProcessed = statusFilter.toIsProcessed()

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

    /**
     * Per-visit status from the route (e.g. Home's "Rules fired" tile). While set it replaces the
     * saved filter in the state but is never persisted; the first filter edit drops it. Kept in
     * [SavedStateHandle] so process death doesn't re-apply the route's status over the user's edits.
     */
    private var initialStatusOverride: NotificationStatusFilter?
        get() = savedStateHandle[KEY_STATUS_OVERRIDE]
        set(value) {
            savedStateHandle[KEY_STATUS_OVERRIDE] = value
        }

    init {
        loadSavedFilters()
        loadAppNames()
        checkNotificationListenerStatus()
    }

    /**
     * Load saved filter preferences from the repository.
     */
    private fun loadSavedFilters() {
        viewModelScope.launch {
            userPreferencesRepository.observeInboxFilters()
                .collect { filters ->
                    val override = initialStatusOverride
                    setState { copy(filter = override?.let { InboxFilter(status = it) } ?: filters.toInboxFilter()) }
                }
        }
    }

    /** Display names for the active-filter chips. */
    private fun loadAppNames() {
        viewModelScope.launch {
            notificationRepository.observeAppsWithNotifications()
                .collect { apps -> setState { copy(appNames = apps.associate { it.packageName to it.name }) } }
        }
    }

    override fun onEvent(event: InboxEvent) {
        when (event) {
            is InboxEvent.OnSearchQueryChange -> updateSearchQuery(event.query)
            is InboxEvent.OnFilterChange -> saveFilter(event.filter)
            is InboxEvent.OnRemoveFilter -> saveFilter(uiState.value.filter.without(event.chip))
            InboxEvent.OnClearFilters -> {
                updateSearchQuery("")
                saveFilter(InboxFilter())
            }
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

    /** Persists [filter]; the saved-filters collector in [loadSavedFilters] feeds it back to the state. */
    private fun saveFilter(filter: InboxFilter) {
        if (initialStatusOverride != null) {
            // Leaving the per-visit override: show the edit now, since persisting a value equal to
            // the saved one wouldn't re-emit through the saved-filters collector.
            initialStatusOverride = null
            setState { copy(filter = filter) }
        }
        viewModelScope.launch {
            userPreferencesRepository.setInboxFilters(filter.toSettings())
                .onFailure { e ->
                    Timber.e(e, "Failed to save inbox filters")
                }
        }
    }

    private fun onNotificationClick(notificationId: String) {
        sendEffect(InboxEffect.NavigateToNotificationDetail(notificationId))
    }
}

// File-scoped so the constructor's super-call argument (resolveStatusOverride) can reach it.
private const val KEY_STATUS_OVERRIDE = "inbox_status_override"

/**
 * Seeds the override from the route on first creation only; once the key exists (including a
 * null left by a filter edit) the saved value wins, so a restored ViewModel keeps the user's choice.
 */
private fun SavedStateHandle.resolveStatusOverride(initialStatus: NotificationStatusFilter?): NotificationStatusFilter? {
    if (!contains(KEY_STATUS_OVERRIDE)) set(KEY_STATUS_OVERRIDE, initialStatus)
    return get(KEY_STATUS_OVERRIDE)
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
