package dev.gaferneira.notificapp.features.inbox.contract

import androidx.compose.runtime.Immutable
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter as Status

/**
 * Data class representing the state of the Inbox screen.
 *
 * Note: The notification data itself is exposed as a separate Flow<PagingData<NotificationItem>>
 * from the ViewModel, not stored in this state class. This state only holds filter configuration.
 */
data class InboxUiState(
    /** The applied filter (status + source apps) */
    val filter: InboxFilter = InboxFilter(),
    /** Display names by package name, for the active-filter chips */
    val appNames: Map<String, String> = emptyMap(),
    val searchQuery: String = "",
    /** Whether notification listener access is currently granted */
    val isNotificationListenerActive: Boolean = true,
)

/**
 * The Inbox filter: notifications of a given processing [status] coming from any of the
 * [selectedApps] (OR across apps; empty selection = every app). There is no sort option, so every
 * field counts as a filter.
 */
data class InboxFilter(
    val selectedApps: Set<String> = emptySet(),
    val status: Status = Status.ALL,
) {
    /** Count of active filter dimensions (status, apps), each counted once. */
    fun activeFilterCount(): Int = (if (status != Status.ALL) 1 else 0) + (if (selectedApps.isNotEmpty()) 1 else 0)

    /** True when any filter dimension is active. */
    fun isActive(): Boolean = activeFilterCount() > 0

    /** The individual active filters, in display order, for the dismissible chips row. */
    fun activeChips(): List<InboxFilterChip> = buildList {
        if (status != Status.ALL) add(InboxFilterChip.Status(status))
        selectedApps.sorted().forEach { add(InboxFilterChip.App(it)) }
    }

    /** This filter without the dimension entry represented by [chip]. */
    fun without(chip: InboxFilterChip): InboxFilter = when (chip) {
        is InboxFilterChip.Status -> copy(status = Status.ALL)
        is InboxFilterChip.App -> copy(selectedApps = selectedApps - chip.packageName)
    }
}

/** One dismissible entry of the active-filter chips row. */
sealed interface InboxFilterChip {
    data class Status(val status: NotificationStatusFilter) : InboxFilterChip
    data class App(val packageName: String) : InboxFilterChip
}

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

    /** Apply (and persist) a new filter from the filter sheet */
    data class OnFilterChange(val filter: InboxFilter) : InboxEvent

    /** Remove a single active filter (chip) */
    data class OnRemoveFilter(val chip: InboxFilterChip) : InboxEvent
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
