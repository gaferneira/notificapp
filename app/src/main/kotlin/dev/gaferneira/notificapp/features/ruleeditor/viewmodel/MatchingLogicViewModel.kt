package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the MatchingLogicBottomSheet.
 *
 * Manages the state for creating and editing matching conditions.
 * Self-contained with no dependencies on the parent RuleEditorViewModel.
 */
@HiltViewModel
class MatchingLogicViewModel @Inject constructor() :
    MviViewModel<MatchingLogicContract.UiState, MatchingLogicContract.UiEvent, MatchingLogicContract.UiEffect>(
        MatchingLogicContract.UiState(),
    ) {

    /** Tracks the editing condition ID when in EDIT mode */
    private var editingConditionId: String? = null

    override fun onEvent(event: MatchingLogicContract.UiEvent) {
        when (event) {
            is MatchingLogicContract.UiEvent.InitForEdit -> initForEdit(event)
            is MatchingLogicContract.UiEvent.OnMatchingConditionChange -> updateMatchingCondition(event.condition)
            is MatchingLogicContract.UiEvent.OnMatchingOperatorChange -> updateMatchingOperator(event.operator)
            is MatchingLogicContract.UiEvent.OnMatchingValueChange -> updateMatchingValue(event.value)
            is MatchingLogicContract.UiEvent.OnClearError -> clearError()
            is MatchingLogicContract.UiEvent.OnConfirm -> confirm()
            is MatchingLogicContract.UiEvent.OnDismiss -> dismiss()
        }
    }

    private fun initForEdit(event: MatchingLogicContract.UiEvent.InitForEdit) {
        val condition = event.condition
        editingConditionId = condition.id
        setState {
            copy(
                mode = MatchingLogicContract.UiState.Mode.EDIT,
                matchingCondition = condition.condition ?: MatchingCondition.TEXT_CONTENT,
                matchingOperator = condition.operator ?: MatchingOperator.CONTAINS,
                matchingValue = condition.value ?: "",
                validationError = null,
            )
        }
    }

    private fun updateMatchingCondition(condition: MatchingCondition) {
        setState { copy(matchingCondition = condition) }
    }

    private fun updateMatchingOperator(operator: MatchingOperator) {
        setState { copy(matchingOperator = operator) }
    }

    private fun updateMatchingValue(value: String) {
        setState {
            copy(
                matchingValue = value,
                validationError = null,
            )
        }
    }

    private fun clearError() {
        setState { copy(validationError = null) }
    }

    private fun confirm() {
        val state = uiState.value
        val conditionId = editingConditionId

        if (state.matchingValue.isBlank()) {
            setState { copy(validationError = "Please enter a value to match") }
            sendEffect(MatchingLogicContract.UiEffect.ShowError("Please enter a value to match"))
            return
        }

        val condition = RuleCondition(
            id = conditionId ?: UUID.randomUUID().toString(),
            condition = state.matchingCondition,
            operator = state.matchingOperator,
            value = state.matchingValue,
        )

        if (conditionId != null) {
            sendEffect(MatchingLogicContract.UiEffect.ConditionUpdated(conditionId, condition))
        } else {
            sendEffect(MatchingLogicContract.UiEffect.ConditionCreated(condition))
        }

        sendEffect(MatchingLogicContract.UiEffect.Dismiss)
    }

    private fun dismiss() {
        viewModelScope.launch {
            // Reset state for next time
            editingConditionId = null
            setState { MatchingLogicContract.UiState() }
            sendEffect(MatchingLogicContract.UiEffect.Dismiss)
        }
    }
}
