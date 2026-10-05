package dev.gaferneira.notificapp.features.ruleeditor.contract

import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.features.ruleeditor.domain.BacktestMatch
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorIssue
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorStep
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorWarning
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleUiModel
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleValidation
import dev.gaferneira.notificapp.features.ruleeditor.domain.ValidationResult
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * MVI Contract for the Rule Editor screen.
 *
 * Two presentations of one draft: a guided 3-step create flow and a single-page editor.
 * The matching logic bottom sheet state is managed by its own ViewModel.
 */
object RuleEditorContract {

    /**
     * UI State for the rule editor.
     */
    data class UiState(
        /**
         * How the editor is presented. Decided once from [InitArgs] when the editor opens (and
         * persisted with the draft), never recomputed from the rule's content.
         */
        val mode: EditorMode = EditorMode.GUIDED,
        /** Current step of the guided flow; ignored in [EditorMode.SINGLE_PAGE]. */
        val currentStep: EditorStep = EditorStep.WHEN,
        /** Whether the draft was started from a starter template (drives the one-line hint card). */
        val isFromTemplate: Boolean = false,
        /**
         * Whether the editor was opened on an already saved rule. Decided from [InitArgs.ruleId], so
         * the title reads "Edit rule" even when that rule fails to load (and [RuleUiModel.id] is null).
         */
        val isExistingRule: Boolean = false,
        /** Whether a condition was prefilled from the source notification and its hint card is still showing. */
        val showPrefillHint: Boolean = false,
        /** Rule being edited */
        val rule: RuleUiModel = RuleUiModel(),
        /** Sample notification for testing */
        val sampleNotification: Notification? = null,
        /** Whether the initial load (rule / template / sample notification) is in progress */
        val isLoading: Boolean = false,
        /** Whether a save or delete is in flight (disables Save, shows a progress indicator) */
        val isSaving: Boolean = false,
        /** Save/delete failure to surface (snackbar), dismissed with [UiEvent.OnDismissError] */
        val error: UiText? = null,
        /** Blocking failure of the initial load; when set the form is replaced by an error state */
        val loadError: LoadError? = null,
        /**
         * Snapshot of [rule] taken once the prefill (rule / template / sample notification) or a
         * saved-state restore completed. [hasUnsavedChanges] compares the draft against it.
         */
        val initialRule: RuleUiModel = RuleUiModel(),
        /** Whether the discard-changes confirmation is visible */
        val showUnsavedChangesDialog: Boolean = false,
        /** Validation errors by field */
        val validationErrors: Map<String, UiText> = emptyMap(),
        /** List of enabled apps for filtering available apps in the picker */
        val enabledApps: ImmutableList<AppInfo> = persistentListOf(),
        /** Whether matching logic bottom sheet is visible */
        val isMatchingLogicSheetVisible: Boolean = false,
        /** Whether app selection bottom sheet is visible */
        val isAppSheetVisible: Boolean = false,
        /** Whether the action type-picker dialog is visible */
        val isActionTypePickerVisible: Boolean = false,
        /** Pre-selected type for a NEW config action, seeding the action sheet (null when editing) */
        val pendingActionType: ActionType? = null,
        /** Whether action bottom sheet is visible (config actions: snooze/alarm/dismiss/flash) */
        val isActionSheetVisible: Boolean = false,
        /** Action id awaiting remove confirmation because it is an Extract-data action with fields */
        val pendingExtractDataRemovalId: String? = null,
        /** ID of the condition currently being edited in the bottom sheet, or null for new condition */
        val editingConditionId: String? = null,
        /** ID of the action currently being edited in the bottom sheet, or null for new action */
        val editingActionId: String? = null,
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

        /** True when the draft differs structurally from [initialRule]; reverting an edit makes it false again. */
        val hasUnsavedChanges: Boolean
            get() = rule != initialRule

        /** Validation of the current draft; every gate and message below derives from it. */
        val validation: ValidationResult
            get() = RuleValidation.evaluate(rule)

        /** Everything that currently prevents saving; empty when the draft is saveable. */
        val blockingIssues: List<EditorIssue>
            get() = validation.blocking

        /** Non-blocking heads-ups about the draft. */
        val warnings: List<EditorWarning>
            get() = validation.warnings

        /** Blocking issues that belong to [step]. */
        fun blockingIssuesFor(step: EditorStep): List<EditorIssue> = blockingIssues.filter { it.step == step }

        /** Warnings that belong to [step]. */
        fun warningsFor(step: EditorStep): List<EditorWarning> = warnings.filter { it.step == step }

        /** Blocking issues of steps before the review step, to surface (with a jump) on the review step. */
        val earlierStepIssues: List<EditorIssue>
            get() = blockingIssues.filter { it.step != EditorStep.REVIEW }

        /** The issue that currently disables Next on [currentStep] (guided flow), or null when Next is allowed. */
        val nextBlockedBy: EditorIssue?
            get() = if (mode == EditorMode.GUIDED) blockingIssuesFor(currentStep).firstOrNull { it.nextBlockedMessage != null } else null

        /** Next is offered on a non-final guided step whose own requirements are met. */
        val canGoNext: Boolean
            get() = mode == EditorMode.GUIDED && currentStep.next() != null && nextBlockedBy == null && !isSaving

        /** Save is offered only for a valid draft that is not loading or already being saved. */
        val canSave: Boolean
            get() = blockingIssues.isEmpty() && !isLoading && !isSaving

        /** Delete only applies to rules that already exist. */
        val canDelete: Boolean
            get() = rule.id != null

        /** Deleting raw content only makes sense when the rule extracts data. */
        val showDeleteRawContentToggle: Boolean
            get() = rule.actions.any { it.type == ActionType.SAVE_DATA }

        /** Gated so the first tap doesn't test an empty rule against the entire notification history. */
        val canTestAgainstHistory: Boolean
            get() = rule.triggers.isNotEmpty() || rule.targetApps.isNotEmpty()

        /** Template drafts that still need the user to choose apps get a one-line hint. */
        val showTemplateHint: Boolean
            get() = mode == EditorMode.SINGLE_PAGE && isFromTemplate && rule.id == null && rule.targetApps.isEmpty()

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
    }

