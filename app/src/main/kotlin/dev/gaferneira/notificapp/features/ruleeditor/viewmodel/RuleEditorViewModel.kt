package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.extraction.RuleEngine
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec
import dev.gaferneira.notificapp.core.rulesharing.RuleJsonCodec.withFreshIdentityForImport
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.messaging.AppMessenger
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.SNOOZE_THROTTLE_RESET_AT_KEY
import dev.gaferneira.notificapp.domain.model.SnoozeMode
import dev.gaferneira.notificapp.domain.model.getSnoozeMode
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.domain.repository.RuleTemplateRepository
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.EditorMode
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.InitArgs
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.LoadError
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.BacktestMatch
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorIssue
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorStep
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleDraftCodec
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleUiModel
import dev.gaferneira.notificapp.features.ruleeditor.domain.prefillConditionFrom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.plus

/**
 * ViewModel for the Rule Editor screen.
 *
 * Manages rule creation/editing with form handling, validation,
 * and extraction testing. The matching logic bottom sheet has its own ViewModel.
 *
 * Draft lifecycle:
 * - **Load once.** [UiEvent.Initialize] prefills the draft (rule, template, sample notification) and
 *   then snapshots it as [UiState.initialRule]; repeating it with the same [InitArgs] is a no-op, so
 *   a recomposition or configuration change can never wipe in-progress edits. A blocking failure
 *   surfaces as [UiState.loadError] and [UiEvent.OnRetryLoadClicked] repeats the load.
 * - **Dirty check.** [UiState.hasUnsavedChanges] compares the draft against that snapshot.
 * - **Presentation.** [UiState.mode] (guided create vs single page) is derived once from [InitArgs]
 *   and persisted with the draft. In guided mode [UiState.currentStep] walks WHEN, DO, REVIEW over
 *   the same single draft, so moving between steps never loses data.
 * - **Process death.** The draft, its snapshot, the mode and the current step are mirrored into
 *   [SavedStateHandle] (debounced) using the shareable rule wire format ([RuleDraftCodec]). On
 *   recreation the draft is restored and the nav-arg prefill is skipped. Transient UI state (open
 *   sheets, dialogs, backtest results) and the sample notification payload are not persisted; the
 *   sample notification is re-fetched by id.
 * - **Feedback.** Save/delete errors live in [UiState.error] (dismissible). Success messages go
 *   through [AppMessenger] because this screen is popped immediately afterwards, so a snackbar
 *   hosted by it would never be seen.
 */
