package dev.gaferneira.notificapp.features.ruleeditor.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.domain.model.RuleTrigger
import dev.gaferneira.notificapp.domain.model.TriggerType
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleUiModel
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
            is UiEvent.OnAddDescriptionClicked -> showDescriptionField()
            is UiEvent.OnCategoryChange -> updateCategory(event.category)
            is UiEvent.OnAddCategoryClicked -> showCategoryField()
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
            is UiEvent.OnEditFieldClicked -> openFieldForEditing(event.fieldId)
            is UiEvent.OnRemoveFieldClicked -> removeField(event.fieldId)
            is UiEvent.OnFieldSaved -> onFieldSaved(event.field)
            is UiEvent.OnTriggerSaved -> onTriggerSaved(event.trigger)
            is UiEvent.OnActionSaved -> onActionSaved(event.action)
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
                    rule = RuleUiModel(
                        triggers = listOf(
                            RuleTrigger(
                                id = UUID.randomUUID().toString(),
                                type = TriggerType.APP,
                                targetApps = emptyList(),
                            ),
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
                                    targetApps = if (currentRule.id == null) listOf(notification.packageName) else currentRule.targetApps,
                                    triggers = if (currentRule.id == null) {
                                        listOf(
                                            RuleTrigger(
                                                id = UUID.randomUUID().toString(),
                                                type = TriggerType.APP,
                                                targetApps = listOf(
                                                    AppInfo(
                                                        packageName = notification.packageName,
                                                        name = notification.appName,
                                                    ),
                                                ),
                                            ),
                                        )
                                    } else {
                                        currentRule.triggers
                                    },
                                    isGlobalRule = false,
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

    private fun updateArea(area: String) {
        setState { copy(rule = rule.copy(area = area)) }
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
            copy(rule = rule.copy(triggers = rule.triggers.filter { it.id != triggerId }))
        }
    }

    private fun onTriggerSaved(trigger: RuleTrigger) {
        val editingId = uiState.value.editingTriggerId
        if (editingId != null) {
            // Update existing trigger
            setState {
                copy(
                    rule = rule.copy(triggers = rule.triggers.map { if (it.id == editingId) trigger else it }),
                    isMatchingLogicSheetVisible = false,
                    editingTriggerId = null,
                )
            }
        } else {
            // Add new trigger
            setState {
                copy(
                    rule = rule.copy(triggers = rule.triggers + trigger),
                    isMatchingLogicSheetVisible = false,
                )
            }
        }
    }

    private fun openTriggerForEditing(triggerId: String) {
        uiState.value.rule.triggers.find { it.id == triggerId } ?: return
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
            copy(rule = rule.copy(actions = rule.actions.filter { it.id != actionId }))
        }
    }

    private fun onActionSaved(action: RuleAction) {
        val editingId = uiState.value.editingActionId
        if (editingId != null) {
            // Update existing action
            setState {
                copy(
                    rule = rule.copy(actions = rule.actions.map { if (it.id == editingId) action else it }),
                    isActionSheetVisible = false,
                    editingActionId = null,
                )
            }
        } else {
            // Add new action
            setState {
                copy(
                    rule = rule.copy(actions = rule.actions + action),
                    isActionSheetVisible = false,
                )
            }
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
            RuleField(
                id = UUID.randomUUID().toString(),
                name = if (matches.size == 1) "Amount" else "Amount ${index + 1}",
                method = ExtractionMethod.LineExtraction(10),
            )
        }

        // Also add a Save to Data action if not already present
        val hasSaveAction = uiState.value.rule.actions.any { it.type == ActionType.SAVE_DATA }
        val newActions = if (!hasSaveAction) {
            uiState.value.rule.actions + RuleAction(
                id = UUID.randomUUID().toString(),
                type = ActionType.SAVE_DATA,
                isEnabled = true,
            )
        } else {
            uiState.value.rule.actions
        }

        setState {
            copy(
                rule = rule.copy(
                    extractionFields = newFields,
                    actions = newActions,
                ),
            )
        }
    }

    private fun toggleGlobalRule() {
        setState {
            copy(
                rule = rule.copy(
                    isGlobalRule = !rule.isGlobalRule,
                    targetApps = if (!rule.isGlobalRule) emptyList() else rule.targetApps,
                ),
            )
        }
    }

    private fun updateTargetApps(apps: List<String>) {
        setState { copy(rule = rule.copy(targetApps = apps)) }
    }

    private fun addField() {
        setState {
            copy(
                isFieldSheetVisible = true,
                editingFieldId = null, // Clear editing state for new field
            )
        }
    }

    private fun openFieldForEditing(fieldId: String) {
        setState {
            copy(
                isFieldSheetVisible = true,
                editingFieldId = fieldId,
            )
        }
    }

    private fun removeField(fieldId: String) {
        setState {
            copy(
                rule = rule.copy(extractionFields = rule.extractionFields.filter { it.id != fieldId }),
            )
        }
    }

    private fun onFieldSaved(field: RuleField) {
        val currentState = uiState.value
        val existingFieldId = currentState.editingFieldId

        setState {
            val updatedFields = if (existingFieldId != null) {
                // Update existing field
                rule.extractionFields.map {
                    if (it.id == existingFieldId) field else it
                }
            } else {
                // Add new field
                rule.extractionFields + field
            }
            copy(
                rule = rule.copy(extractionFields = updatedFields),
            )
        }
        dismissBottomSheet()
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

            val rule = Rule(
                id = ruleUiModel.id ?: UUID.randomUUID().toString(),
                name = ruleUiModel.name.trim(),
                description = ruleUiModel.description.takeIf { it.isNotBlank() },
                triggers = ruleUiModel.triggers,
                actions = ruleUiModel.actions,
                isActive = true,
                targetApps = if (ruleUiModel.isGlobalRule) null else ruleUiModel.targetApps,
                fields = ruleUiModel.extractionFields,
            )

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
                isFieldSheetVisible = false,
                isActionSheetVisible = false,
                isMatchingLogicSheetVisible = false,
                editingFieldId = null,
                editingTriggerId = null,
                editingActionId = null,
            )
        }
    }

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
