package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.TriggerUiModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the MatchingLogicBottomSheet.
 *
 * Manages the state for creating and editing trigger conditions.
 * Self-contained with no dependencies on the parent RuleEditorViewModel.
 */
@HiltViewModel
class MatchingLogicViewModel @Inject constructor() :
    MviViewModel<MatchingLogicContract.UiState, MatchingLogicContract.UiEvent, MatchingLogicContract.UiEffect>(
        MatchingLogicContract.UiState(),
    ) {

    /** Tracks the editing trigger ID when in EDIT mode */
    private var editingTriggerId: String? = null

    override fun onEvent(event: MatchingLogicContract.UiEvent) {
        when (event) {
            is MatchingLogicContract.UiEvent.InitForEdit -> initForEdit(event)
            is MatchingLogicContract.UiEvent.OnTriggerTypeChange -> updateTriggerType(event.triggerType)
            is MatchingLogicContract.UiEvent.OnMatchingConditionChange -> updateMatchingCondition(event.condition)
            is MatchingLogicContract.UiEvent.OnMatchingOperatorChange -> updateMatchingOperator(event.operator)
            is MatchingLogicContract.UiEvent.OnMatchingValueChange -> updateMatchingValue(event.value)
            is MatchingLogicContract.UiEvent.OnAppsSelected -> addApps(event.apps)
            is MatchingLogicContract.UiEvent.OnRemoveApp -> removeApp(event.packageName)
            is MatchingLogicContract.UiEvent.OnClearError -> clearError()
            is MatchingLogicContract.UiEvent.OnConfirm -> confirm()
            is MatchingLogicContract.UiEvent.OnDismiss -> dismiss()
            is MatchingLogicContract.UiEvent.OnShowAppPicker -> showAppPicker()
            is MatchingLogicContract.UiEvent.OnDismissAppPicker -> hideAppPicker()
        }
    }

    private fun initForEdit(event: MatchingLogicContract.UiEvent.InitForEdit) {
        editingTriggerId = event.triggerId
        setState {
            copy(
                mode = MatchingLogicContract.UiState.Mode.EDIT,
                triggerType = event.triggerType,
                matchingCondition = event.condition ?: MatchingLogicContract.MatchingCondition.TEXT_CONTENT,
                matchingOperator = event.operator ?: MatchingLogicContract.MatchingOperator.CONTAINS,
                matchingValue = event.value ?: "",
                selectedApps = event.selectedApps,
                validationError = null,
            )
        }
    }

    private fun updateTriggerType(triggerType: MatchingLogicContract.TriggerType) {
        setState {
            copy(
                triggerType = triggerType,
                validationError = null,
            )
        }
    }

    private fun updateMatchingCondition(condition: MatchingLogicContract.MatchingCondition) {
        setState { copy(matchingCondition = condition) }
    }

    private fun updateMatchingOperator(operator: MatchingLogicContract.MatchingOperator) {
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

    private fun addApps(apps: List<MatchingLogicContract.AppInfo>) {
        setState {
            copy(
                selectedApps = apps,
                validationError = null,
            )
        }
        hideAppPicker()
    }

    private fun removeApp(packageName: String) {
        setState {
            copy(selectedApps = selectedApps.filter { it.packageName != packageName })
        }
    }

    private fun clearError() {
        setState { copy(validationError = null) }
    }

    private fun confirm() {
        val state = uiState.value
        val triggerId = editingTriggerId

        when (state.triggerType) {
            MatchingLogicContract.TriggerType.CONDITION -> {
                if (state.matchingValue.isBlank()) {
                    setState { copy(validationError = "Please enter a value to match") }
                    sendEffect(MatchingLogicContract.UiEffect.ShowError("Please enter a value to match"))
                    return
                }

                val trigger = TriggerUiModel(
                    id = triggerId ?: UUID.randomUUID().toString(),
                    type = MatchingLogicContract.TriggerType.CONDITION,
                    condition = state.matchingCondition,
                    operator = state.matchingOperator,
                    value = state.matchingValue,
                )

                if (triggerId != null) {
                    sendEffect(MatchingLogicContract.UiEffect.TriggerUpdated(triggerId, trigger))
                } else {
                    sendEffect(MatchingLogicContract.UiEffect.TriggerCreated(trigger))
                }
            }

            MatchingLogicContract.TriggerType.APP -> {
                val trigger = TriggerUiModel(
                    id = triggerId ?: UUID.randomUUID().toString(),
                    type = MatchingLogicContract.TriggerType.APP,
                    selectedApps = state.selectedApps,
                )

                if (triggerId != null) {
                    sendEffect(MatchingLogicContract.UiEffect.TriggerUpdated(triggerId, trigger))
                } else {
                    sendEffect(MatchingLogicContract.UiEffect.TriggerCreated(trigger))
                }
            }
        }

        sendEffect(MatchingLogicContract.UiEffect.Dismiss)
    }

    private fun dismiss() {
        viewModelScope.launch {
            // Reset state for next time
            editingTriggerId = null
            setState { MatchingLogicContract.UiState() }
            sendEffect(MatchingLogicContract.UiEffect.Dismiss)
        }
    }

    private fun showAppPicker() {
        setState { copy(isAppPickerVisible = true) }
    }

    private fun hideAppPicker() {
        setState { copy(isAppPickerVisible = false) }
    }
}
