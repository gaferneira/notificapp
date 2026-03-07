package dev.gaferneira.notificapp.features.rules.contract

import dev.gaferneira.notificapp.core.ui.Resource
import dev.gaferneira.notificapp.domain.model.Rule

/**
 * Filter configuration for rules.
 * Supports filtering by status, category, and target app.
 */
data class RuleFilter(
    val status: Status = Status.ALL,
    val selectedCategories: Set<String> = emptySet(),
    val selectedApps: Set<String> = emptySet(),
) {
    enum class Status {
        ALL,
        ENABLED,
        DISABLED,
    }

    /**
     * Returns true if any filter is active (not default).
     */
    fun isActive(): Boolean = status != Status.ALL || selectedCategories.isNotEmpty() || selectedApps.isNotEmpty()

    /**
     * Returns the count of active filter dimensions.
     */
    fun activeFilterCount(): Int {
        var count = 0
        if (status != Status.ALL) count++
        if (selectedCategories.isNotEmpty()) count++
        if (selectedApps.isNotEmpty()) count++
        return count
    }
}

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
}

/**
 * UI Effects (one-time events) for RulesScreen.
 */
sealed interface RulesEffect {
    data class NavigateToRuleEditor(val ruleId: String? = null) : RulesEffect
    data class ShowError(val message: String) : RulesEffect
    data class ShowDeleteConfirmation(val ruleId: String) : RulesEffect
}
