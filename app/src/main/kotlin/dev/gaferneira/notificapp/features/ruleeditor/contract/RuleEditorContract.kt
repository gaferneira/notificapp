package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.domain.model.ExtractionField
import dev.gaferneira.notificapp.domain.model.Notification

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
        /** Rule ID - null for new rules */
        val ruleId: String? = null,
        /** Rule name */
        val name: String = "",
        /** Rule description */
        val description: String = "",
        /** Rule category */
        val category: String = "",
        /** Rule area */
        val area: String = "",
        /** Whether this is a global rule (applies to all apps) */
        val isGlobalRule: Boolean = true,
        /** Target app package names (null = all apps) */
        val targetApps: List<String> = emptyList(),
        /** List of configured triggers */
        val triggers: List<TriggerUiModel> = emptyList(),
        /** List of configured actions */
        val actions: List<ActionUiModel> = emptyList(),
        /** Fields to extract (only for SAVE_DATA action) */
        val extractionFields: List<ExtractionFieldUiModel> = emptyList(),
        /** Sample notification for testing */
        val sampleNotification: Notification? = null,
        /** Test extraction results */
        val testResults: Map<String, TestResult> = emptyMap(),
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
        /** ID of the trigger currently being edited in the bottom sheet, or null for new trigger */
        val editingTriggerId: String? = null,
        /** ID of the action currently being edited in the bottom sheet, or null for new action */
        val editingActionId: String? = null,
    ) {
        /** Whether the form is valid */
        val isValid: Boolean
            get() = name.isNotBlank() &&
                validationErrors.isEmpty()

        /** Whether we can test extraction */
        val canTestExtraction: Boolean
            get() = sampleNotification != null &&
                extractionFields.isNotEmpty()

        /** Get the trigger being edited, if any */
        val editingTrigger: TriggerUiModel?
            get() = editingTriggerId?.let { id -> triggers.find { it.id == id } }

        /** Get the action being edited, if any */
        val editingAction: ActionUiModel?
            get() = editingActionId?.let { id -> actions.find { it.id == id } }
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

        /** Update rule category */
        data class OnCategoryChange(val category: String) : UiEvent()

        /** Update rule area */
        data class OnAreaChange(val area: String) : UiEvent()

        /** Toggle global rule setting */
        data object OnGlobalRuleToggle : UiEvent()

        /** Update target apps */
        data class OnTargetAppsChange(val apps: List<String>) : UiEvent()

        /** Show matching logic bottom sheet for adding a new trigger */
        data object OnAddTriggerClicked : UiEvent()

        /** Hide matching logic bottom sheet */
        data object OnDismissTriggerSheet : UiEvent()

        /** Handle effect from the MatchingLogicBottomSheet */
        data class OnMatchingLogicEffect(val effect: MatchingLogicContract.UiEffect) : UiEvent()

        /** Remove a trigger from the list */
        data class OnRemoveTriggerClicked(val triggerId: String) : UiEvent()

        /** Click on a trigger item to edit it */
        data class OnTriggerItemClicked(val triggerId: String) : UiEvent()

        /** Show action bottom sheet */
        data object OnAddActionClicked : UiEvent()

        /** Hide action bottom sheet */
        data object OnDismissActionSheet : UiEvent()

        /** Handle effect from the ActionBottomSheet */
        data class OnActionSheetEffect(val effect: ActionBottomSheetContract.UiEffect) : UiEvent()

        /** Click on an action item to edit it */
        data class OnEditActionClicked(val actionId: String) : UiEvent()

        /** Remove an action from the list */
        data class OnRemoveActionClicked(val actionId: String) : UiEvent()

        /** Auto-generate extraction fields */
        data object OnAutoGenerateClicked : UiEvent()

        /** Add a new extraction field */
        data object OnAddFieldClicked : UiEvent()

        /** Remove an extraction field */
        data class OnRemoveFieldClicked(val fieldId: String) : UiEvent()

        /** Add field result returned from AddFieldScreen */
        data class OnFieldAdded(val field: ExtractionField) : UiEvent()

        /** Test extraction on sample notification */
        data object OnTestExtractionClicked : UiEvent()

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
        /** Navigate back to previous screen */
        data object NavigateBack : UiEffect()

        /** Navigate to AddField screen with sample text */
        data class NavigateToAddField(val sampleText: String) : UiEffect()

        /** Show success message */
        data class ShowSuccess(val message: String) : UiEffect()

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
     * UI model for an action in the list.
     */
    data class ActionUiModel(val id: String, val type: ActionType, val isEnabled: Boolean = true) {
        val displayName: String
            get() = when (type) {
                ActionType.SAVE_DATA -> "Save to Data tab"
                ActionType.DELETE_NOTIFICATION -> "Delete notification"
                ActionType.CREATE_ALARM -> "Create alarm"
            }
    }

    /**
     * UI model for an extraction field in the list.
     */
    data class ExtractionFieldUiModel(
        val id: String,
        val name: String,
        val methodType: String,
        val methodSummary: String,
    )

    /**
     * Result of testing field extraction.
     */
    sealed class TestResult {
        data class Success(val value: String) : TestResult()
        data class Failure(val reason: String) : TestResult()
    }
}
