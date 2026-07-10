package dev.gaferneira.notificapp.features.rules.contract

import dev.gaferneira.notificapp.core.ui.Resource
import dev.gaferneira.notificapp.domain.model.Rule

/**
 * Data class representing the state of rules with filtered results.
 *
 * @property rules The list of filtered rules based on search query and filter
 * @property allRules The complete list of unfiltered rules from the repository
 * @property searchQuery Current search query string
 * @property filter Current filter selection (All/Enabled/Disabled)
 */
data class RulesUiState(
    val rules: Resource<List<Rule>> = Resource.Loading(),
    val allRules: List<Rule> = emptyList(),
    val searchQuery: String = "",
    val filter: RuleFilter = RuleFilter(),
    /** A successfully decoded, not-yet-saved imported rule awaiting user confirmation */
    val importPreview: Rule? = null,
    /** Wire names of actions dropped from [importPreview] because this app version doesn't recognize them */
    val importSkippedActions: List<String> = emptyList(),
    /** Message to show when decoding an imported rule fails */
    val importError: String? = null,
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
    data class OnExportRuleClick(val ruleId: String) : RulesEvent
    data class OnRuleTextReceived(val text: String) : RulesEvent
    data object OnImportConfirmed : RulesEvent
    data object OnImportCancelled : RulesEvent
    data object OnDismissImportError : RulesEvent
}

/**
 * UI Effects (one-time events) for RulesScreen.
 */
sealed interface RulesEffect {
    data class NavigateToRuleEditor(val ruleId: String? = null) : RulesEffect
    data class ShowError(val message: String) : RulesEffect
    data class ShareRule(val ruleName: String, val json: String) : RulesEffect
    data class ShowSuccess(val message: String) : RulesEffect
}

/**
 * Filter configuration for rules.
 * Supports filtering by status, category, and target app, plus sorting.
 */
data class RuleFilter(
    val status: Status = Status.ALL,
    val selectedCategories: Set<String> = emptySet(),
    val selectedApps: Set<String> = emptySet(),
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

    /**
     * Returns true if any filter or sort is active (not default).
     */
    fun isActive(): Boolean = status != Status.ALL ||
        selectedCategories.isNotEmpty() ||
        selectedApps.isNotEmpty() ||
        sortBy != SortBy.CATEGORY_ASC

    /**
     * Returns the count of active filter/sort dimensions.
     */
    fun activeFilterCount(): Int {
        var count = 0
        if (status != Status.ALL) count++
        if (selectedCategories.isNotEmpty()) count++
        if (selectedApps.isNotEmpty()) count++
        if (sortBy != SortBy.CATEGORY_ASC) count++
        return count
    }
}
