package dev.gaferneira.notificapp.features.rules.contract

import dev.gaferneira.notificapp.core.ui.Resource
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.Rule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Data class representing the state of rules with filtered results.
 *
 * @property rules The list of filtered rules based on search query and filter
 * @property allRules The complete list of unfiltered rules from the repository
 * @property searchQuery Current search query string
 * @property filter Current applied filter and sort
 */
data class RulesUiState(
    val rules: Resource<ImmutableList<Rule>> = Resource.Loading(),
    val allRules: ImmutableList<Rule> = persistentListOf(),
    val searchQuery: String = "",
    val filter: RuleFilter = RuleFilter(),
    /** A successfully decoded, not-yet-saved imported rule awaiting user confirmation */
    val importPreview: Rule? = null,
    /** Wire names of actions dropped from [importPreview] because this app version doesn't recognize them */
    val importSkippedActions: List<String> = emptyList(),
    /** Message to show when decoding an imported rule fails */
    val importError: UiText? = null,
)

/**
 * UI Events for RulesScreen.
 */
sealed interface RulesEvent {
    data object LoadRules : RulesEvent
    data object Refresh : RulesEvent
    data class OnRuleClick(val ruleId: String) : RulesEvent
    data class OnRuleToggleActive(val ruleId: String) : RulesEvent
    data object OnAddRuleClick : RulesEvent
    data class OnSearchQueryChange(val query: String) : RulesEvent
    data class OnFilterChange(val filter: RuleFilter) : RulesEvent

    /** Removes a single active filter (tap on a dismissible chip under the search bar). */
    data class OnRemoveFilter(val chip: RuleFilterChip) : RulesEvent

    /**
     * Resets the search query and every filter dimension ("no results" empty state action).
     * The sort order is not a filter and is kept.
     */
    data object OnClearFilters : RulesEvent
    data class OnRuleTextReceived(val text: String) : RulesEvent
    data object OnImportConfirmed : RulesEvent
    data object OnImportCancelled : RulesEvent
    data object OnDismissImportError : RulesEvent
}

/**
 * UI Effects (one-time events) for RulesScreen.
 */
sealed interface RulesEffect {
    data class NavigateToRuleDetails(val ruleId: String) : RulesEffect
    data class NavigateToRuleEditor(val ruleId: String? = null) : RulesEffect
    data class ShowError(val message: UiText) : RulesEffect
    data class ShowSuccess(val message: UiText) : RulesEffect
}

/**
 * Filter and sort configuration for rules.
 *
 * Filters (status, categories, apps) narrow the list; [sortBy] only orders it. Sort is therefore
 * NOT an "active filter": it never counts in [isActive] / [activeFilterCount] (badge, "Clear
 * filters" empty-state action) and [withoutFilters] keeps it. Matching lives in
 * [RuleFilter.matches] (see `RuleFiltering.kt`).
 *
 * @property status Enabled/disabled filter
 * @property selectedCategories Selected category names (OR)
 * @property includeUncategorized Also match rules whose category is null (part of the category dimension)
 * @property selectedApps Selected package names (OR). A rule matches when it MENTIONS the app in
 * its target list, in include or exclude mode. Global rules do not match by default.
 * @property includeGlobalRules Only meaningful when [selectedApps] is not empty: additionally match
 * every rule that would run for a selected app (global rules and exclude-mode rules not excluding it)
 */
data class RuleFilter(
    val status: Status = Status.ALL,
    val selectedCategories: Set<String> = emptySet(),
    val includeUncategorized: Boolean = false,
    val selectedApps: Set<String> = emptySet(),
    val includeGlobalRules: Boolean = false,
    val sortBy: SortBy = SortBy.CATEGORY_ASC,
) {
    enum class Status {
        ALL,
        ENABLED,
        DISABLED,
    }

    enum class SortBy {
        CATEGORY_ASC, // Group by category, categories A-Z, rules by name
        NAME_ASC, // Flat list, rules A-Z
        NAME_DESC, // Flat list, rules Z-A
        CREATED_NEWEST, // Flat list, newest first
        CREATED_OLDEST, // Flat list, oldest first
        UPDATED_RECENT, // Flat list, recently modified first
        STATUS, // Group by status (Active first)
    }

    /** True when the category dimension has any selection (named categories or uncategorized). */
    val hasCategoryFilter: Boolean get() = selectedCategories.isNotEmpty() || includeUncategorized

    /** Returns true if any filter dimension is active. Sort is ignored by design. */
    fun isActive(): Boolean = activeFilterCount() > 0

    /** Count of active filter dimensions (status, categories, apps), each counted once. */
    fun activeFilterCount(): Int {
        var count = 0
        if (status != Status.ALL) count++
        if (hasCategoryFilter) count++
        if (selectedApps.isNotEmpty()) count++
        return count
    }

    /** Same sort, every filter dimension reset. */
    fun withoutFilters(): RuleFilter = RuleFilter(sortBy = sortBy)

    /** The individual active filters, in display order, for the dismissible chips row. */
    fun activeChips(): List<RuleFilterChip> = buildList {
        if (status != Status.ALL) add(RuleFilterChip.Status(status))
        selectedCategories.sortedBy { it.lowercase() }.forEach { add(RuleFilterChip.Category(it)) }
        if (includeUncategorized) add(RuleFilterChip.Uncategorized)
        selectedApps.sorted().forEach { add(RuleFilterChip.App(it)) }
        if (selectedApps.isNotEmpty() && includeGlobalRules) add(RuleFilterChip.GlobalRules)
    }

    /** Returns this filter with the single filter represented by [chip] removed. */
    fun without(chip: RuleFilterChip): RuleFilter = when (chip) {
        is RuleFilterChip.Status -> copy(status = Status.ALL)
        is RuleFilterChip.Category -> copy(selectedCategories = selectedCategories - chip.name)
        RuleFilterChip.Uncategorized -> copy(includeUncategorized = false)
        is RuleFilterChip.App -> (selectedApps - chip.packageName).let { apps ->
            // The global-rules toggle is meaningless without apps; never leave it dangling.
            copy(selectedApps = apps, includeGlobalRules = includeGlobalRules && apps.isNotEmpty())
        }
        RuleFilterChip.GlobalRules -> copy(includeGlobalRules = false)
    }
}

/** One removable entry of the active-filters chips row. */
sealed interface RuleFilterChip {
    data class Status(val status: RuleFilter.Status) : RuleFilterChip
    data class Category(val name: String) : RuleFilterChip
    data object Uncategorized : RuleFilterChip
    data class App(val packageName: String) : RuleFilterChip
    data object GlobalRules : RuleFilterChip
}
