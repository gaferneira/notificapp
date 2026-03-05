package dev.gaferneira.notificapp.features.inbox.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.inbox.contract.InboxEffect
import dev.gaferneira.notificapp.features.inbox.contract.InboxEvent
import dev.gaferneira.notificapp.features.inbox.contract.InboxUiState
import dev.gaferneira.notificapp.features.inbox.contract.NotificationGroup
import dev.gaferneira.notificapp.features.inbox.contract.NotificationItem
import dev.gaferneira.notificapp.util.timeAgo
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Date
import javax.inject.Inject

/**
 * ViewModel for the Inbox Screen.
 *
 * Implements MVI pattern per ADR 001:
 * - StateFlow for UI state
 * - Channel for effects
 * - Centralized event handling via onEvent()
 *
 * Spec: openspec/specs/inbox/001-inbox-screen.md
 */
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val selectedAppRepository: SelectedAppRepository,
) : MviViewModel<InboxUiState, InboxEvent, InboxEffect>(InboxUiState.Loading) {

    private var allNotifications: List<Notification> = emptyList()
    private var selectedApps: List<String> = emptyList()

    init {
        loadNotifications()
    }

    override fun onEvent(event: InboxEvent) {
        when (event) {
            is InboxEvent.LoadNotifications -> loadNotifications()
            is InboxEvent.Refresh -> refreshNotifications()
            is InboxEvent.OnSearchQueryChange -> updateSearchQuery(event.query)
            is InboxEvent.OnAppFilterChange -> updateAppFilter(event.packageNames)
            is InboxEvent.OnNotificationClick -> onNotificationClick(event.notificationId)
            is InboxEvent.OnNotificationDelete -> deleteNotification(event.notificationId)
        }
    }

    private fun loadNotifications() {
        setState { InboxUiState.Loading }

        viewModelScope.launch {
            try {
                // Observe notifications from repository
                notificationRepository.observeAllNotifications()
                    .collectLatest { notifications ->
                        allNotifications = notifications
                        val grouped = groupAndFilterNotifications(notifications, "", selectedApps)
                        setState {
                            InboxUiState.Success(
                                groupedNotifications = grouped,
                                selectedApps = selectedApps,
                            )
                        }
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load notifications")
                setState { InboxUiState.Error(e.message ?: "Failed to load notifications") }
                sendEffect(InboxEffect.ShowError("Failed to load notifications"))
            }
        }
    }

    private fun refreshNotifications() {
        viewModelScope.launch {
            notificationRepository.getAllNotifications()
                .onSuccess { notifications ->
                    allNotifications = notifications
                    val grouped = groupAndFilterNotifications(notifications, "", selectedApps)
                    setState {
                        InboxUiState.Success(
                            groupedNotifications = grouped,
                            selectedApps = selectedApps,
                        )
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to refresh notifications")
                    sendEffect(InboxEffect.ShowError("Failed to refresh notifications"))
                }
        }
    }

    private fun updateSearchQuery(query: String) {
        val currentState = uiState.value as? InboxUiState.Success ?: return
        val grouped = groupAndFilterNotifications(allNotifications, query, selectedApps)
        setState {
            currentState.copy(
                groupedNotifications = grouped,
                searchQuery = query,
            )
        }
    }

    private fun updateAppFilter(packageNames: List<String>) {
        selectedApps = packageNames
        val currentState = uiState.value as? InboxUiState.Success ?: return
        val grouped = groupAndFilterNotifications(
            allNotifications,
            currentState.searchQuery,
            packageNames,
        )
        setState {
            currentState.copy(
                groupedNotifications = grouped,
                selectedApps = packageNames,
            )
        }
    }

    private fun onNotificationClick(notificationId: String) {
        sendEffect(InboxEffect.NavigateToNotificationDetail(notificationId))
    }

    private fun deleteNotification(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.deleteNotification(notificationId)
                .onSuccess {
                    Timber.d("Deleted notification: $notificationId")
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to delete notification: $notificationId")
                    sendEffect(InboxEffect.ShowError("Failed to delete notification"))
                }
        }
    }

    /**
     * Group notifications by app, sort by date (newest first),
     * and apply filters.
     */
    private fun groupAndFilterNotifications(
        notifications: List<Notification>,
        searchQuery: String,
        packageNames: List<String>,
    ): List<NotificationGroup> {
        var filtered = notifications

        // Filter by selected apps
        if (packageNames.isNotEmpty()) {
            filtered = filtered.filter { it.packageName in packageNames }
        }

        // Filter by search query
        if (searchQuery.isNotBlank()) {
            val query = searchQuery.lowercase()
            filtered = filtered.filter { notification ->
                notification.title?.lowercase()?.contains(query) == true ||
                    notification.content?.lowercase()?.contains(query) == true ||
                    notification.appName.lowercase().contains(query) ||
                    notification.packageName.lowercase().contains(query)
            }
        }

        // Sort by timestamp (newest first)
        filtered = filtered.sortedByDescending { it.timestamp }

        // Map to UI model
        val notificationItems = filtered.map { it.toNotificationItem() }

        // Group by app
        return notificationItems
            .groupBy { it.appPackageName }
            .map { (packageName, items) ->
                NotificationGroup(
                    appPackageName = packageName,
                    appName = items.firstOrNull()?.appName ?: packageName,
                    notifications = items,
                )
            }
            // Sort groups by the newest notification in each group
            .sortedByDescending { group ->
                group.notifications.maxOfOrNull { it.timestamp } ?: 0L
            }
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
