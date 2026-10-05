package dev.gaferneira.notificapp.domain.model.preferences

import kotlinx.serialization.Serializable

/**
 * Domain model for the applied Rules screen filter and sort, persisted across process death.
 *
 * Mirrors the feature-level `RuleFilter` (which lives in `features/rules/contract` and therefore
 * cannot be referenced from the domain layer); the Rules ViewModel maps between the two.
 *
 * @property status Enabled/disabled status filter
 * @property selectedCategories Selected rule categories
 * @property includeUncategorized Whether rules without a category are included
 * @property selectedApps Selected app package names
 * @property includeGlobalRules When apps are selected, also match rules that would run for them
 * (global rules and exclude-mode rules that do not exclude the app)
 * @property sortBy Sort order (not a filter: it never counts as an active filter)
 */
@Serializable
data class RulesFilterSettings(
    val status: RulesStatusFilter = RulesStatusFilter.ALL,
    val selectedCategories: List<String> = emptyList(),
    val includeUncategorized: Boolean = false,
    val selectedApps: List<String> = emptyList(),
    val includeGlobalRules: Boolean = false,
    val sortBy: RulesSort = RulesSort.CATEGORY_ASC,
)

/** Enabled/disabled filter for the rules list. */
@Serializable
enum class RulesStatusFilter {
    ALL,
    ENABLED,
    DISABLED,
}

/** Sort order for the rules list. */
@Serializable
enum class RulesSort {
    CATEGORY_ASC,
    NAME_ASC,
    NAME_DESC,
    CREATED_NEWEST,
    CREATED_OLDEST,
    UPDATED_RECENT,
    STATUS,
}
