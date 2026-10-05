package dev.gaferneira.notificapp.features.inbox.contract

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter as Status

/**
 * MVI Contract for the Inbox filter bottom sheet.
 *
 * The sheet edits an unapplied [UiState.draft]; nothing reaches the Inbox screen until
 * [UiEvent.OnApply].
 */
object InboxFilterContract {

    /** A selectable app with the number of notifications it has in the inbox. */
    data class AppOption(val packageName: String, val name: String, val notificationCount: Int)

    /**
     * UI State for the inbox filter bottom sheet.
     */
    data class UiState(
        /** The unapplied filter being edited */
        val draft: InboxFilter = InboxFilter(),
        /** Apps with at least one notification, filtered by [appSearchQuery], sorted case-insensitively */
        val withNotificationsApps: ImmutableList<AppOption> = persistentListOf(),
        /** Selected apps that no longer have notifications (stale), filtered by [appSearchQuery] */
        val withoutNotificationsApps: ImmutableList<AppOption> = persistentListOf(),
        /** Every selected app, regardless of the search query, so they can always be reviewed/removed */
        val selectedApps: ImmutableList<AppOption> = persistentListOf(),
        /** Current text of the app picker search field */
        val appSearchQuery: String = "",
        /** Whether any app option exists at all, independent of the picker search text */
        val hasAppOptions: Boolean = false,
        /** Number of notifications matching [draft] (and the Inbox search query) */
        val matchCount: Int = 0,
    ) {
        /** Whether the draft has any active filter. */
        val hasActiveFilters: Boolean get() = draft.isActive()
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /**
         * Provide the applied filter and the Inbox search text. The draft is hydrated from
         * [currentFilter] once per sheet opening; later Init events only refresh the live count
         * and never discard unapplied edits.
         */
        data class Init(
            val currentFilter: InboxFilter,
            val searchQuery: String = "",
        ) : UiEvent()

        /** Toggle an app selection */
        data class OnAppToggle(val appPackageName: String) : UiEvent()

        /** Change the app picker search text */
        data class OnAppSearchChange(val query: String) : UiEvent()

        /** Change status filter */
        data class OnStatusChange(val status: Status) : UiEvent()

        /** Clear all filters */
        data object OnClearAll : UiEvent()

        /** Apply the selected filters */
        data object OnApply : UiEvent()

        /** Dismiss without applying */
        data object OnDismiss : UiEvent()
    }

    /**
     * One-time effects to communicate with the parent.
     */
    sealed class UiEffect {
        /** Dismiss the sheet */
        data object Dismiss : UiEffect()

        /** Apply the new filter configuration */
        data class ApplyFilter(val filter: InboxFilter) : UiEffect()
    }
}
