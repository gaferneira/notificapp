package dev.gaferneira.notificapp.features.inbox.contract

import androidx.compose.runtime.Immutable
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter as Status

/**
 * Data class representing the state of the Inbox screen.
 *
 * Note: The notification data itself is exposed as a separate Flow<PagingData<NotificationItem>>
 * from the ViewModel, not stored in this state class. This state only holds filter configuration.
 */
data class InboxUiState(
    val selectedApps: List<String> = emptyList(),
    val statusFilter: Status = Status.ALL,
    val searchQuery: String = "",
    /** Whether notification listener access is currently granted */
    val isNotificationListenerActive: Boolean = true,
)

/**
 * UI representation of a notification item.
 */
@Immutable
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
 * Sealed class representing items in the paginated inbox list.
 * Used with PagingData.insertSeparators() to add time headers.
 */
sealed class InboxListItem {
    /**
     * Time header showing a 2-hour window (e.g., "10 March 18:00").
     */
    data class TimeHeader(
        val timestamp: Long,
        val label: String,
    ) : InboxListItem()

    /**
     * A notification row in the list.
     */
    data class NotificationRow(
        val notification: NotificationItem,
    ) : InboxListItem()
}

/**
 * UI Events for InboxScreen.
 */
sealed interface InboxEvent {
    data class OnSearchQueryChange(val query: String) : InboxEvent
    data class OnAppFilterChange(
        val packageNames: List<String>,
        val statusFilter: Status = Status.ALL,
    ) : InboxEvent
    data class OnNotificationClick(val notificationId: String) : InboxEvent

    /** Re-check notification listener status (called on resume) */
    data object OnResume : InboxEvent
}

/**
 * UI Effects (one-time events) for InboxScreen.
 */
sealed interface InboxEffect {
    data class NavigateToNotificationDetail(val notificationId: String) : InboxEffect
    data class ShowError(val message: String) : InboxEffect
}
