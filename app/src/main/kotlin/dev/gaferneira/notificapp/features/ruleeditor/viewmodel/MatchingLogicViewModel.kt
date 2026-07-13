package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the MatchingLogicBottomSheet.
 *
 * Manages the state for creating and editing matching conditions, across all three
 * [RuleCondition] families. Self-contained with no dependencies on the parent RuleEditorViewModel.
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
            is MatchingLogicContract.UiEvent.OnConditionTypeChange -> updateConditionType(event.type)
            is MatchingLogicContract.UiEvent.OnMatchingConditionChange -> updateMatchingCondition(event.condition)
            is MatchingLogicContract.UiEvent.OnMatchingOperatorChange -> updateMatchingOperator(event.operator)
            is MatchingLogicContract.UiEvent.OnMatchingValueChange -> updateMatchingValue(event.value)
            is MatchingLogicContract.UiEvent.OnDayToggled -> toggleDay(event.day)
            is MatchingLogicContract.UiEvent.OnStartTimeChange -> updateStartTime(event.time)
            is MatchingLogicContract.UiEvent.OnEndTimeChange -> updateEndTime(event.time)
            is MatchingLogicContract.UiEvent.OnClearError -> clearError()
            is MatchingLogicContract.UiEvent.OnConfirm -> confirm()
            is MatchingLogicContract.UiEvent.OnDismiss -> dismiss()
        }
    }

    private fun initForEdit(event: MatchingLogicContract.UiEvent.InitForEdit) {
        val condition = event.condition
        editingConditionId = condition.id
        setState {
            when (condition) {
                is RuleCondition.ContentMatchCondition -> copy(
                    mode = MatchingLogicContract.UiState.Mode.EDIT,
                    conditionType = MatchingLogicContract.ConditionType.CONTENT,
                    matchingCondition = condition.condition,
                    matchingOperator = condition.operator,
                    matchingValue = condition.value,
                    validationError = null,
                )
                is RuleCondition.DayOfWeekCondition -> copy(
                    mode = MatchingLogicContract.UiState.Mode.EDIT,
                    conditionType = MatchingLogicContract.ConditionType.DAY_OF_WEEK,
                    selectedDays = condition.days,
                    validationError = null,
                )
                is RuleCondition.TimeRangeCondition -> copy(
                    mode = MatchingLogicContract.UiState.Mode.EDIT,
                    conditionType = MatchingLogicContract.ConditionType.TIME_RANGE,
                    startTime = condition.start,
                    endTime = condition.end,
                    validationError = null,
                )
            }
        }
    }

    private fun updateConditionType(type: MatchingLogicContract.ConditionType) {
        setState { copy(conditionType = type, validationError = null) }
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

    private fun toggleDay(day: DayOfWeek) {
        setState {
            copy(
                selectedDays = if (day in selectedDays) selectedDays - day else selectedDays + day,
                validationError = null,
            )
        }
    }

    private fun updateStartTime(time: LocalTime) {
        setState { copy(startTime = time, validationError = null) }
    }

    private fun updateEndTime(time: LocalTime) {
        setState { copy(endTime = time, validationError = null) }
    }

    private fun clearError() {
        setState { copy(validationError = null) }
    }

    private fun confirm() {
        val state = uiState.value
        val conditionId = editingConditionId

        val condition = when (state.conditionType) {
            MatchingLogicContract.ConditionType.CONTENT -> {
                if (state.matchingValue.isBlank()) {
                    reportValidationError("Please enter a value to match")
                    return
                }
                RuleCondition.ContentMatchCondition(
                    id = conditionId ?: UUID.randomUUID().toString(),
                    condition = state.matchingCondition,
                    operator = state.matchingOperator,
                    value = state.matchingValue,
                )
            }
            MatchingLogicContract.ConditionType.DAY_OF_WEEK -> {
                if (state.selectedDays.isEmpty()) {
                    reportValidationError("Please select at least one day")
                    return
                }
                RuleCondition.DayOfWeekCondition(
                    id = conditionId ?: UUID.randomUUID().toString(),
                    days = state.selectedDays,
                )
            }
            MatchingLogicContract.ConditionType.TIME_RANGE -> {
                RuleCondition.TimeRangeCondition(
                    id = conditionId ?: UUID.randomUUID().toString(),
                    start = state.startTime,
                    end = state.endTime,
                )
            }
        }

        if (conditionId != null) {
            sendEffect(MatchingLogicContract.UiEffect.ConditionUpdated(conditionId, condition))
        } else {
            sendEffect(MatchingLogicContract.UiEffect.ConditionCreated(condition))
        }

        sendEffect(MatchingLogicContract.UiEffect.Dismiss)
    }

    private fun reportValidationError(message: String) {
        setState { copy(validationError = message) }
        sendEffect(MatchingLogicContract.UiEffect.ShowError(message))
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
