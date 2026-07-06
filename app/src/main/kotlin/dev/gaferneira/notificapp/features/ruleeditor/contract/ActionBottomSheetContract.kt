package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_FULLSCREEN_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_VIBRATION_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_FLASH_COUNT
import dev.gaferneira.notificapp.domain.model.DEFAULT_FLASH_DURATION_MS
import dev.gaferneira.notificapp.domain.model.DEFAULT_SNOOZE_DURATION_MINUTES
import dev.gaferneira.notificapp.domain.model.RuleAction

/**
 * MVI Contract for the ActionBottomSheet.
 *
 * Manages the state for creating and configuring actions (DO logic).
 */
object ActionBottomSheetContract {

    /**
     * UI State for the action bottom sheet.
     */
    data class UiState(
        /** Current mode: adding new or editing existing */
        val mode: Mode = Mode.ADD,
        /** Selected action type */
        val actionType: ActionType? = null,
        /** Snooze duration in minutes (for SNOOZE_NOTIFICATION type) */
        val snoozeDurationMinutes: Int = DEFAULT_SNOOZE_DURATION_MINUTES,
        /** Selected alarm sound URI (for CREATE_ALARM type), or null for the device default */
        val alarmSoundUri: String? = null,
        /** Whether the alarm should also vibrate (for CREATE_ALARM type) */
        val alarmVibrationEnabled: Boolean = DEFAULT_ALARM_VIBRATION_ENABLED,
        /** Whether the alarm shows the full-screen call-style UI (for CREATE_ALARM type) */
        val alarmFullScreenEnabled: Boolean = DEFAULT_ALARM_FULLSCREEN_ENABLED,
        /** Number of torch flashes (for FLASH_ALERT type) */
        val flashCount: Int = DEFAULT_FLASH_COUNT,
        /** Duration of each flash phase in milliseconds (for FLASH_ALERT type) */
        val flashDurationMs: Long = DEFAULT_FLASH_DURATION_MS,
        /** Validation error message, if any */
        val validationError: String? = null,
    ) {
        enum class Mode {
            ADD,
            EDIT,
        }
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** Initialize the sheet for editing an existing action */
        data class InitForEdit(val action: RuleAction) : UiEvent()

        /** Update action type */
        data class OnActionTypeChange(val actionType: ActionType) : UiEvent()

        /** Update snooze duration in minutes */
        data class OnSnoozeDurationChange(val minutes: Int) : UiEvent()

        /** Update the selected alarm sound URI (null for the device default) */
        data class OnAlarmSoundChange(val uri: String?) : UiEvent()

        /** Toggle whether the alarm should also vibrate */
        data class OnAlarmVibrationToggle(val enabled: Boolean) : UiEvent()

        /** Toggle whether the alarm shows the full-screen call-style UI */
        data class OnAlarmFullScreenToggle(val enabled: Boolean) : UiEvent()

        /** Update the number of torch flashes */
        data class OnFlashCountChange(val count: Int) : UiEvent()

        /** Update the duration of each flash phase in milliseconds */
        data class OnFlashDurationChange(val durationMs: Long) : UiEvent()

        /** Confirm and create/update action */
        data object OnConfirm : UiEvent()

        /** Dismiss without saving */
        data object OnDismiss : UiEvent()
    }

    /**
     * One-time effects to communicate with the parent.
     */
    sealed class UiEffect {
        /** New action created */
        data class ActionCreated(val action: RuleAction) : UiEffect()

        /** Existing action updated */
        data class ActionUpdated(val actionId: String, val action: RuleAction) : UiEffect()

        /** Dismiss the sheet */
        data object Dismiss : UiEffect()

        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }
}
