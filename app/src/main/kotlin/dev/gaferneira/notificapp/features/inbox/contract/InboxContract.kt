package dev.gaferneira.notificapp.features.inbox.contract

/**
 * MVI Contract for InboxScreen.
 *
 * Spec: openspec/specs/inbox/001-inbox-screen.md
 */
sealed interface InboxUiState {
    data object Loading : InboxUiState
    data class Success(
        val groupedNotifications: List<NotificationGroup>,
        val selectedApps: List<String>,
        val searchQuery: String = "",
    ) : InboxUiState
    data class Error(val message: String) : InboxUiState
}

/**
 * A group of notifications from the same app.
 */
data class NotificationGroup(
    val appPackageName: String,
    val appName: String,
    val notifications: List<NotificationItem>,
)

/**
 * UI representation of a notification item.
 */
data class NotificationItem(
    val id: String,
    val appName: String,
    val appPackageName: String,
    val title: String?,
    val content: String?,
    val timestamp: Long,
    val formattedTime: String,
    val isProcessed: Boolean,
    /** Number of rules that have been applied to this notification */
    val appliedRulesCount: Int = 0,
)

/**
 * UI Events for InboxScreen.
 */
sealed interface InboxEvent {
    data object LoadNotifications : InboxEvent
    data object Refresh : InboxEvent
    data class OnSearchQueryChange(val query: String) : InboxEvent
    data class OnAppFilterChange(val packageNames: List<String>) : InboxEvent
    data class OnNotificationClick(val notificationId: String) : InboxEvent
    data class OnNotificationDelete(val notificationId: String) : InboxEvent
}

/**
 * UI Effects (one-time events) for InboxScreen.
 */
sealed interface InboxEffect {
    data class NavigateToNotificationDetail(val notificationId: String) : InboxEffect
    data class ShowError(val message: String) : InboxEffect
    data object ShowPermissionRequired : InboxEffect
}
