package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.features.ruleeditor.contract.ActionBottomSheetContract
import dev.gaferneira.notificapp.features.ruleeditor.domain.ActionType
import dev.gaferneira.notificapp.features.ruleeditor.domain.ActionUiModel
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * ViewModel for the ActionBottomSheet.
 *
 * Manages the state for creating and configuring actions.
 * Supports both adding new actions and editing existing ones.
 * Self-contained with no dependencies on the parent RuleEditorViewModel.
 */
@HiltViewModel
class ActionBottomSheetViewModel @Inject constructor() :
    MviViewModel<ActionBottomSheetContract.UiState, ActionBottomSheetContract.UiEvent, ActionBottomSheetContract.UiEffect>(
        ActionBottomSheetContract.UiState(),
    ) {

    /** Tracks the editing action ID when in EDIT mode */
    private var editingActionId: String? = null

    override fun onEvent(event: ActionBottomSheetContract.UiEvent) {
        when (event) {
            is ActionBottomSheetContract.UiEvent.InitForEdit -> initForEdit(event)
            is ActionBottomSheetContract.UiEvent.OnActionTypeChange -> updateActionType(event.actionType)
            is ActionBottomSheetContract.UiEvent.OnToggleSaveToDataLab -> toggleSaveToDataLab()
            is ActionBottomSheetContract.UiEvent.OnConfirm -> confirm()
            is ActionBottomSheetContract.UiEvent.OnDismiss -> dismiss()
        }
    }

    private fun initForEdit(event: ActionBottomSheetContract.UiEvent.InitForEdit) {
        editingActionId = event.actionId
        setState {
            copy(
                mode = ActionBottomSheetContract.UiState.Mode.EDIT,
                actionType = event.actionType,
                isSaveToDataLabEnabled = event.isEnabled,
                validationError = null,
            )
        }
    }

    private fun updateActionType(actionType: ActionType) {
        setState {
            copy(
                actionType = actionType,
                validationError = null,
            )
        }
    }

    private fun toggleSaveToDataLab() {
        setState { copy(isSaveToDataLabEnabled = !isSaveToDataLabEnabled) }
    }

    private fun confirm() {
        val state = uiState.value
        val actionId = editingActionId

        val action = ActionUiModel(
            id = actionId ?: UUID.randomUUID().toString(),
            type = state.actionType,
            isEnabled = if (state.actionType == ActionType.SAVE_DATA) {
                state.isSaveToDataLabEnabled
            } else {
                true
            },
        )

        if (actionId != null) {
            sendEffect(ActionBottomSheetContract.UiEffect.ActionUpdated(actionId, action))
        } else {
            sendEffect(ActionBottomSheetContract.UiEffect.ActionCreated(action))
        }

        sendEffect(ActionBottomSheetContract.UiEffect.Dismiss)
    }

    private fun dismiss() {
        viewModelScope.launch {
            // Reset state for next time
            editingActionId = null
            setState { ActionBottomSheetContract.UiState() }
            sendEffect(ActionBottomSheetContract.UiEffect.Dismiss)
        }
    }
}
