package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleTrigger
import dev.gaferneira.notificapp.domain.model.TriggerType
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
        /** Whether matching logic bottom sheet is visible */
        val isMatchingLogicSheetVisible: Boolean = false,
        /** Whether action bottom sheet is visible */
        val isActionSheetVisible: Boolean = false,
        /** Whether field bottom sheet is visible */
        val isFieldSheetVisible: Boolean = false,
        /** ID of the trigger currently being edited in the bottom sheet, or null for new trigger */
        val editingTriggerId: String? = null,
        /** ID of the action currently being edited in the bottom sheet, or null for new action */
        val editingActionId: String? = null,
        /** ID of the field currently being edited in the bottom sheet, or null for new field */
        val editingFieldId: String? = null,
        /** Whether to show the description field (true if description is not empty) */
        val showDescription: Boolean = false,
        /** Whether to show the category field (true if category is not empty) */
        val showCategory: Boolean = false,
    ) {
        /** Whether the form is valid */
        val isValid: Boolean
            get() = rule.name.isNotBlank() &&
                validationErrors.isEmpty()

        /** Whether we can test extraction */
        val canTestExtraction: Boolean
            get() = sampleNotification != null &&
                rule.extractionFields.isNotEmpty()

        /** Get the trigger being edited, if any */
        val editingTrigger: RuleTrigger?
            get() = editingTriggerId?.let { id -> rule.triggers.find { it.id == id } }

        /** Get the action being edited, if any */
        val editingAction: RuleAction?
            get() = editingActionId?.let { id -> rule.actions.find { it.id == id } }

        /** Get the field being edited, if any */
        val editingField: RuleField?
            get() = editingFieldId?.let { id -> rule.extractionFields.find { it.id == id } }
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

        /** Update rule area */
        data class OnAreaChange(val area: String) : UiEvent()

        /** Toggle global rule setting */
        data object OnGlobalRuleToggle : UiEvent()

        /** Update target apps */
        data class OnTargetAppsChange(val apps: List<String>) : UiEvent()

        /** Show matching logic bottom sheet for adding a new trigger */
        data object OnAddTriggerClicked : UiEvent()

        /** Remove a trigger from the list */
        data class OnRemoveTriggerClicked(val triggerId: String) : UiEvent()

        /** Click on a trigger item to edit it */
        data class OnTriggerItemClicked(val triggerId: String) : UiEvent()

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

        /** Trigger saved from MatchingLogicBottomSheet (add or update) */
        data class OnTriggerSaved(val trigger: RuleTrigger) : UiEvent()

        /** Action saved from ActionBottomSheet (add or update) */
        data class OnActionSaved(val action: RuleAction) : UiEvent()

        /** Save the rule */
        data object OnSaveClicked : UiEvent()

        /** Navigate back */
        data object OnBackClicked : UiEvent()

        /** Dismiss error */
        data object OnDismissError : UiEvent()
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

val RuleTrigger.displayText: String
    get() = when (type) {
        TriggerType.CONDITION -> "${condition?.displayName()} ${operator?.displayName()} '$value'"
        TriggerType.APP -> if (targetApps.isEmpty()) {
            "All apps selected"
        } else if (targetApps.size == 1) {
            "App is ${targetApps.first().name}"
        } else {
            "${targetApps.size} apps selected"
        }
    }

val RuleAction.displayName: String
    get() = when (type) {
        ActionType.SAVE_DATA -> "Save to Data tab"
        ActionType.DELETE_NOTIFICATION -> "Delete notification"
        ActionType.CREATE_ALARM -> "Create alarm"
    }
