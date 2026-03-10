package dev.gaferneira.notificapp.features.inbox.contract

import dev.gaferneira.notificapp.domain.model.AppInfo

/**
 * MVI Contract for the InboxFilterBottomSheet.
 *
 * Manages the state for filtering notifications by app and processed status.
 */
object InboxFilterContract {

    /**
     * Status filter options for notifications.
     */
    enum class Status {
        ALL,
        PROCESSED,
        UNPROCESSED,
    }

    /**
     * UI State for the inbox filter bottom sheet.
     */
    data class UiState(
        /** All available apps that have notifications */
        val availableApps: List<AppInfo> = emptyList(),
        /** Currently selected app package names */
        val selectedApps: Set<String> = emptySet(),
        /** Current processed status filter */
        val statusFilter: Status = Status.ALL,
        /** Whether any filters are active */
        val hasActiveFilters: Boolean = false,
    )

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** Initialize with current filter state */
        data class Init(
            val currentSelectedApps: List<String>,
            val currentStatusFilter: Status = Status.ALL,
        ) : UiEvent()

        /** Toggle an app selection */
        data class OnAppToggle(val appPackageName: String) : UiEvent()

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
        data class ApplyFilter(
            val selectedApps: List<String>,
            val statusFilter: Status,
        ) : UiEffect()
    }
}
