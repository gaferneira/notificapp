package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.domain.model.ExtractionField
import dev.gaferneira.notificapp.domain.model.ExtractionMethod
import dev.gaferneira.notificapp.domain.model.ExtractionRule
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.ruleeditor.contract.MatchingLogicContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.contract.TriggerUiModel
import dev.gaferneira.notificapp.features.ruleeditor.domain.ActionType
import dev.gaferneira.notificapp.features.ruleeditor.domain.ActionUiModel
import dev.gaferneira.notificapp.features.ruleeditor.domain.ExtractionFieldUiModel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

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
    private val navigationHandler: NavigationHandler,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.LoadRule -> loadRule(event.ruleId)
            is UiEvent.LoadSampleNotification -> loadSampleNotification(event.notificationId)
            is UiEvent.OnContinueClicked -> navigateToStep(2)
            is UiEvent.OnBackToLogicClicked -> navigateToStep(1)
            is UiEvent.OnNameChange -> updateName(event.name)
            is UiEvent.OnDescriptionChange -> updateDescription(event.description)
            is UiEvent.OnCategoryChange -> updateCategory(event.category)
            is UiEvent.OnAreaChange -> updateArea(event.area)
            is UiEvent.OnGlobalRuleToggle -> toggleGlobalRule()
            is UiEvent.OnTargetAppsChange -> updateTargetApps(event.apps)
            is UiEvent.OnAddTriggerClicked -> showMatchingLogicSheet()
            is UiEvent.OnRemoveTriggerClicked -> removeTrigger(event.triggerId)
            is UiEvent.OnTriggerItemClicked -> openTriggerForEditing(event.triggerId)
            is UiEvent.OnAddActionClicked -> showActionSheet()
            is UiEvent.OnEditActionClicked -> openActionForEditing(event.actionId)
            is UiEvent.OnRemoveActionClicked -> removeAction(event.actionId)
            is UiEvent.OnAutoGenerateClicked -> autoGenerateExtraction()
            is UiEvent.OnAddFieldClicked -> addField()
            is UiEvent.OnRemoveFieldClicked -> removeField(event.fieldId)
            is UiEvent.OnFieldAdded -> onFieldAdded(event.field)
            is UiEvent.OnTriggerAdded -> onTriggerAdded(event.trigger)
            is UiEvent.OnTriggerUpdated -> onTriggerUpdated(event.triggerId, event.trigger)
            is UiEvent.OnActionAdded -> onActionAdded(event.action)
            is UiEvent.OnActionUpdated -> onActionUpdated(event.actionId, event.action)
            is UiEvent.OnTestExtractionClicked -> testExtraction()
            is UiEvent.OnSaveClicked -> saveRule()
            is UiEvent.OnBackClicked -> onBackClicked()
            is UiEvent.OnDismissError -> dismissError()
            UiEvent.OnDismissSheet -> dismissBottomSheet()
        }
    }

    private fun onBackClicked() {
        viewModelScope.launch {
            navigationHandler.goBack()
        }
    }

    private fun loadRule(ruleId: String?) {
        if (ruleId == null) {
            setState {
                copy(
                    triggers = listOf(
                        TriggerUiModel(
                            id = UUID.randomUUID().toString(),
                            type = MatchingLogicContract.TriggerType.APP,
                            selectedApps = emptyList(),
                        ),
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            setState { copy(isLoading = true) }

            ruleRepository.getRule(ruleId)
                .onSuccess { rule ->
                    if (rule != null) {
                        setState {
                            copy(
                                ruleId = rule.id,
                                name = rule.name,
                                description = rule.description ?: "",
                                isGlobalRule = rule.targetApps == null,
                                targetApps = rule.targetApps ?: emptyList(),
                                extractionFields = rule.extractionFields.map { it.toUiModel() },
                                isLoading = false,
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
                            copy(
                                sampleNotification = notification,
                                targetApps = if (ruleId == null) listOf(notification.packageName) else targetApps,
                                triggers = if (ruleId == null) {
                                    listOf(
                                        TriggerUiModel(
                                            id = UUID.randomUUID().toString(),
                                            type = MatchingLogicContract.TriggerType.APP,
                                            selectedApps = listOf(
                                                MatchingLogicContract.AppInfo(
                                                    packageName = notification.packageName,
                                                    name = notification.appName,
                                                ),
                                            ),
                                        ),
                                    )
                                } else {
                                    triggers
                                },
                                isGlobalRule = false,
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
                name = name,
                validationErrors = validationErrors - "name",
            )
        }
    }

    private fun updateDescription(description: String) {
        setState { copy(description = description) }
    }

    private fun updateCategory(category: String) {
        setState { copy(category = category) }
    }

    private fun updateArea(area: String) {
        setState { copy(area = area) }
    }

    private fun navigateToStep(step: Int) {
        setState { copy(currentStep = step) }
    }

    private fun showMatchingLogicSheet() {
        setState {
            copy(
                isMatchingLogicSheetVisible = true,
                editingTriggerId = null,
            )
        }
    }

    private fun removeTrigger(triggerId: String) {
        setState {
            copy(triggers = triggers.filter { it.id != triggerId })
        }
    }

    private fun onTriggerAdded(trigger: TriggerUiModel) {
        setState {
            copy(
                triggers = triggers + trigger,
                isMatchingLogicSheetVisible = false,
            )
        }
    }

    private fun onTriggerUpdated(triggerId: String, trigger: TriggerUiModel) {
        setState {
            copy(
                triggers = triggers.map { if (it.id == triggerId) trigger else it },
                isMatchingLogicSheetVisible = false,
                editingTriggerId = null,
            )
        }
    }

    private fun openTriggerForEditing(triggerId: String) {
        uiState.value.triggers.find { it.id == triggerId } ?: return
        setState {
            copy(
                editingTriggerId = triggerId,
                isMatchingLogicSheetVisible = true,
            )
        }
    }

    private fun showActionSheet() {
        setState { copy(isActionSheetVisible = true) }
    }

    private fun hideActionSheet() {
        setState { copy(isActionSheetVisible = false, editingActionId = null) }
    }

    private fun openActionForEditing(actionId: String) {
        setState {
            copy(
                editingActionId = actionId,
                isActionSheetVisible = true,
            )
        }
    }

    private fun removeAction(actionId: String) {
        setState {
            copy(actions = actions.filter { it.id != actionId })
        }
    }

    private fun onActionAdded(action: ActionUiModel) {
        setState {
            copy(
                actions = actions + action,
                isActionSheetVisible = false,
            )
        }
    }

    private fun onActionUpdated(actionId: String, action: ActionUiModel) {
        setState {
            copy(
                actions = actions.map { if (it.id == actionId) action else it },
                isActionSheetVisible = false,
                editingActionId = null,
            )
        }
    }

    private fun autoGenerateExtraction() {
        val sampleText = uiState.value.sampleNotification?.let { notification ->
            notification.content ?: notification.title ?: notification.rawContent
        } ?: ""

        if (sampleText.isEmpty()) {
            sendEffect(UiEffect.ShowError("No notification text available to analyze"))
            return
        }

        // Find number patterns in the text
        val numberPattern = Regex("""\d+[.,]?\d*""")
        val matches = numberPattern.findAll(sampleText).toList()

        if (matches.isEmpty()) {
            sendEffect(UiEffect.ShowError("No numbers found in the notification"))
            return
        }

        val newFields = matches.mapIndexed { index, matchResult ->
            ExtractionFieldUiModel(
                id = UUID.randomUUID().toString(),
                name = if (matches.size == 1) "Amount" else "Amount ${index + 1}",
                methodType = "smart_amount",
                methodSummary = "Detected: ${matchResult.value}",
            )
        }

        // Also add a Save to Data action if not already present
        val hasSaveAction = uiState.value.actions.any { it.type == ActionType.SAVE_DATA }
        val newActions = if (!hasSaveAction) {
            uiState.value.actions + ActionUiModel(
                id = UUID.randomUUID().toString(),
                type = ActionType.SAVE_DATA,
                isEnabled = true,
            )
        } else {
            uiState.value.actions
        }

        setState {
            copy(
                extractionFields = newFields,
                actions = newActions,
            )
        }

        // Auto-test the new fields
        testExtraction()
    }

    private fun toggleGlobalRule() {
        setState {
            copy(
                isGlobalRule = !isGlobalRule,
                targetApps = if (!isGlobalRule) emptyList() else targetApps,
            )
        }
    }

    private fun updateTargetApps(apps: List<String>) {
        setState { copy(targetApps = apps) }
    }

    private fun addField() {
        setState {
            copy(
                isFieldSheetVisible = true,
            )
        }
    }

    private fun removeField(fieldId: String) {
        setState {
            copy(
                extractionFields = extractionFields.filter { it.id != fieldId },
                testResults = testResults - fieldId,
            )
        }
    }

    private fun onFieldAdded(field: ExtractionField) {
        setState {
            copy(
                extractionFields = extractionFields + field.toUiModel(),
            )
        }
        // Auto-test the new field
        testExtraction()
    }

    private fun testExtraction() {
        val sample = uiState.value.sampleNotification
        if (sample == null) {
            sendEffect(UiEffect.ShowError("No sample notification available"))
            return
        }

        val sampleText = sample.content ?: sample.title ?: sample.rawContent
        val results = mutableMapOf<String, RuleEditorContract.TestResult>()

        uiState.value.extractionFields.forEach { fieldUiModel ->
            val result = testFieldExtraction(sampleText, fieldUiModel)
            results[fieldUiModel.id] = result
        }

        setState { copy(testResults = results) }
    }

    private fun testFieldExtraction(
        @Suppress("UNUSED_PARAMETER") text: String,
        @Suppress("UNUSED_PARAMETER") fieldUiModel: ExtractionFieldUiModel,
    ): RuleEditorContract.TestResult = RuleEditorContract.TestResult.Failure("Test requires field configuration")

    private fun saveRule() {
        val currentState = uiState.value

        // Validate - only name is required in the new design
        val errors = mutableMapOf<String, String>()
        if (currentState.name.isBlank()) {
            errors["name"] = "Rule name is required"
        }

        if (errors.isNotEmpty()) {
            setState { copy(validationErrors = errors) }
            sendEffect(UiEffect.ShowError("Please enter a rule name"))
            return
        }

        viewModelScope.launch {
            setState { copy(isLoading = true) }

            val rule = ExtractionRule(
                id = currentState.ruleId ?: UUID.randomUUID().toString(),
                name = currentState.name.trim(),
                description = currentState.description.takeIf { it.isNotBlank() },
                pattern = currentState.triggers.firstOrNull { it.type == MatchingLogicContract.TriggerType.CONDITION }?.value?.takeIf { it.isNotBlank() } ?: ".*",
                isActive = true,
                targetApps = if (currentState.isGlobalRule) null else currentState.targetApps,
                extractionFields = currentState.extractionFields.map { it.toDomainModel() },
            )

            val result = if (currentState.ruleId != null) {
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
                isFieldSheetVisible = false,
                isActionSheetVisible = false,
                isMatchingLogicSheetVisible = false,
                editingFieldId = null,
                editingTriggerId = null,
                editingActionId = null,
            )
        }
    }

    private fun ExtractionField.toUiModel() = ExtractionFieldUiModel(
        id = UUID.randomUUID().toString(),
        name = name,
        methodType = method.type,
        methodSummary = getMethodSummary(method),
    )

    private fun ExtractionFieldUiModel.toDomainModel(): ExtractionField = ExtractionField(
        name = name,
        description = null,
        method = ExtractionMethod.RegexPattern("(.*)", 1),
        isRequired = false,
    )

    private fun getMethodSummary(method: ExtractionMethod): String = when (method) {
        is ExtractionMethod.FixedPosition -> "Chars ${method.startIndex}-${method.endIndex}"
        is ExtractionMethod.TextBetweenAnchors -> "Between '${method.startAnchor}' and '${method.endAnchor}'"
        is ExtractionMethod.RegexPattern -> "Regex pattern"
        is ExtractionMethod.TextAfterKeyword -> "After '${method.keyword}'"
        is ExtractionMethod.TextBeforeKeyword -> "Before '${method.keyword}'"
        is ExtractionMethod.LineExtraction -> "Line ${method.lineNumber}"
        is ExtractionMethod.SplitByDelimiter -> "Split by '${method.delimiter}'"
        is ExtractionMethod.JsonPath -> "JSON path: ${method.path}"
        is ExtractionMethod.SmartAmountDetection -> "Smart amount"
        is ExtractionMethod.SmartDateDetection -> "Smart date"
    }
}
