package dev.gaferneira.notificapp.features.rules.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract.AppOption
import dev.gaferneira.notificapp.features.rules.contract.RulesFilterContract.CategoryOption
import dev.gaferneira.notificapp.features.rules.contract.matches
import dev.gaferneira.notificapp.features.rules.contract.mentionsApp
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the rules Filter & sort bottom sheet.
 *
 * Holds an unapplied [RulesFilterContract.UiState.draft] and derives everything else from it plus
 * the rules and the monitored apps:
 * - Options: categories and apps with the number of rules that use/mention them. Apps are the
 *   union of apps mentioned by rules ("used") and enabled monitored apps; monitored apps with no
 *   rule show up in the second group with a count of 0.
 * - Live match count for the draft via [matches], the same function the Rules list uses.
 *
 * Draft lifecycle: the draft is hydrated from the applied filter once per sheet opening (the first
 * [RulesFilterContract.UiEvent.Init] after the previous Apply/Dismiss). Re-sending Init (rules
 * changed) only refreshes the options, so unapplied edits are never wiped. On hydration, selected
 * categories that no longer exist are dropped; selected apps are always kept (shown as selected
 * entries, with 0 rules when unknown) so the user can see and remove them.
 */
@HiltViewModel
class FilterBottomSheetViewModel @Inject constructor(
    private val selectedAppRepository: SelectedAppRepository,
) : MviViewModel<RulesFilterContract.UiState, RulesFilterContract.UiEvent, RulesFilterContract.UiEffect>(
    RulesFilterContract.UiState(),
) {

    private var rules: List<Rule> = emptyList()
    private var searchQuery: String = ""
    private var monitoredApps: List<SelectedApp> = emptyList()
    private var hydrated = false

    init {
        viewModelScope.launch {
            selectedAppRepository.observeEnabledApps().collect { apps ->
                monitoredApps = apps
                refresh()
            }
        }
    }

    override fun onEvent(event: RulesFilterContract.UiEvent) {
        when (event) {
            is RulesFilterContract.UiEvent.Init -> initialize(event)
            is RulesFilterContract.UiEvent.OnCategoryToggle -> editDraft {
                copy(selectedCategories = selectedCategories.toggle(event.category))
            }
            RulesFilterContract.UiEvent.OnUncategorizedToggle -> editDraft {
                copy(includeUncategorized = !includeUncategorized)
            }
            is RulesFilterContract.UiEvent.OnAppToggle -> editDraft {
                val apps = selectedApps.toggle(event.appPackageName)
                copy(selectedApps = apps, includeGlobalRules = includeGlobalRules && apps.isNotEmpty())
            }
            is RulesFilterContract.UiEvent.OnAppSearchChange -> {
                setState { copy(appSearchQuery = event.query) }
                refresh()
            }
            is RulesFilterContract.UiEvent.OnIncludeGlobalRulesChange -> editDraft {
                copy(includeGlobalRules = event.include && selectedApps.isNotEmpty())
            }
            is RulesFilterContract.UiEvent.OnStatusChange -> editDraft { copy(status = event.status) }
            is RulesFilterContract.UiEvent.OnSortChange -> editDraft { copy(sortBy = event.sortBy) }
            RulesFilterContract.UiEvent.OnClearAll -> editDraft { withoutFilters() }
            RulesFilterContract.UiEvent.OnApply -> {
                val filter = uiState.value.draft
                hydrated = false
                sendEffect(RulesFilterContract.UiEffect.ApplyFilter(filter))
            }
            RulesFilterContract.UiEvent.OnDismiss -> {
                hydrated = false
                sendEffect(RulesFilterContract.UiEffect.Dismiss)
            }
        }
    }

    private fun initialize(event: RulesFilterContract.UiEvent.Init) {
        rules = event.allRules
        searchQuery = event.searchQuery
        if (!hydrated) {
            hydrated = true
            val current = event.currentFilter
            val categories = rules.mapNotNull { it.category }.toSet()
            val draft = current.copy(
                selectedCategories = current.selectedCategories.filterTo(mutableSetOf()) { it in categories },
                includeUncategorized = current.includeUncategorized && rules.any { it.category == null },
                includeGlobalRules = current.includeGlobalRules && current.selectedApps.isNotEmpty(),
            )
            setState { copy(draft = draft, appSearchQuery = "") }
        }
        refresh()
    }

    private fun editDraft(edit: RuleFilter.() -> RuleFilter) {
        setState { copy(draft = draft.edit()) }
        refresh()
    }

    /** Recomputes every derived field from the draft, the rules and the monitored apps. */
    private fun refresh() {
        val current = uiState.value
        val draft = current.draft
        val query = current.appSearchQuery.trim()

        val categoryOptions = rules
            .mapNotNull { it.category }
            .groupingBy { it }
            .eachCount()
            .map { (name, count) -> CategoryOption(name, count) }
            .sortedBy { it.name.lowercase() }

        val allApps = buildAppOptions(draft.selectedApps)
        val visible = allApps.filter { query.isEmpty() || it.name.contains(query, ignoreCase = true) }

        setState {
            copy(
                categories = categoryOptions.toImmutableList(),
                uncategorizedCount = rules.count { it.category == null },
                usedApps = visible.filter { it.ruleCount > 0 }.toImmutableList(),
                otherApps = visible.filter { it.ruleCount == 0 }.toImmutableList(),
                selectedApps = allApps.filter { it.packageName in draft.selectedApps }.toImmutableList(),
                hasAppOptions = allApps.isNotEmpty(),
                matchCount = rules.count { draft.matches(it, searchQuery) },
            )
        }
    }

    /**
     * Union of apps mentioned by rules, enabled monitored apps and (for stale selections) the
     * selected packages themselves, sorted case-insensitively by name.
     */
    private fun buildAppOptions(selected: Set<String>): List<AppOption> {
        val names = LinkedHashMap<String, String>()
        rules.forEach { rule -> rule.targetApps?.forEach { names.putIfAbsent(it.packageName, it.name) } }
        monitoredApps.forEach { names.putIfAbsent(it.packageName, it.appName) }
        selected.forEach { names.putIfAbsent(it, it) }
        return names
            .map { (pkg, name) -> AppOption(pkg, name, ruleCount = rules.count { it.mentionsApp(pkg) }) }
            .sortedWith(compareBy({ it.name.lowercase() }, { it.packageName }))
    }

    private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value
}
