package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.features.ruleeditor.contract.ActionBottomSheetContract
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
            is ActionBottomSheetContract.UiEvent.OnSnoozeDurationChange -> updateSnoozeDuration(event.minutes)
            is ActionBottomSheetContract.UiEvent.OnAlarmSoundChange -> updateAlarmSound(event.uri)
            is ActionBottomSheetContract.UiEvent.OnAlarmVibrationToggle -> updateAlarmVibration(event.enabled)
            is ActionBottomSheetContract.UiEvent.OnFlashCountChange -> updateFlashCount(event.count)
            is ActionBottomSheetContract.UiEvent.OnFlashDurationChange -> updateFlashDuration(event.durationMs)
            is ActionBottomSheetContract.UiEvent.OnConfirm -> confirm()
            is ActionBottomSheetContract.UiEvent.OnDismiss -> dismiss()
        }
    }

    private fun initForEdit(event: ActionBottomSheetContract.UiEvent.InitForEdit) {
        val action = event.action
        editingActionId = action.id
        setState {
            copy(
                mode = ActionBottomSheetContract.UiState.Mode.EDIT,
                actionType = action.type,
                snoozeDurationMinutes = action.getSnoozeDurationMinutes(),
                alarmSoundUri = action.getAlarmSoundUri(),
                alarmVibrationEnabled = action.isAlarmVibrationEnabled(),
                flashCount = action.getFlashCount(),
                flashDurationMs = action.getFlashDurationMs(),
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

    private fun updateSnoozeDuration(minutes: Int) {
        setState {
            copy(snoozeDurationMinutes = minutes)
        }
    }

    private fun updateAlarmSound(uri: String?) {
        setState {
            copy(alarmSoundUri = uri)
        }
    }

    private fun updateAlarmVibration(enabled: Boolean) {
        setState {
            copy(alarmVibrationEnabled = enabled)
        }
    }

    private fun updateFlashCount(count: Int) {
        setState {
            copy(flashCount = count)
        }
    }

    private fun updateFlashDuration(durationMs: Long) {
        setState {
            copy(flashDurationMs = durationMs)
        }
    }

    private fun confirm() {
        val state = uiState.value
        val actionType = state.actionType ?: return
        val actionId = editingActionId

        val action = when (actionType) {
            ActionType.SNOOZE_NOTIFICATION -> {
                RuleAction.createSnooze(
                    id = actionId ?: UUID.randomUUID().toString(),
                    durationMinutes = state.snoozeDurationMinutes,
                )
            }
            ActionType.CREATE_ALARM -> {
                RuleAction.createAlarm(
                    id = actionId ?: UUID.randomUUID().toString(),
                    soundUri = state.alarmSoundUri,
                    vibrationEnabled = state.alarmVibrationEnabled,
                )
            }
            ActionType.FLASH_ALERT -> {
                RuleAction.createFlashAlert(
                    id = actionId ?: UUID.randomUUID().toString(),
                    flashCount = state.flashCount,
                    flashDurationMs = state.flashDurationMs,
                )
            }
            else -> {
                RuleAction(
                    id = actionId ?: UUID.randomUUID().toString(),
                    type = actionType,
                )
            }
        }

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