@Suppress("LongParameterList") // Hilt ViewModel: repositories + messenger + SavedStateHandle + injected dispatcher, same precedent as ProcessNotificationUseCase
@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val notificationRepository: NotificationRepository,
    private val selectedAppRepository: SelectedAppRepository,
    private val ruleTemplateRepository: RuleTemplateRepository,
    private val ruleEngine: RuleEngine,
    private val navigationHandler: NavigationHandler,
    private val appMessenger: AppMessenger,
    private val savedStateHandle: SavedStateHandle,
    @Dispatcher(DispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    private var initArgs: InitArgs? = null
    private var loadJob: Job? = null
    private var restoredFromSavedState = false

    /** Draft persistence starts only once the prefill completed (or a draft was restored). */
    private var isDraftTrackable = false

    init {
        restoreFromSavedState()
        observeEnabledApps()
        persistDraftOnChange()
    }

    private fun restoreFromSavedState() {
        val draft = savedStateHandle.get<String>(KEY_DRAFT)?.let(RuleDraftCodec::decode) ?: return
        val initial = savedStateHandle.get<String>(KEY_INITIAL)?.let(RuleDraftCodec::decode) ?: return
        restoredFromSavedState = true
        isDraftTrackable = true
        val mode = savedStateHandle.get<String>(KEY_MODE)?.let { name -> EditorMode.entries.find { it.name == name } }
            ?: if (draft.id != null) EditorMode.SINGLE_PAGE else EditorMode.GUIDED
        val step = savedStateHandle.get<String>(KEY_STEP)?.let { name -> EditorStep.entries.find { it.name == name } }
            ?: EditorStep.WHEN
        setState {
            copy(
                rule = draft,
                initialRule = initial,
                mode = mode,
                currentStep = step,
                isFromTemplate = savedStateHandle.get<Boolean>(KEY_FROM_TEMPLATE) ?: false,
                isExistingRule = draft.id != null,
                showPrefillHint = savedStateHandle.get<Boolean>(KEY_PREFILL_HINT) ?: false,
                showCategory = draft.category.isNotBlank(),
                showDescription = draft.description.isNotBlank(),
            )
        }
    }

    @OptIn(FlowPreview::class)
    private fun persistDraftOnChange() {
        viewModelScope.launch {
            uiState
                .map { DraftSnapshot(it.rule, it.initialRule, it.currentStep, it.mode, it.isFromTemplate, it.showPrefillHint) }
                .distinctUntilChanged()
                .debounce(PERSIST_DEBOUNCE_MS)
                .collect { snapshot ->
                    if (!isDraftTrackable) return@collect
                    val (draftJson, initialJson) = withContext(defaultDispatcher) {
                        RuleDraftCodec.encode(snapshot.draft) to RuleDraftCodec.encode(snapshot.initial)
                    }
                    savedStateHandle[KEY_DRAFT] = draftJson
                    savedStateHandle[KEY_INITIAL] = initialJson
                    savedStateHandle[KEY_STEP] = snapshot.step.name
                    savedStateHandle[KEY_MODE] = snapshot.mode.name
                    savedStateHandle[KEY_FROM_TEMPLATE] = snapshot.fromTemplate
                    savedStateHandle[KEY_PREFILL_HINT] = snapshot.prefillHint
                }
        }
    }

    private fun observeEnabledApps() {
        viewModelScope.launch {
            selectedAppRepository.observeEnabledApps()
                .collect { apps ->
                    setState {
                        copy(
                            enabledApps = apps.map { AppInfo(it.packageName, it.appName) }.toPersistentList(),
                        )
                    }
                }
        }
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.Initialize -> initialize(event.args)
            UiEvent.OnRetryLoadClicked -> retryLoad()
            UiEvent.OnNextStepClicked -> navigateToNextStep()
            UiEvent.OnPreviousStepClicked -> navigateToPreviousStep()
            is UiEvent.OnStepSelected -> jumpToCompletedStep(event.step)
            is UiEvent.OnIssueClicked -> jumpToIssueStep(event.issue)
            UiEvent.OnPrefillHintDismissed -> setState { copy(showPrefillHint = false) }
            is UiEvent.OnNameChange -> updateName(event.name)
            is UiEvent.OnDescriptionChange -> updateDescription(event.description)
            is UiEvent.OnAddDescriptionClicked -> showDescriptionField()
            is UiEvent.OnCategoryChange -> updateCategory(event.category)
            is UiEvent.OnAddCategoryClicked -> showCategoryField()
            is UiEvent.OnDryRunToggle -> updateDryRun(event.enabled)
            is UiEvent.OnDeleteRawContentToggle -> updateDeleteRawContentAfterExtraction(event.enabled)
            is UiEvent.OnAddConditionClicked -> showMatchingLogicSheet()
            is UiEvent.OnRemoveConditionClicked -> removeCondition(event.conditionId)
            is UiEvent.OnConditionItemClicked -> openConditionForEditing(event.conditionId)
            is UiEvent.OnAppsClicked -> showAppSheet()
            is UiEvent.OnAppsSelected -> onAppsSelected(event.apps)
            is UiEvent.OnAppScopeModeChanged -> onAppScopeModeChanged(event.isIncludeMode)
            is UiEvent.OnConditionLogicChanged -> setState { copy(rule = rule.copy(conditionLogic = event.logic)) }
            is UiEvent.OnAddActionClicked -> showActionTypePicker()
            is UiEvent.OnActionTypeSelected -> onActionTypeSelected(event.type)
            is UiEvent.OnDismissActionTypePicker -> setState { copy(isActionTypePickerVisible = false) }
            is UiEvent.OnToggleActionClicked -> toggleAction(event.actionId, event.enabled)
            is UiEvent.OnEditActionClicked -> openActionForEditing(event.actionId)
            is UiEvent.OnRemoveActionClicked -> removeAction(event.actionId)
            is UiEvent.OnConfirmExtractDataRemoval -> confirmExtractDataRemoval()
            is UiEvent.OnDismissExtractDataRemoval -> setState { copy(pendingExtractDataRemovalId = null) }
            is UiEvent.OnExtractDataCommitted -> onExtractDataCommitted(event.fields)
            is UiEvent.OnConditionSaved -> onConditionSaved(event.condition)
            is UiEvent.OnActionSaved -> onActionSaved(event.action)
            UiEvent.OnTestAgainstHistoryClicked -> testAgainstHistory()
            UiEvent.OnDismissBacktestResults -> dismissBacktestResults()
            is UiEvent.OnSaveClicked -> saveRule()
            is UiEvent.OnBackClicked -> onBackClicked()
            UiEvent.OnDiscardConfirmed -> {
                setState { copy(showUnsavedChangesDialog = false) }
                leaveEditor()
            }
            UiEvent.OnDiscardDismissed -> setState { copy(showUnsavedChangesDialog = false) }
            is UiEvent.OnDismissError -> dismissError()
            UiEvent.OnDismissSheet -> dismissBottomSheet()
            UiEvent.OnDeleteClicked -> showDeleteConfirmation()
            UiEvent.OnDeleteConfirmed -> deleteRule()
            UiEvent.OnDeleteDismissed -> dismissDeleteConfirmation()
        }
    }

    /**
     * Back intent shared by the top-bar arrow, Cancel and the system back: in the guided flow a
     * later step returns to the previous one; otherwise unsaved changes ask for confirmation and a
     * clean draft closes the editor. Ignored while saving.
     */
    private fun onBackClicked() {
        val state = uiState.value
        when {
            state.isSaving -> Unit
            state.mode == EditorMode.GUIDED && state.currentStep.previous() != null -> navigateToPreviousStep()
            state.hasUnsavedChanges -> setState { copy(showUnsavedChangesDialog = true) }
            else -> leaveEditor()
        }
    }

    private fun leaveEditor() {
        viewModelScope.launch { navigationHandler.goBack() }
    }

    private fun initialize(args: InitArgs) {
        if (initArgs == args && uiState.value.loadError == null) return
        initArgs = args
        if (restoredFromSavedState) {
            // The draft came back from saved state: keep the user's edits, only re-fetch the
            // sample notification (it is not persisted) for the Extract-data test panel.
            args.notificationId?.let { id -> viewModelScope.launch { fetchSampleNotification(id)?.let { n -> setState { copy(sampleNotification = n) } } } }
            return
        }
        startLoad(args)
    }

    private fun retryLoad() {
        val args = initArgs ?: return
        startLoad(args)
    }

    private fun startLoad(args: InitArgs) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            // The presentation is decided here, once, from how the editor was opened.
            setState {
                copy(
                    isLoading = true,
                    loadError = null,
                    mode = args.editorMode,
                    currentStep = EditorStep.WHEN,
                    isFromTemplate = args.templateAssetFileName != null,
                    isExistingRule = args.ruleId != null,
                    showPrefillHint = false,
                )
            }

            val prefill = when {
                args.ruleId != null -> loadExistingRule(args.ruleId)
                args.templateAssetFileName != null -> loadTemplate(args.templateAssetFileName)
                else -> Prefill.Ready(RuleUiModel())
            }
            if (prefill is Prefill.Failed) {
                setState { copy(isLoading = false, loadError = prefill.error) }
                return@launch
            }

            var rule = (prefill as Prefill.Ready).rule
            val sample = args.notificationId?.let { fetchSampleNotification(it) }
            var prefilledCondition = false
            if (sample != null && rule.id == null) {
                // Scope to the source app and seed one content condition so the rule is not "too broad".
                val condition = prefillConditionFrom(sample)
                rule = rule.copy(
                    targetApps = persistentListOf(sample.app),
                    isIncludeMode = true,
                    triggers = listOfNotNull(condition).toPersistentList(),
                )
                prefilledCondition = condition != null
            }
            isDraftTrackable = true
            setState {
                copy(
                    rule = rule,
                    initialRule = rule,
                    sampleNotification = sample,
                    showPrefillHint = prefilledCondition,
                    isLoading = false,
                    showCategory = rule.category.isNotBlank(),
                    showDescription = rule.description.isNotBlank(),
                )
            }
        }
    }

    private suspend fun loadExistingRule(ruleId: String): Prefill = ruleRepository.getRule(ruleId).fold(
        onSuccess = { rule ->
            if (rule != null) {
                Prefill.Ready(RuleUiModel.fromDomain(rule))
            } else {
                Prefill.Failed(LoadError(UiText.StringResource(R.string.rule_editor_error_not_found), canRetry = false))
            }
        },
        onFailure = { e ->
            Timber.e(e, "Failed to load rule: $ruleId")
            Prefill.Failed(LoadError(UiText.StringResource(R.string.rule_editor_error_load), canRetry = true))
        },
    )

    /**
     * Populates the form from a template without persisting anything: `id = null` keeps the
     * editor in "create" mode, so the rule is only saved when the user taps Save.
     */
    private suspend fun loadTemplate(assetFileName: String): Prefill {
        val text = ruleTemplateRepository.getTemplateText(assetFileName).getOrElse { e ->
            Timber.w(e, "Rule template unavailable: $assetFileName")
            return Prefill.Failed(LoadError(UiText.StringResource(R.string.rule_editor_error_template_missing), canRetry = false))
        }
        return RuleJsonCodec.decode(text).fold(
            onSuccess = { result ->
                // Bundled templates are trusted: keep their own dry-run flag, which the import
                // pipeline would otherwise force on (that safety rule is for untrusted files).
                val template = result.rule.withFreshIdentityForImport().copy(isDryRun = result.rule.isDryRun)
                Prefill.Ready(RuleUiModel.fromDomain(template).copy(id = null))
            },
            onFailure = { e ->
                Timber.w(e, "Failed to decode rule template: $assetFileName")
                Prefill.Failed(LoadError(UiText.StringResource(R.string.rule_editor_error_template_invalid), canRetry = false))
            },
        )
    }

    /** The sample notification is a non-blocking extra: a failure is logged and the editor opens without it. */
    private suspend fun fetchSampleNotification(notificationId: String): Notification? = notificationRepository.getNotification(notificationId)
        .onFailure { e -> Timber.e(e, "Failed to load sample notification: $notificationId") }
        .getOrNull()

    private sealed interface Prefill {
        data class Ready(val rule: RuleUiModel) : Prefill
        data class Failed(val error: LoadError) : Prefill
    }

    private data class DraftSnapshot(
        val draft: RuleUiModel,
        val initial: RuleUiModel,
        val step: EditorStep,
        val mode: EditorMode,
        val fromTemplate: Boolean,
        val prefillHint: Boolean,
    )

    private fun updateName(name: String) {
        setState {
            copy(
                rule = rule.copy(name = name),
                validationErrors = validationErrors - "name",
            )
        }
    }

    private fun updateDescription(description: String) {
        setState { copy(rule = rule.copy(description = description)) }
    }

    private fun showDescriptionField() {
        setState { copy(showDescription = true) }
    }

    private fun showCategoryField() {
        setState { copy(showCategory = true) }
    }

    private fun updateCategory(category: String) {
        setState { copy(rule = rule.copy(category = category)) }
    }

    private fun updateDryRun(enabled: Boolean) {
        setState { copy(rule = rule.copy(isDryRun = enabled)) }
    }

    private fun updateDeleteRawContentAfterExtraction(enabled: Boolean) {
        setState { copy(rule = rule.copy(deleteRawContentAfterExtraction = enabled)) }
    }

    private fun navigateToNextStep() {
        val state = uiState.value
        // The gate (and the bottom bar's explanation of it) derives from the same validation.
        if (!state.canGoNext) return
        val next = state.currentStep.next() ?: return
        setState { copy(currentStep = next) }
    }

    private fun navigateToPreviousStep() {
        val state = uiState.value
        if (state.mode != EditorMode.GUIDED) return
        val previous = state.currentStep.previous() ?: return
        setState { copy(currentStep = previous) }
    }

    /** Only already completed steps are reachable from the indicator; moving forward goes through Next. */
    private fun jumpToCompletedStep(step: EditorStep) {
        val state = uiState.value
        if (state.mode != EditorMode.GUIDED || step.ordinal >= state.currentStep.ordinal) return
        setState { copy(currentStep = step) }
    }

    /** Guided flow only: bring the user to the step an offending issue belongs to. */
    private fun jumpToIssueStep(issue: EditorIssue) {
        val state = uiState.value
        if (state.mode != EditorMode.GUIDED || state.isSaving) return
        setState { copy(currentStep = issue.step) }
    }

    private fun showMatchingLogicSheet() {
        setState {
            copy(
                isMatchingLogicSheetVisible = true,
                editingConditionId = null,
            )
        }
    }

    private fun removeCondition(conditionId: String) {
        setState {
            copy(rule = rule.copy(triggers = rule.triggers.filter { it.id != conditionId }.toPersistentList()))
        }
    }

    private fun onConditionSaved(condition: RuleCondition) {
        val editingId = uiState.value.editingConditionId
        if (editingId != null) {
            // Update existing condition
            setState {
                copy(
                    rule = rule.copy(triggers = rule.triggers.map { if (it.id == editingId) condition else it }.toPersistentList()),
                    isMatchingLogicSheetVisible = false,
                    editingConditionId = null,
                )
            }
        } else {
            // Add new condition
            setState {
                copy(
                    rule = rule.copy(triggers = (rule.triggers + condition).toPersistentList()),
                    isMatchingLogicSheetVisible = false,
                )
            }
        }
    }

    private fun openConditionForEditing(conditionId: String) {
        val condition = uiState.value.rule.triggers.find { it.id == conditionId } ?: return
        // A Group is read-only in this editor - it has no leaf-editing UI, so it must not open the
        // MatchingLogicBottomSheet. It still survives load/save round trips untouched (see
        // RuleUiModel.fromDomain/toEntity, which copy `triggers` generically) and can be removed
        // like any other condition via OnRemoveConditionClicked.
        if (condition is RuleCondition.Group) return
        setState {
            copy(
                editingConditionId = conditionId,
                isMatchingLogicSheetVisible = true,
            )
        }
    }

    private fun showAppSheet() {
        setState { copy(isAppSheetVisible = true) }
    }

    private fun onAppsSelected(apps: ImmutableList<AppInfo>) {
        setState {
            copy(
                rule = rule.copy(targetApps = apps.toPersistentList()),
                isAppSheetVisible = false,
            )
        }
    }

    private fun onAppScopeModeChanged(isIncludeMode: Boolean) {
        // Mode is only meaningful when at least one app is selected; otherwise keep include.
        if (uiState.value.rule.targetApps.isEmpty()) return
        setState { copy(rule = rule.copy(isIncludeMode = isIncludeMode)) }
    }

    private fun showActionTypePicker() {
        setState { copy(isActionTypePickerVisible = true) }
    }

    private fun onActionTypeSelected(type: ActionType) {
        setState { copy(isActionTypePickerVisible = false) }
        when (type) {
            // Dismiss has no configuration, so there is no sheet - it is added directly.
            ActionType.DISMISS_NOTIFICATION -> setState {
                copy(rule = rule.copy(actions = (rule.actions + RuleAction(id = UUID.randomUUID().toString(), type = type)).toPersistentList()))
            }
            // Snooze / alarm / flash each open their own type-scoped configuration sheet.
            else -> setState {
                copy(
                    pendingActionType = type,
                    editingActionId = null,
                    isActionSheetVisible = true,
                )
            }
        }
    }

    private fun openActionForEditing(actionId: String) {
        val action = uiState.value.rule.actions.find { it.id == actionId } ?: return
        when (action.type) {
            // Dismiss has no configuration to edit.
            ActionType.DISMISS_NOTIFICATION -> Unit
            else -> setState {
                copy(
                    editingActionId = actionId,
                    isActionSheetVisible = true,
                )
            }
        }
    }

    private fun toggleAction(actionId: String, enabled: Boolean) {
        setState {
            copy(
                rule = rule.copy(
                    actions = rule.actions.map { action -> action.toggled(actionId, enabled) }.toPersistentList(),
                ),
            )
        }
    }

    /**
     * Toggle [action]'s enabled flag if its id matches [actionId]. A disable->re-enable
     * transition on a [SnoozeMode.THROTTLE] action stamps a fresh reset watermark (D5), so the
     * next match always delivers instead of resuming a stale in-flight window.
     */
    private fun RuleAction.toggled(actionId: String, enabled: Boolean): RuleAction {
        if (id != actionId) return this
        val toggled = copy(isEnabled = enabled)
        val isReEnablingThrottle = enabled && !isEnabled && getSnoozeMode() == SnoozeMode.THROTTLE
        return if (isReEnablingThrottle) {
            toggled.copy(config = toggled.config + (SNOOZE_THROTTLE_RESET_AT_KEY to System.currentTimeMillis().toString()))
        } else {
            toggled
        }
    }

    private fun removeAction(actionId: String) {
        val action = uiState.value.rule.actions.find { it.id == actionId } ?: return
        if (action.type == ActionType.SAVE_DATA && uiState.value.rule.fields.isNotEmpty()) {
            // Removing Extract-data clears its fields - confirm before the destructive step.
            setState { copy(pendingExtractDataRemovalId = actionId) }
        } else {
            performRemoveAction(actionId, clearFields = action.type == ActionType.SAVE_DATA)
        }
    }

    private fun confirmExtractDataRemoval() {
        val actionId = uiState.value.pendingExtractDataRemovalId ?: return
        performRemoveAction(actionId, clearFields = true)
    }

    private fun performRemoveAction(actionId: String, clearFields: Boolean) {
        setState {
            copy(
                rule = rule.copy(
                    actions = rule.actions.filter { it.id != actionId }.toPersistentList(),
                    fields = if (clearFields) persistentListOf() else rule.fields,
                ),
                pendingExtractDataRemovalId = null,
            )
        }
    }

    private fun onActionSaved(action: RuleAction) {
        val editingId = uiState.value.editingActionId
        if (editingId != null) {
            // Update existing action
            setState {
                copy(
                    rule = rule.copy(actions = rule.actions.map { if (it.id == editingId) action else it }.toPersistentList()),
                    isActionSheetVisible = false,
                    editingActionId = null,
                    pendingActionType = null,
                )
            }
        } else {
            // Add new action
            setState {
                copy(
                    rule = rule.copy(actions = (rule.actions + action).toPersistentList()),
                    isActionSheetVisible = false,
                    pendingActionType = null,
                )
            }
        }
    }

    /**
     * Commit the Extract-data draft from [ExtractDataViewModel]. The `SAVE_DATA` action and its
     * fields reach the rule only here (commit-on-confirm): the action is added if absent
     * (one-action-per-type), and the rule's fields are replaced with the confirmed draft.
     */
    private fun onExtractDataCommitted(fields: ImmutableList<RuleField>) {
        setState {
            val actions = if (rule.actions.any { it.type == ActionType.SAVE_DATA }) {
                rule.actions
            } else {
                (rule.actions + RuleAction(id = UUID.randomUUID().toString(), type = ActionType.SAVE_DATA)).toPersistentList()
            }
            copy(
                rule = rule.copy(fields = fields.toPersistentList(), actions = actions),
                isActionSheetVisible = false,
                editingActionId = null,
                pendingActionType = null,
            )
        }
    }

    /**
     * Test the current draft rule (as configured in the editor, not yet saved) against
     * previously captured notification history. Purely a preview: no [RuleEngine] match is
     * persisted, and no actions run.
     */
    private fun testAgainstHistory() {
        val draftRule = uiState.value.rule.toEntity()
        val targetPackages = draftRule.targetApps
            ?.map { it.packageName }
            ?.takeIf { it.isNotEmpty() }

        viewModelScope.launch {
            setState { copy(isBacktesting = true) }

            notificationRepository.getNotificationsForBacktest(
                targetPackages = targetPackages,
                isIncludeMode = draftRule.isIncludeMode,
                limit = BACKTEST_NOTIFICATION_LIMIT,
            )
                .onSuccess { candidates ->
                    // Evaluating up to BACKTEST_NOTIFICATION_LIMIT notifications runs regex/JSON
                    // extraction per candidate - CPU work that doesn't belong on Main.
                    val results = withContext(defaultDispatcher) {
                        candidates.mapNotNull { notification ->
                            // Each candidate is evaluated against the LocalDateTime it was actually
                            // captured at (not wall-clock "now") - see design D6a - so a draft
                            // day-of-week/time-range condition reflects whether it would have
                            // matched when the notification arrived, not uniformly across the set.
                            val capturedAt = Instant.ofEpochMilli(notification.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime()
                            ruleEngine.evaluate(notification, listOf(draftRule), capturedAt).firstOrNull()?.let { match ->
                                BacktestMatch(notification = notification, extractedData = match.extractedData)
                            }
                        }
                    }

                    setState {
                        copy(
                            isBacktesting = false,
                            backtestResults = results,
                            backtestTestedCount = candidates.size,
                        )
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to test rule against history")
                    setState { copy(isBacktesting = false) }
                    sendEffect(UiEffect.ShowError(UiText.StringResource(R.string.rule_editor_error_backtest)))
                }
        }
    }

    private fun dismissBacktestResults() {
        setState { copy(backtestResults = null) }
    }

    private fun saveRule() {
        val currentState = uiState.value
        if (currentState.isSaving || currentState.isLoading) return
        val ruleUiModel = currentState.rule

        val issues = currentState.blockingIssues
        if (issues.isNotEmpty()) {
            if (EditorIssue.NAME_REQUIRED in issues) {
                setState { copy(validationErrors = mapOf("name" to UiText.StringResource(R.string.rule_editor_error_name_required))) }
                sendEffect(UiEffect.ShowError(UiText.StringResource(R.string.rule_editor_error_enter_name)))
            } else {
                sendEffect(UiEffect.ShowError(UiText.StringResource(issues.first().message)))
            }
            return
        }

        // Flag synchronously so a rapid second tap is ignored before the coroutine starts.
        setState { copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val rule = ruleUiModel.toEntity()

            val result = if (ruleUiModel.id != null) {
                ruleRepository.updateRule(rule)
            } else {
                ruleRepository.saveRule(rule)
            }

            result
                .onSuccess {
                    // Saved: the draft is now the baseline, so nothing is left to discard.
                    setState { copy(isSaving = false, initialRule = ruleUiModel) }
                    appMessenger.post(UiText.StringResource(R.string.rule_editor_saved))
                    navigationHandler.goBack()
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to save rule")
                    setState { copy(isSaving = false, error = UiText.StringResource(R.string.rule_editor_error_save)) }
                }
        }
    }

    private fun dismissError() {
        setState { copy(error = null) }
    }

    private fun dismissBottomSheet() {
        setState {
            copy(
                isActionSheetVisible = false,
                isMatchingLogicSheetVisible = false,
                isAppSheetVisible = false,
                editingConditionId = null,
                editingActionId = null,
                pendingActionType = null,
            )
        }
    }

    private fun showDeleteConfirmation() {
        setState { copy(showDeleteConfirmation = true) }
    }

    private fun dismissDeleteConfirmation() {
        setState { copy(showDeleteConfirmation = false) }
    }

    private fun deleteRule() {
        val ruleId = uiState.value.rule.id ?: return
        if (uiState.value.isSaving) return

        setState { copy(isSaving = true, error = null, showDeleteConfirmation = false) }
        viewModelScope.launch {
            ruleRepository.deleteRule(ruleId)
                .onSuccess {
                    Timber.d("Rule deleted: $ruleId")
                    setState { copy(isSaving = false, initialRule = rule) }
                    appMessenger.post(UiText.StringResource(R.string.rule_editor_deleted))
                    navigationHandler.goBack()
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to delete rule: $ruleId")
                    setState { copy(isSaving = false, error = UiText.StringResource(R.string.rule_editor_error_delete)) }
                }
        }
    }

    private companion object {
        /** Caps "Test against history" to the most recent notifications so it can't OOM or freeze. */
        const val BACKTEST_NOTIFICATION_LIMIT = 500

        const val PERSIST_DEBOUNCE_MS = 300L
        const val KEY_DRAFT = "rule_editor_draft"
        const val KEY_INITIAL = "rule_editor_initial"
        const val KEY_STEP = "rule_editor_wizard_step"
        const val KEY_MODE = "rule_editor_mode"
        const val KEY_FROM_TEMPLATE = "rule_editor_from_template"
        const val KEY_PREFILL_HINT = "rule_editor_prefill_hint"
    }
}
