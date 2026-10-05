package dev.gaferneira.notificapp.features.ruledetails.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.ruledetails.contract.RuleDetailsContract.UiEffect
import dev.gaferneira.notificapp.features.ruledetails.contract.RuleDetailsContract.UiEvent
import dev.gaferneira.notificapp.features.ruledetails.contract.RuleDetailsContract.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Rule Details screen.
 *
 * Observes the rule through [RuleRepository.observeRule] so edits made in the editor are
 * reflected when the user returns, and its match statistics through
 * [RuleExecutionRepository.observeRuleStats]. When the rule disappears (deleted here or from the editor
 * stacked above this screen) the screen pops itself.
 */
@HiltViewModel
class RuleDetailsViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val ruleExecutionRepository: RuleExecutionRepository,
    private val navigationHandler: NavigationHandler,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    private var ruleId: String? = null
    private var observeJob: Job? = null
    private var statsJob: Job? = null
    private var hasExited = false

    /**
     * Set the ID of the rule to display and start observing it.
     */
    fun setRuleId(id: String) {
        if (ruleId == id) return
        ruleId = id
        hasExited = false
        observeRule(id)
        observeStats(id)
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            UiEvent.OnBackClicked -> exit()
            UiEvent.OnEditClicked -> onEditClicked()
            UiEvent.OnToggleActiveClicked -> onToggleActiveClicked()
            UiEvent.OnShareClicked -> onShareClicked()
            UiEvent.OnDeleteClicked -> setState { copy(showDeleteConfirmation = true) }
            UiEvent.OnDeleteConfirmed -> onDeleteConfirmed()
            UiEvent.OnDeleteDismissed -> setState { copy(showDeleteConfirmation = false) }
            UiEvent.OnGoLiveClicked -> setState { copy(showGoLiveConfirmation = true) }
            UiEvent.OnGoLiveConfirmed -> onGoLiveConfirmed()
            UiEvent.OnGoLiveDismissed -> setState { copy(showGoLiveConfirmation = false) }
            UiEvent.OnRetryClicked -> ruleId?.let {
                observeRule(it)
                observeStats(it)
            }
        }
    }

    private fun observeRule(id: String) {
        observeJob?.cancel()
        setState { copy(isLoading = true, error = null) }
        observeJob = viewModelScope.launch {
            ruleRepository.observeRule(id)
                .catch { e ->
                    Timber.e(e, "Failed to observe rule: $id")
                    setState { copy(isLoading = false, error = UiText.StringResource(R.string.rule_details_error_load)) }
                }
                .collect { rule ->
                    if (rule == null) {
                        // Deleted (here or from the editor) or never existed: nothing left to show.
                        exit()
                    } else {
                        setState { copy(rule = rule, isLoading = false, error = null) }
                    }
                }
        }
    }

    /**
     * Stats are secondary: a failure is logged and the section hidden (`stats = null`), never
     * surfaced as a screen error.
     */
    private fun observeStats(id: String) {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            ruleExecutionRepository.observeRuleStats(id)
                .catch { e ->
                    Timber.e(e, "Failed to observe stats for rule: $id")
                    setState { copy(stats = null) }
                }
                .collect { stats -> setState { copy(stats = stats) } }
        }
    }

    private fun onEditClicked() {
        val id = ruleId ?: return
        viewModelScope.launch {
            navigationHandler.navigate(Routes.ruleEditor(ruleId = id))
        }
    }

    private fun onToggleActiveClicked() {
        val id = ruleId ?: return
        viewModelScope.launch {
            ruleRepository.toggleRuleActive(id)
                .onFailure { e ->
                    Timber.e(e, "Failed to toggle rule: $id")
                    sendEffect(UiEffect.ShowMessage(UiText.StringResource(R.string.rule_details_error_toggle)))
                }
        }
    }

    private fun onShareClicked() {
        val rule = uiState.value.rule ?: return
        sendEffect(UiEffect.ShareRule(ruleName = rule.name, json = RuleJsonCodec.encode(rule)))
    }

    private fun onDeleteConfirmed() {
        val id = ruleId ?: return
        setState { copy(showDeleteConfirmation = false) }
        viewModelScope.launch {
            ruleRepository.deleteRule(id)
                .onSuccess { exit() }
                .onFailure { e ->
                    Timber.e(e, "Failed to delete rule: $id")
                    sendEffect(UiEffect.ShowMessage(UiText.StringResource(R.string.rule_details_error_delete)))
                }
        }
    }

    /**
     * Turns dry run off and saves the rule through [RuleRepository.updateRule], like the editor
     * does (which also refreshes `updatedAt`). The observed rule then re-emits with
     * `isDryRun = false`, so the banner disappears without any extra state handling here.
     */
    private fun onGoLiveConfirmed() {
        val rule = uiState.value.rule
        setState { copy(showGoLiveConfirmation = false) }
        if (rule == null || !rule.isDryRun || uiState.value.isGoingLive) return
        setState { copy(isGoingLive = true) }
        viewModelScope.launch {
            ruleRepository.updateRule(rule.copy(isDryRun = false, updatedAt = System.currentTimeMillis()))
                .onSuccess {
                    setState { copy(isGoingLive = false) }
                    sendEffect(UiEffect.ShowMessage(UiText.StringResource(R.string.rule_details_go_live_success)))
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to take rule live: ${rule.id}")
                    setState { copy(isGoingLive = false) }
                    sendEffect(UiEffect.ShowMessage(UiText.StringResource(R.string.rule_details_error_go_live)))
                }
        }
    }

    /**
     * Pops this screen at most once. Both a successful delete and the observed rule turning null
     * lead here, and popping twice would also pop the screen underneath.
     */
    private fun exit() {
        if (hasExited) return
        hasExited = true
        viewModelScope.launch {
            navigationHandler.goBack()
        }
    }
}
