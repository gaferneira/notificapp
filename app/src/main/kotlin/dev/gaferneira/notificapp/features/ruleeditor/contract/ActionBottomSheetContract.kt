package dev.gaferneira.notificapp.features.ruleeditor.contract

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
        val actionType: ActionType = ActionType.SAVE_DATA,
        /** Whether Save to Data Lab is enabled (only for SAVE_DATA) */
        val isSaveToDataLabEnabled: Boolean = true,
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
        data class InitForEdit(val actionId: String, val actionType: ActionType, val isEnabled: Boolean) : UiEvent()

        /** Update action type */
        data class OnActionTypeChange(val actionType: ActionType) : UiEvent()

        /** Toggle Save to Data Lab setting */
        data object OnToggleSaveToDataLab : UiEvent()

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
        data class ActionCreated(val action: ActionUiModel) : UiEffect()

        /** Existing action updated */
        data class ActionUpdated(val actionId: String, val action: ActionUiModel) : UiEffect()

        /** Dismiss the sheet */
        data object Dismiss : UiEffect()

        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }

    /**
     * Action to take when rule matches.
     */
    enum class ActionType {
        SAVE_DATA,
        DELETE_NOTIFICATION,
        CREATE_ALARM,
    }

    /**
     * UI model for an action.
     */
    data class ActionUiModel(val id: String, val type: ActionType, val isEnabled: Boolean = true) {
        val displayName: String
            get() = when (type) {
                ActionType.SAVE_DATA -> "Save to Data tab"
                ActionType.DELETE_NOTIFICATION -> "Delete notification"
                ActionType.CREATE_ALARM -> "Create alarm"
            }
    }
}
