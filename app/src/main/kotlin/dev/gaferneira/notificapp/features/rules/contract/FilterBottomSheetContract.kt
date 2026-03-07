package dev.gaferneira.notificapp.features.rules.contract

import dev.gaferneira.notificapp.domain.model.AppInfo

/**
 * MVI Contract for the FilterBottomSheet.
 *
 * Manages the state for filtering rules by category and target app.
 */
object FilterBottomSheetContract {

    /**
     * UI State for the filter bottom sheet.
     */
    data class UiState(
        /** All available categories from the rules list */
        val availableCategories: List<String> = emptyList(),
        /** All available target apps from the rules list */
        val availableApps: List<AppInfo> = emptyList(),
        /** Currently selected categories */
        val selectedCategories: Set<String> = emptySet(),
        /** Currently selected app package names */
        val selectedApps: Set<String> = emptySet(),
        /** Current status filter (inherited from parent) */
        val statusFilter: RuleFilter.Status = RuleFilter.Status.ALL,
        /** Whether any filters are active */
        val hasActiveFilters: Boolean = false,
    )

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** Initialize with current filter state */
        data class Init(val currentFilter: RuleFilter) : UiEvent()

        /** Toggle a category selection */
        data class OnCategoryToggle(val category: String) : UiEvent()

        /** Toggle an app selection */
        data class OnAppToggle(val appPackageName: String) : UiEvent()

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
        data class ApplyFilter(val filter: RuleFilter) : UiEffect()
    }
}
