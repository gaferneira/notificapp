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
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import dev.gaferneira.notificapp.features.rules.contract.RuleFilter
import dev.gaferneira.notificapp.features.rules.contract.RulesEffect
import dev.gaferneira.notificapp.features.rules.contract.RulesEvent
import dev.gaferneira.notificapp.features.rules.contract.RulesUiState
import dev.gaferneira.notificapp.features.rules.contract.filterAndSort
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
 * - The applied filter and sort are persisted in [UserPreferencesRepository] (restored on start,
 *   saved whenever they change); the search query is not.
 */
@HiltViewModel
class RulesViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : MviViewModel<RulesUiState, RulesEvent, RulesEffect>(RulesUiState()) {

    private val allRules = MutableStateFlow<ImmutableList<Rule>>(persistentListOf())
    private val searchQuery = MutableStateFlow("")
    private val filter = MutableStateFlow(RuleFilter())
    private var observeRulesJob: Job? = null

    init {
        restoreSavedFilter()
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
        val filteredRules = currentFilter.filterAndSort(rules, query).toImmutableList()
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
            is RulesEvent.OnRemoveFilter -> onFilterChange(filter.value.without(event.chip))
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

    private fun onSearchQueryChange(query: String) {
        searchQuery.value = query
    }

    private fun onFilterChange(newFilter: RuleFilter) {
        filter.value = newFilter
        saveFilter(newFilter)
    }

    /** Clears search and every filter dimension; the sort order is not a filter and is kept. */
    private fun onClearFilters() {
        searchQuery.value = ""
        onFilterChange(filter.value.withoutFilters())
    }

    private fun restoreSavedFilter() {
        viewModelScope.launch {
            runCatching { userPreferencesRepository.observeRulesFilters().first() }
                .onSuccess { saved ->
                    // A filter the user changed before the restore finished wins over the saved one.
                    if (filter.value == RuleFilter()) filter.value = saved.toRuleFilter()
                }
                .onFailure { e -> Timber.e(e, "Failed to restore rules filter") }
        }
    }

    private fun saveFilter(newFilter: RuleFilter) {
        viewModelScope.launch {
            userPreferencesRepository.setRulesFilters(newFilter.toSettings())
                .onFailure { e -> Timber.e(e, "Failed to save rules filter") }
        }
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
