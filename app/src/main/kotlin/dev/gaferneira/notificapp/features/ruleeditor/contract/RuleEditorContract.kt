package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.features.ruleeditor.domain.BacktestMatch
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleUiModel

/**
 * MVI Contract for the Rule Editor screen.
 *
 * Provides two-screen rule editing: main editor + add field screen.
 * The matching logic bottom sheet state is managed by its own ViewModel.
 */
object RuleEditorContract {

    /**
     * UI State for the rule editor.
     */
    data class UiState(
        /** Current step in the wizard (1 = Logic, 2 = Metadata) */
        val currentStep: Int = 1,
        /** Rule being edited */
        val rule: RuleUiModel = RuleUiModel(),
        /** Sample notification for testing */
        val sampleNotification: Notification? = null,
        /** Whether currently loading */
        val isLoading: Boolean = false,
        /** Error message if any */
        val error: String? = null,
        /** Validation errors by field */
        val validationErrors: Map<String, String> = emptyMap(),
        /** List of enabled apps for filtering available apps in the picker */
        val enabledApps: List<AppInfo> = emptyList(),
        /** Whether matching logic bottom sheet is visible */
        val isMatchingLogicSheetVisible: Boolean = false,
        /** Whether app selection bottom sheet is visible */
        val isAppSheetVisible: Boolean = false,
        /** Whether action bottom sheet is visible */
        val isActionSheetVisible: Boolean = false,
        /** Whether field bottom sheet is visible */
        val isFieldSheetVisible: Boolean = false,
        /** ID of the condition currently being edited in the bottom sheet, or null for new condition */
        val editingConditionId: String? = null,
        /** ID of the action currently being edited in the bottom sheet, or null for new action */
        val editingActionId: String? = null,
        /** ID of the field currently being edited in the bottom sheet, or null for new field */
        val editingFieldId: String? = null,
        /** Whether to show the description field (true if description is not empty) */
        val showDescription: Boolean = false,
        /** Whether to show the category field (true if category is not empty) */
        val showCategory: Boolean = false,
        /** Whether to show the delete confirmation dialog */
        val showDeleteConfirmation: Boolean = false,
        /** Whether a "test against history" run is in progress */
        val isBacktesting: Boolean = false,
        /** Results of the last "test against history" run, or null if never run */
        val backtestResults: List<BacktestMatch>? = null,
        /** Number of historical notifications tested in the last backtest run */
        val backtestTestedCount: Int = 0,
    ) {
        /** Whether the form is valid */
        val isValid: Boolean
            get() = rule.name.isNotBlank() &&
                validationErrors.isEmpty()

        /** Whether we can test extraction */
        val canTestExtraction: Boolean
            get() = sampleNotification != null &&
                rule.fields.isNotEmpty()

        /** Get the condition being edited, if any */
        val editingCondition: RuleCondition?
            get() = editingConditionId?.let { id -> rule.triggers.find { it.id == id } }

        /** Get the action being edited, if any */
        val editingAction: RuleAction?
            get() = editingActionId?.let { id -> rule.actions.find { it.id == id } }

        /** Get the field being edited, if any */
        val editingField: RuleField?
            get() = editingFieldId?.let { id -> rule.fields.find { it.id == id } }
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** Load existing rule by ID */
        data class LoadRule(val ruleId: String?) : UiEvent()

        /** Load sample notification by ID */
        data class LoadSampleNotification(val notificationId: String) : UiEvent()

        /** Navigate to next step (Continue clicked) */
        data object OnContinueClicked : UiEvent()

        /** Navigate back to logic step from metadata */
        data object OnBackToLogicClicked : UiEvent()

        /** Update rule name */
        data class OnNameChange(val name: String) : UiEvent()

        /** Update rule description */
        data class OnDescriptionChange(val description: String) : UiEvent()

        /** Show description field */
        data object OnAddDescriptionClicked : UiEvent()

        /** Update rule category */
        data class OnCategoryChange(val category: String) : UiEvent()

        /** Show category field */
        data object OnAddCategoryClicked : UiEvent()

        /** Show matching logic bottom sheet for adding a new condition */
        data object OnAddConditionClicked : UiEvent()

        /** Remove a condition from the list */
        data class OnRemoveConditionClicked(val conditionId: String) : UiEvent()

        /** Click on a condition item to edit it */
        data class OnConditionItemClicked(val conditionId: String) : UiEvent()

        /** Show app selection bottom sheet */
        data object OnAppsClicked : UiEvent()

        /** Apps selected from AppBottomSheet */
        data class OnAppsSelected(val apps: List<AppInfo>) : UiEvent()

        /** Show action bottom sheet */
        data object OnAddActionClicked : UiEvent()

        /** Click on an action item to edit it */
        data class OnEditActionClicked(val actionId: String) : UiEvent()

        /** Remove an action from the list */
        data class OnRemoveActionClicked(val actionId: String) : UiEvent()

        /** Auto-generate extraction fields */
        data object OnAutoGenerateClicked : UiEvent()

        /** Add a new extraction field */
        data object OnAddFieldClicked : UiEvent()

        /** Edit an existing extraction field */
        data class OnEditFieldClicked(val fieldId: String) : UiEvent()

        /** Remove an extraction field */
        data class OnRemoveFieldClicked(val fieldId: String) : UiEvent()

        /** Add field result returned from AddFieldScreen */
        data class OnFieldSaved(val field: RuleField) : UiEvent()
        data object OnDismissSheet : UiEvent()

        /** Condition saved from MatchingLogicBottomSheet (add or update) */
        data class OnConditionSaved(val condition: RuleCondition) : UiEvent()

        /** Action saved from ActionBottomSheet (add or update) */
        data class OnActionSaved(val action: RuleAction) : UiEvent()

        /** Test the current draft rule against captured notification history */
        data object OnTestAgainstHistoryClicked : UiEvent()

        /** Dismiss the "test against history" results */
        data object OnDismissBacktestResults : UiEvent()

        /** Save the rule */
        data object OnSaveClicked : UiEvent()

        /** Navigate back */
        data object OnBackClicked : UiEvent()

        /** Dismiss error */
        data object OnDismissError : UiEvent()

        /** Show delete confirmation dialog */
        data object OnDeleteClicked : UiEvent()

        /** Confirm rule deletion */
        data object OnDeleteConfirmed : UiEvent()

        /** Dismiss delete confirmation dialog */
        data object OnDeleteDismissed : UiEvent()
    }

    /**
     * One-time effects for navigation and actions.
     */
    sealed class UiEffect {
        /** Show success message */
        data class ShowSuccess(val message: String) : UiEffect()

        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }
}

val RuleCondition.displayText: String
    get() = "${condition?.displayName()} ${operator?.displayName()} '$value'"

val RuleAction.displayName: String
    get() = when (type) {
        ActionType.SAVE_DATA -> "Save to Data tab"
        ActionType.DISMISS_NOTIFICATION -> "Delete notification"
        ActionType.CREATE_ALARM -> "Create alarm"
        ActionType.SNOOZE_NOTIFICATION -> "Snooze notification"
        ActionType.FLASH_ALERT -> "Flash alert"
    }