    /**
     * Blocking failure of the initial load.
     *
     * @property message user-facing explanation
     * @property canRetry whether repeating the load can plausibly succeed (false for "not found")
     */
    data class LoadError(val message: UiText, val canRetry: Boolean)

    /** Presentation of the editor: a 3-step guided flow or one scrollable page. */
    enum class EditorMode {
        /** Creating a rule from scratch: When, Do, then Name & review. */
        GUIDED,

        /** Editing an existing rule, or creating from a template / notification (content is prefilled). */
        SINGLE_PAGE,
    }

    /** Display text of a starter template resolved in the current app language by the UI layer. */
    data class LocalizedTemplateText(
        val name: String,
        val description: String,
        val category: String,
    )

    /**
     * Navigation arguments that determine what the editor pre-fills. Loading is idempotent per
     * distinct [InitArgs], so re-sending [UiEvent.Initialize] after a recomposition or
     * configuration change never overwrites in-progress edits.
     */
    data class InitArgs(
        val ruleId: String? = null,
        val notificationId: String? = null,
        val templateAssetFileName: String? = null,
        /** Template name/description/category in the app language; the JSON asset itself stays English. */
        val templateText: LocalizedTemplateText? = null,
    ) {
        /** Only a rule created from scratch gets the guided flow; anything prefilled is a single page. */
        val editorMode: EditorMode
            get() = if (ruleId == null && notificationId == null && templateAssetFileName == null) {
                EditorMode.GUIDED
            } else {
                EditorMode.SINGLE_PAGE
            }
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /**
         * Pre-fill the editor from the navigation arguments (existing rule, starter template and/or
         * sample notification). Safe to send repeatedly: a second call with the same args is ignored.
         */
        data class Initialize(val args: InitArgs) : UiEvent()

        /** Retry a failed initial load */
        data object OnRetryLoadClicked : UiEvent()

        /** Guided flow: advance to the next step (no-op on the last step or in single-page mode) */
        data object OnNextStepClicked : UiEvent()

        /**
         * A blocking [issue] was tapped: the guided flow jumps back to the step it belongs to (the
         * single-page editor scrolls to the section in the UI instead, so this is a no-op there).
         */
        data class OnIssueClicked(val issue: EditorIssue) : UiEvent()

        /** Dismiss the "we pre-filled a condition" hint */
        data object OnPrefillHintDismissed : UiEvent()

        /** Guided flow: return to the previous step (no-op on the first step or in single-page mode) */
        data object OnPreviousStepClicked : UiEvent()

        /** Guided flow: jump back to an already completed [step] from the step indicator */
        data class OnStepSelected(val step: EditorStep) : UiEvent()

        /** Update rule name */
        data class OnNameChange(val name: String) : UiEvent()

        /** Update rule description */
        data class OnDescriptionChange(val description: String) : UiEvent()

        /** Show description field */
        data object OnAddDescriptionClicked : UiEvent()

        /** Update rule category */
        data class OnCategoryChange(val category: String) : UiEvent()

        /** Toggle dry-run mode for this rule */
        data class OnDryRunToggle(val enabled: Boolean) : UiEvent()

        /** Toggle whether the source notification's raw text is scrubbed after extraction */
        data class OnDeleteRawContentToggle(val enabled: Boolean) : UiEvent()

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
        data class OnAppsSelected(val apps: ImmutableList<AppInfo>) : UiEvent()

        /** Toggle between include-list and exclude-list app scope */
        data class OnAppScopeModeChanged(val isIncludeMode: Boolean) : UiEvent()

        /** Toggle whether conditions must ALL match or ANY match */
        data class OnConditionLogicChanged(val logic: ConditionCombinator) : UiEvent()

        /** Show the action type-picker dialog */
        data object OnAddActionClicked : UiEvent()

        /** An action type was chosen in the picker dialog */
        data class OnActionTypeSelected(val type: ActionType) : UiEvent()

        /** Dismiss the action type-picker dialog */
        data object OnDismissActionTypePicker : UiEvent()

        /** Toggle an action's enabled state */
        data class OnToggleActionClicked(val actionId: String, val enabled: Boolean) : UiEvent()

        /** Click on an action item to edit it */
        data class OnEditActionClicked(val actionId: String) : UiEvent()

        /** Remove an action from the list */
        data class OnRemoveActionClicked(val actionId: String) : UiEvent()

        /** Confirm removing an Extract-data action, clearing its fields */
        data object OnConfirmExtractDataRemoval : UiEvent()

        /** Dismiss the Extract-data removal confirmation */
        data object OnDismissExtractDataRemoval : UiEvent()

        /** Extract-data draft confirmed: commit these fields and ensure the SAVE_DATA action exists */
        data class OnExtractDataCommitted(val fields: ImmutableList<RuleField>) : UiEvent()
        data object OnDismissSheet : UiEvent()

        /** Condition saved from MatchingLogicBottomSheet (add or update) */
        data class OnConditionSaved(val condition: RuleCondition) : UiEvent()

        /** Action saved from a type-scoped action sheet (add or update) */
        data class OnActionSaved(val action: RuleAction) : UiEvent()

        /** Test the current draft rule against captured notification history */
        data object OnTestAgainstHistoryClicked : UiEvent()

        /** Dismiss the "test against history" results */
        data object OnDismissBacktestResults : UiEvent()

        /** Save the rule */
        data object OnSaveClicked : UiEvent()

        /**
         * Back intent (top-bar arrow, Cancel or system back): in the guided flow a later step returns
         * to the previous one; otherwise unsaved changes raise the discard dialog and a clean draft
         * closes the editor.
         */
        data object OnBackClicked : UiEvent()

        /** Discard the draft and close the editor */
        data object OnDiscardConfirmed : UiEvent()

        /** Keep editing (dismiss the discard dialog) */
        data object OnDiscardDismissed : UiEvent()

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
     * One-time effects. Success feedback for save/delete is not an effect: the editor closes right
     * after, so it goes through `AppMessenger` to be shown by the app-level host instead.
     */
    sealed class UiEffect {
        /** Show a transient error message (validation, backtest failure) */
        data class ShowError(val message: UiText) : UiEffect()
    }
}
