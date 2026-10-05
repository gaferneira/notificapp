package dev.gaferneira.notificapp.features.rules.viewmodel

import dev.gaferneira.notificapp.domain.model.preferences.RulesFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.RulesSort
import dev.gaferneira.notificapp.domain.model.preferences.RulesStatusFilter
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter

/** Maps the persisted settings to the feature-level [RuleFilter]. */
internal fun RulesFilterSettings.toRuleFilter(): RuleFilter = RuleFilter(
    status = RuleFilter.Status.valueOf(status.name),
    selectedCategories = selectedCategories.toSet(),
    includeUncategorized = includeUncategorized,
    selectedApps = selectedApps.toSet(),
    includeGlobalRules = includeGlobalRules && selectedApps.isNotEmpty(),
    sortBy = RuleFilter.SortBy.valueOf(sortBy.name),
)

/** Maps the feature-level [RuleFilter] to its persisted form. */
internal fun RuleFilter.toSettings(): RulesFilterSettings = RulesFilterSettings(
    status = RulesStatusFilter.valueOf(status.name),
    selectedCategories = selectedCategories.sorted(),
    includeUncategorized = includeUncategorized,
    selectedApps = selectedApps.sorted(),
    includeGlobalRules = includeGlobalRules,
    sortBy = RulesSort.valueOf(sortBy.name),
)
