package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.extraction.RuleEngine
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.SNOOZE_THROTTLE_RESET_AT_KEY
import dev.gaferneira.notificapp.domain.model.SnoozeMode
import dev.gaferneira.notificapp.domain.model.getSnoozeMode
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.BacktestMatch
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleUiModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.plus

/**
 * ViewModel for the Rule Editor screen.
 *
 * Manages rule creation/editing with form handling, validation,
 * and extraction testing. The matching logic bottom sheet has its own ViewModel.
 */
@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val notificationRepository: NotificationRepository,
    private val selectedAppRepository: SelectedAppRepository,
    private val ruleEngine: RuleEngine,
    private val navigationHandler: NavigationHandler,
    @Dispatcher(DispatcherType.Default) private val defaultDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    init {
        observeEnabledApps()
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
            is UiEvent.LoadRule -> loadRule(event.ruleId)
            is UiEvent.LoadSampleNotification -> loadSampleNotification(event.notificationId)
            is UiEvent.OnContinueClicked -> navigateToStep(2)
            is UiEvent.OnBackToLogicClicked -> navigateToStep(1)
            is UiEvent.OnNameChange -> updateName(event.name)
            is UiEvent.OnDescriptionChange -> updateDescription(event.description)
            is UiEvent.OnAddDescriptionClicked -> showDescriptionField()
            is UiEvent.OnCategoryChange -> updateCategory(event.category)
            is UiEvent.OnAddCategoryClicked -> showCategoryField()
            is UiEvent.OnDryRunToggle -> updateDryRun(event.enabled)
            is UiEvent.OnAddConditionClicked -> showMatchingLogicSheet()
            is UiEvent.OnRemoveConditionClicked -> removeCondition(event.conditionId)
            is UiEvent.OnConditionItemClicked -> openConditionForEditing(event.conditionId)
            is UiEvent.OnAppsClicked -> showAppSheet()
            is UiEvent.OnAppsSelected -> onAppsSelected(event.apps)
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
            is UiEvent.OnDismissError -> dismissError()
            UiEvent.OnDismissSheet -> dismissBottomSheet()
            UiEvent.OnDeleteClicked -> showDeleteConfirmation()
            UiEvent.OnDeleteConfirmed -> deleteRule()
            UiEvent.OnDeleteDismissed -> dismissDeleteConfirmation()
        }
    }

    private fun onBackClicked() {
        viewModelScope.launch {
            navigationHandler.goBack()
        }
    }

    private fun loadRule(ruleId: String?) {
        if (ruleId == null) {
            return
        }
        viewModelScope.launch {
            setState { copy(isLoading = true) }

            ruleRepository.getRule(ruleId)
                .onSuccess { rule ->
                    if (rule != null) {
                        val uiModel = RuleUiModel.fromDomain(rule)
                        setState {
                            copy(
                                rule = uiModel,
                                isLoading = false,
                                showCategory = uiModel.category.isNotBlank(),
                                showDescription = uiModel.description.isNotBlank(),
                            )
                        }
                    } else {
                        setState {
                            copy(
                                isLoading = false,
                                error = "Rule not found",
                            )
                        }
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to load rule: $ruleId")
                    setState {
                        copy(
                            isLoading = false,
                            error = "Failed to load rule: ${e.message}",
                        )
                    }
                }
        }
    }

    private fun loadSampleNotification(notificationId: String) {
        viewModelScope.launch {
            notificationRepository.getNotification(notificationId)
                .onSuccess { notification ->
                    if (notification != null) {
                        setState {
                            val currentRule = rule
                            copy(
                                sampleNotification = notification,
                                rule = currentRule.copy(
                                    targetApps = if (currentRule.id == null) persistentListOf(notification.app) else currentRule.targetApps,
                                    triggers = if (currentRule.id == null) {
                                        persistentListOf()
                                    } else {
                                        currentRule.triggers
                                    },
                                ),
                            )
                        }
                    }
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to load sample notification: $notificationId")
                }
        }
    }

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

    private fun navigateToStep(step: Int) {
        setState { copy(currentStep = step) }
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
        uiState.value.rule.triggers.find { it.id == conditionId } ?: return
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

            notificationRepository.getNotificationsForBacktest(targetPackages, BACKTEST_NOTIFICATION_LIMIT)
                .onSuccess { candidates ->
                    // Evaluating up to BACKTEST_NOTIFICATION_LIMIT notifications runs regex/JSON
                    // extraction per candidate - CPU work that doesn't belong on Main.
                    val results = withContext(defaultDispatcher) {
                        candidates.mapNotNull { notification ->
                            ruleEngine.evaluate(notification, listOf(draftRule)).firstOrNull()?.let { match ->
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
                    sendEffect(UiEffect.ShowError("Failed to test against history"))
                }
        }
    }

    private fun dismissBacktestResults() {
        setState { copy(backtestResults = null) }
    }

    private fun saveRule() {
        val currentState = uiState.value
        val ruleUiModel = currentState.rule

        // Validate - only name is required in the new design
        val errors = mutableMapOf<String, String>()
        if (ruleUiModel.name.isBlank()) {
            errors["name"] = "Rule name is required"
        }

        if (errors.isNotEmpty()) {
            setState { copy(validationErrors = errors) }
            sendEffect(UiEffect.ShowError("Please enter a rule name"))
            return
        }

        viewModelScope.launch {
            setState { copy(isLoading = true) }

            val rule = ruleUiModel.toEntity()

            val result = if (ruleUiModel.id != null) {
                ruleRepository.updateRule(rule)
            } else {
                ruleRepository.saveRule(rule)
            }

            result
                .onSuccess {
                    setState { copy(isLoading = false) }
                    sendEffect(UiEffect.ShowSuccess("Rule saved successfully"))
                    navigationHandler.goBack()
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to save rule")
                    setState {
                        copy(
                            isLoading = false,
                            error = "Failed to save rule: ${e.message}",
                        )
                    }
                    sendEffect(UiEffect.ShowError("Failed to save rule"))
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

        viewModelScope.launch {
            setState { copy(isLoading = true, showDeleteConfirmation = false) }

            ruleRepository.deleteRule(ruleId)
                .onSuccess {
                    Timber.d("Rule deleted: $ruleId")
                    setState { copy(isLoading = false) }
                    sendEffect(UiEffect.ShowSuccess("Rule deleted successfully"))
                    navigationHandler.goBack()
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to delete rule: $ruleId")
                    setState {
                        copy(
                            isLoading = false,
                            error = "Failed to delete rule: ${e.message}",
                        )
                    }
                    sendEffect(UiEffect.ShowError("Failed to delete rule"))
                }
        }
    }

    private companion object {
        /** Caps "Test against history" to the most recent notifications so it can't OOM or freeze. */
        const val BACKTEST_NOTIFICATION_LIMIT = 500
    }
}
