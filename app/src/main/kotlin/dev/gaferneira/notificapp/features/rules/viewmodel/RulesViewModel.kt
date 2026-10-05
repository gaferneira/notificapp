package dev.gaferneira.notificapp.features.rules.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.common.Failure
import dev.gaferneira.notificapp.core.rulesharing.RuleImportFailure
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec.withFreshIdentityForImport
import dev.gaferneira.notificapp.core.ui.Resource
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.appliesToPackage
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesEffect
import dev.gaferneira.notificapp.features.rules.contract.RulesEvent
import dev.gaferneira.notificapp.features.rules.contract.RulesUiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
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

    private val allRules = MutableStateFlow<ImmutableList<Rule>>(persistentListOf())
    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(RuleFilter())
    private var observeRulesJob: Job? = null

    init {
        loadRules()

        // Combine all state flows to produce filtered rules
        viewModelScope.launch {
            combine(allRules, searchQuery, filter) { _, _, _ -> }
                .collectLatest {
                    // A failed load stays visible until the user retries.
                    if (uiState.value.rules !is Resource.Error) publishRules()
                }
        }
    }

    private fun publishRules() {
        val rules = allRules.value
        val query = searchQuery.value
        val currentFilter = filter.value
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

    override fun onEvent(event: RulesEvent) {
        when (event) {
            is RulesEvent.LoadRules -> loadRules()
            is RulesEvent.Refresh -> refreshRules()
            is RulesEvent.OnRuleClick -> onRuleClick(event.ruleId)
            is RulesEvent.OnRuleToggleActive -> onRuleToggleActive(event.ruleId)
            is RulesEvent.OnAddRuleClick -> onAddRuleClick()
            is RulesEvent.OnSearchQueryChange -> onSearchQueryChange(event.query)
            is RulesEvent.OnFilterChange -> onFilterChange(event.filter)
            RulesEvent.OnClearFilters -> onClearFilters()
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

        observeRulesJob?.cancel()
        observeRulesJob = viewModelScope.launch {
            ruleRepository.observeAllRules()
                .catch { e ->
                    Timber.e(e, "Failed to load rules")
                    setState { copy(rules = Resource.Error(Failure.UnknownException(e))) }
                }
                .collectLatest { rules ->
                    allRules.value = rules.toImmutableList()
                    // After a failed load + retry the list may be unchanged, so the combine in
                    // init does not re-emit; leave the Loading/Error state explicitly.
                    if (uiState.value.rules !is Resource.Success) publishRules()
                }
        }
    }

    private fun refreshRules() {
        viewModelScope.launch {
            ruleRepository.getAllRules()
                .onSuccess { rules ->
                    allRules.value = rules.toImmutableList()
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to refresh rules")
                    sendEffect(RulesEffect.ShowError(UiText.StringResource(R.string.rules_error_refresh)))
                }
        }
    }

    private fun applyFilters(rules: ImmutableList<Rule>, query: String, currentFilter: RuleFilter): ImmutableList<Rule> {
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

            // Apply app filter using effective scope, not literal list membership.
            // An exclude-mode rule that omits a selected app still fires for it, so it matches.
            val matchesAppFilter = if (currentFilter.selectedApps.isEmpty()) {
                true
            } else {
                currentFilter.selectedApps.any { rule.appliesToPackage(it) }
            }

            matchesSearch && matchesStatusFilter && matchesCategoryFilter && matchesAppFilter
        }

        // Then apply sorting
        val sortedRules = when (currentFilter.sortBy) {
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
        return sortedRules.toImmutableList()
    }

    private fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    private fun onFilterChange(newFilter: RuleFilter) {
        filter.value = newFilter
    }

    private fun onClearFilters() {
        searchQuery.value = ""
        filter.value = RuleFilter()
    }

    private fun onRuleClick(ruleId: String) {
        sendEffect(RulesEffect.NavigateToRuleDetails(ruleId))
    }

    private fun onRuleToggleActive(ruleId: String) {
        viewModelScope.launch {
            ruleRepository.toggleRuleActive(ruleId)
                .onSuccess {
                    Timber.d("Toggled rule active state: $ruleId")
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to toggle rule: $ruleId")
                    sendEffect(RulesEffect.ShowError(UiText.StringResource(R.string.rules_error_toggle)))
                }
        }
    }

    private fun onAddRuleClick() {
        sendEffect(RulesEffect.NavigateToRuleEditor())
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
                setState { copy(importError = e.toImportErrorText()) }
            }
    }

    private fun onImportConfirmed() {
        val rule = uiState.value.importPreview ?: return
        viewModelScope.launch {
            setState { copy(importPreview = null, importSkippedActions = emptyList()) }
            ruleRepository.saveRule(rule)
                .onSuccess {
                    Timber.d("Imported rule: ${rule.id}")
                    sendEffect(RulesEffect.ShowSuccess(UiText.StringResource(R.string.rules_imported_dry_run, arrayOf(rule.name))))
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to save imported rule")
                    sendEffect(RulesEffect.ShowError(UiText.StringResource(R.string.rules_error_import)))
                }
        }
    }
}

/**
 * Maps a [RuleJsonCodec.decode] failure to a localized message. The codec's English `message` is
 * a technical detail for logs only and is never shown; unknown failures fall back to the generic
 * "not a valid rule" text.
 */
internal fun Throwable.toImportErrorText(): UiText = when (this) {
    is RuleImportFailure.UnsupportedSchemaVersion -> UiText.StringResource(R.string.rules_import_error_newer_version)
    is RuleImportFailure.MissingName -> UiText.StringResource(R.string.rules_import_error_missing_name)
    is RuleImportFailure.UnknownValue -> UiText.StringResource(R.string.rules_import_error_unknown_value, arrayOf(value))
    is RuleImportFailure.NestedTooDeeply -> UiText.StringResource(R.string.rules_import_error_too_deep, arrayOf(maxDepth))
    else -> UiText.StringResource(R.string.rules_import_invalid)
}
