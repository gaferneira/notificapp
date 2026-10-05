package dev.gaferneira.notificapp.features.rules.contract

import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.appliesToPackage

/** True when [pkg] appears in this rule's target list, in include OR exclude mode. */
fun Rule.mentionsApp(pkg: String): Boolean = targetApps?.any { it.packageName == pkg } == true

/**
 * The single source of truth for whether [rule] passes this filter (and the optional free-text
 * [query]). Used by the Rules list and by the filter sheet's live "Show N rules" count.
 *
 * - Status: ALL / ENABLED / DISABLED.
 * - Categories: OR across [RuleFilter.selectedCategories] plus null-category rules when
 *   [RuleFilter.includeUncategorized]. Empty selection matches everything.
 * - Apps: OR across selected packages. A rule matches a package when it MENTIONS it
 *   ([mentionsApp]). When [RuleFilter.includeGlobalRules] is on, it also matches every rule that
 *   would run for the package ([appliesToPackage]) - i.e. the union of "mentions" and "would run
 *   for". Global rules (null/empty targets) therefore only match when the toggle is on.
 * - Search: case-insensitive over name, description and category.
 */
fun RuleFilter.matches(rule: Rule, query: String = ""): Boolean = matchesSearch(rule, query) && matchesStatus(rule) && matchesCategory(rule) && matchesApps(rule)

private fun matchesSearch(rule: Rule, query: String): Boolean = query.isBlank() ||
    rule.name.contains(query, ignoreCase = true) ||
    rule.description?.contains(query, ignoreCase = true) == true ||
    rule.category?.contains(query, ignoreCase = true) == true

private fun RuleFilter.matchesStatus(rule: Rule): Boolean = when (status) {
    RuleFilter.Status.ALL -> true
    RuleFilter.Status.ENABLED -> rule.isActive
    RuleFilter.Status.DISABLED -> !rule.isActive
}

private fun RuleFilter.matchesCategory(rule: Rule): Boolean = when {
    !hasCategoryFilter -> true
    rule.category == null -> includeUncategorized
    else -> rule.category in selectedCategories
}

private fun RuleFilter.matchesApps(rule: Rule): Boolean = selectedApps.isEmpty() ||
    selectedApps.any { pkg ->
        rule.mentionsApp(pkg) || (includeGlobalRules && rule.appliesToPackage(pkg))
    }

/** Orders [rules] according to [RuleFilter.sortBy]. Text comparisons are case-insensitive. */
fun RuleFilter.sort(rules: List<Rule>): List<Rule> = when (sortBy) {
    RuleFilter.SortBy.CATEGORY_ASC -> rules.sortedWith(
        compareBy<Rule> { it.category == null } // uncategorized last
            .thenBy { it.category?.lowercase().orEmpty() }
            .thenBy { it.name.lowercase() },
    )
    RuleFilter.SortBy.NAME_ASC -> rules.sortedBy { it.name.lowercase() }
    RuleFilter.SortBy.NAME_DESC -> rules.sortedByDescending { it.name.lowercase() }
    RuleFilter.SortBy.CREATED_NEWEST -> rules.sortedByDescending { it.createdAt }
    RuleFilter.SortBy.CREATED_OLDEST -> rules.sortedBy { it.createdAt }
    RuleFilter.SortBy.UPDATED_RECENT -> rules.sortedByDescending { it.updatedAt }
    RuleFilter.SortBy.STATUS -> rules.sortedWith(
        compareByDescending<Rule> { it.isActive }.thenBy { it.name.lowercase() },
    )
}

/** Filters [rules] with [matches] and orders them with [sort]. */
fun RuleFilter.filterAndSort(rules: List<Rule>, query: String = ""): List<Rule> = sort(rules.filter { matches(it, query) })
