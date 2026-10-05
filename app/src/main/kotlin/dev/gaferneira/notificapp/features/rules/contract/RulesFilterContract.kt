package dev.gaferneira.notificapp.features.rules.contract

import dev.gaferneira.notificapp.domain.model.Rule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * MVI Contract for the rules Filter & sort bottom sheet.
 *
 * The sheet edits an unapplied [UiState.draft]; nothing reaches the Rules screen until
 * [UiEvent.OnApply].
 */
object RulesFilterContract {

    /** A selectable category with the number of rules that use it. */
    data class CategoryOption(val name: String, val ruleCount: Int)

    /** A selectable app with the number of rules that MENTION it (include or exclude list). */
    data class AppOption(val packageName: String, val name: String, val ruleCount: Int)

    /**
     * UI State for the filter bottom sheet.
     */
    data class UiState(
        /** The unapplied filter and sort being edited */
        val draft: RuleFilter = RuleFilter(),
        /** Categories used by at least one rule, sorted case-insensitively */
        val categories: ImmutableList<CategoryOption> = persistentListOf(),
        /** Number of rules without a category (the "Uncategorized" option is shown when > 0) */
        val uncategorizedCount: Int = 0,
        /** Apps mentioned by at least one rule, filtered by [appSearchQuery], sorted case-insensitively */
        val usedApps: ImmutableList<AppOption> = persistentListOf(),
        /** Other monitored apps (0 rules) plus selected apps unknown to the options, filtered by [appSearchQuery] */
        val otherApps: ImmutableList<AppOption> = persistentListOf(),
        /** Every selected app, regardless of the search query, so they can always be reviewed/removed */
        val selectedApps: ImmutableList<AppOption> = persistentListOf(),
        /** Current text of the app picker search field */
        val appSearchQuery: String = "",
        /** Whether any app option exists at all, independent of the picker search text */
        val hasAppOptions: Boolean = false,
        /** Number of rules matching [draft] (and the Rules screen search query) */
        val matchCount: Int = 0,
    ) {
        /** Whether the draft has any active filter dimension. Sort is not a filter. */
        val hasActiveFilters: Boolean get() = draft.isActive()

        /** Whether there is anything to filter by. */
        val hasOptions: Boolean get() = categories.isNotEmpty() || uncategorizedCount > 0 || hasAppOptions
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /**
         * Provide the rules (to derive options/counts), the applied filter and the Rules screen
         * search query. The draft is hydrated from [currentFilter] once per sheet opening; later
         * Init events only refresh the options and never discard unapplied edits.
         */
        data class Init(
            val allRules: ImmutableList<Rule>,
            val currentFilter: RuleFilter,
            val searchQuery: String = "",
        ) : UiEvent()

        /** Toggle a category selection */
        data class OnCategoryToggle(val category: String) : UiEvent()

        /** Toggle the "Uncategorized" option */
        data object OnUncategorizedToggle : UiEvent()

        /** Toggle an app selection */
        data class OnAppToggle(val appPackageName: String) : UiEvent()

        /** Change the app picker search text */
        data class OnAppSearchChange(val query: String) : UiEvent()

        /** Toggle "Also show rules that apply to all apps" */
        data class OnIncludeGlobalRulesChange(val include: Boolean) : UiEvent()

        /** Change status filter (All/Enabled/Disabled) */
        data class OnStatusChange(val status: RuleFilter.Status) : UiEvent()

        /** Change sort option */
        data class OnSortChange(val sortBy: RuleFilter.SortBy) : UiEvent()

        /** Clear all filters (the sort order is kept; it is not a filter) */
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
