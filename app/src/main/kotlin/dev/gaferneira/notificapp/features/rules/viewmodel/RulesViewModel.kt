package dev.gaferneira.notificapp.features.rules.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec.withFreshIdentityForImport
import dev.gaferneira.notificapp.core.ui.Resource
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesEffect
import dev.gaferneira.notificapp.features.rules.contract.RulesEvent
import dev.gaferneira.notificapp.features.rules.contract.RulesUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Rules Screen.
 *
 * Implements MVI pattern per ADR 001:
 * - StateFlow for UI state using Resource pattern
 * - Channel for effects
 * - Centralized event handling via onEvent()
 */
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
) : MviViewModel<RulesUiState, RulesEvent, RulesEffect>(RulesUiState()) {

    private val allRules = MutableStateFlow<List<Rule>>(emptyList())
    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(RuleFilter())

    init {
        loadRules()

        // Combine all state flows to produce filtered rules
        viewModelScope.launch {
            combine(allRules, searchQuery, filter) { rules, query, currentFilter ->
                Triple(rules, query, currentFilter)
            }.collectLatest { (rules, query, currentFilter) ->
                val filteredRules = applyFilters(rules, query, currentFilter)
                setState {
                    copy(
                        rules = Resource.Success(filteredRules),
                        allRules = rules,
                        searchQuery = query,
                        filter = currentFilter,
                    )
                }
            }
        }
    }

    override fun onEvent(event: RulesEvent) {
        when (event) {
            is RulesEvent.LoadRules -> loadRules()
            is RulesEvent.Refresh -> refreshRules()
            is RulesEvent.OnRuleClick -> onRuleClick(event.ruleId)
            is RulesEvent.OnRuleToggleActive -> onRuleToggleActive(event.ruleId)
            is RulesEvent.OnAddRuleClick -> onAddRuleClick()
            is RulesEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is RulesEvent.OnFilterChange -> onFilterChange(event.filter)
            is RulesEvent.OnExportRuleClick -> onExportRuleClick(event.ruleId)
            is RulesEvent.OnRuleTextReceived -> onRuleTextReceived(event.text)
            RulesEvent.OnImportConfirmed -> onImportConfirmed()
            RulesEvent.OnImportCancelled -> setState { copy(importPreview = null, importSkippedActions = emptyList()) }
            RulesEvent.OnDismissImportError -> setState { copy(importError = null) }
        }
    }

    private fun loadRules() {
        setState {
            copy(
                rules = Resource.Loading(),
            )
        }

        viewModelScope.launch {
            try {
                ruleRepository.observeAllRules()
                    .collectLatest { rules ->
                        allRules.value = rules
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load rules")
                setState {
                    copy(
                        rules = Resource.Loading(),
                    )
                }
                sendEffect(RulesEffect.ShowError("Failed to load rules"))
            }
        }
    }

    private fun refreshRules() {
        viewModelScope.launch {
            ruleRepository.getAllRules()
                .onSuccess { rules ->
                    allRules.value = rules
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to refresh rules")
                    sendEffect(RulesEffect.ShowError("Failed to refresh rules"))
                }
        }
    }

    private fun applyFilters(rules: List<Rule>, query: String, currentFilter: RuleFilter): List<Rule> {
        // First apply all filters
        val filteredRules = rules.filter { rule ->
            // Apply search filter
            val matchesSearch = if (query.isBlank()) {
                true
            } else {
                rule.name.contains(query, ignoreCase = true) ||
                    rule.description?.contains(query, ignoreCase = true) == true ||
                    rule.category?.contains(query, ignoreCase = true) == true
            }

            // Apply status filter
            val matchesStatusFilter = when (currentFilter.status) {
                RuleFilter.Status.ALL -> true
                RuleFilter.Status.ENABLED -> rule.isActive
                RuleFilter.Status.DISABLED -> !rule.isActive
            }

            // Apply category filter
            val matchesCategoryFilter = if (currentFilter.selectedCategories.isEmpty()) {
                true
            } else {
                rule.category in currentFilter.selectedCategories
            }

            // Apply app filter
            val matchesAppFilter = if (currentFilter.selectedApps.isEmpty()) {
                true
            } else {
                val ruleApps = rule.targetApps?.map { it.packageName } ?: emptyList()
                // Match if any selected app is in the rule's target apps
                currentFilter.selectedApps.any { it in ruleApps }
            }

            matchesSearch && matchesStatusFilter && matchesCategoryFilter && matchesAppFilter
        }

        // Then apply sorting
        return when (currentFilter.sortBy) {
            RuleFilter.SortBy.CATEGORY_ASC -> {
                // Sort by category first, then by name within each category
                filteredRules.sortedWith(
                    compareBy<Rule> { it.category ?: "Uncategorized" }
                        .thenBy { it.name.lowercase() },
                )
            }
            RuleFilter.SortBy.NAME_ASC -> {
                filteredRules.sortedBy { it.name.lowercase() }
            }
            RuleFilter.SortBy.NAME_DESC -> {
                filteredRules.sortedByDescending { it.name.lowercase() }
            }
            RuleFilter.SortBy.CREATED_NEWEST -> {
                filteredRules.sortedByDescending { it.createdAt }
            }
            RuleFilter.SortBy.CREATED_OLDEST -> {
                filteredRules.sortedBy { it.createdAt }
            }
            RuleFilter.SortBy.UPDATED_RECENT -> {
                filteredRules.sortedByDescending { it.updatedAt }
            }
            RuleFilter.SortBy.STATUS -> {
                // Sort by status (active first), then by name
                filteredRules.sortedWith(
                    compareByDescending<Rule> { it.isActive }
                        .thenBy { it.name.lowercase() },
                )
            }
        }
    }

    private fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    private fun onFilterChange(newFilter: RuleFilter) {
        filter.value = newFilter
    }

    private fun onRuleClick(ruleId: String) {
        sendEffect(RulesEffect.NavigateToRuleEditor(ruleId))
    }

    private fun onRuleToggleActive(ruleId: String) {
        viewModelScope.launch {
            ruleRepository.toggleRuleActive(ruleId)
                .onSuccess {
                    Timber.d("Toggled rule active state: $ruleId")
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to toggle rule: $ruleId")
                    sendEffect(RulesEffect.ShowError("Failed to toggle rule"))
                }
        }
    }

    private fun onAddRuleClick() {
        sendEffect(RulesEffect.NavigateToRuleEditor())
    }

    private fun onExportRuleClick(ruleId: String) {
        viewModelScope.launch {
            ruleRepository.getRule(ruleId)
                .onSuccess { rule ->
                    if (rule == null) {
                        sendEffect(RulesEffect.ShowError("Rule not found"))
                        return@onSuccess
                    }
                    sendEffect(RulesEffect.ShareRule(ruleName = rule.name, json = RuleJsonCodec.encode(rule)))
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to load rule for export: $ruleId")
                    sendEffect(RulesEffect.ShowError("Failed to export rule"))
                }
        }
    }

    private fun onRuleTextReceived(text: String) {
        RuleJsonCodec.decode(text)
            .onSuccess { result ->
                setState {
                    copy(
                        importPreview = result.rule.withFreshIdentityForImport(),
                        importSkippedActions = result.skippedActions,
                        importError = null,
                    )
                }
            }
            .onFailure { e ->
                Timber.w(e, "Failed to decode imported rule")
                setState { copy(importError = e.message ?: "This doesn't look like a valid rule file") }
            }
    }

    private fun onImportConfirmed() {
        val rule = uiState.value.importPreview ?: return
        viewModelScope.launch {
            setState { copy(importPreview = null, importSkippedActions = emptyList()) }
            ruleRepository.saveRule(rule)
                .onSuccess {
                    Timber.d("Imported rule: ${rule.id}")
                    sendEffect(RulesEffect.ShowSuccess("Imported \"${rule.name}\" in dry-run mode"))
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to save imported rule")
                    sendEffect(RulesEffect.ShowError("Failed to import rule"))
                }
        }
    }
}
