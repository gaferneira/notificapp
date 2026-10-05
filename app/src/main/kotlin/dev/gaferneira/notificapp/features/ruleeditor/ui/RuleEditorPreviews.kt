package dev.gaferneira.notificapp.features.ruleeditor.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.EditorMode
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.LoadError
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorStep
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleUiModel
import kotlinx.collections.immutable.persistentListOf

private const val DEVICE = "id:pixel_5"

private val previewRule = RuleUiModel(
    id = "rule-1",
    name = "Bank purchase alerts",
    description = "Saves the merchant and amount of each purchase",
    targetApps = persistentListOf(AppInfo("com.bank.app", "Bank")),
    triggers = persistentListOf(
        RuleCondition.ContentMatchCondition(
            id = "1",
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "purchase",
        ),
    ),
    fields = persistentListOf(
        RuleField(id = "1", name = "Merchant", method = ExtractionMethod.LineExtraction(10)),
        RuleField(id = "2", name = "Amount", method = ExtractionMethod.RegexPattern("\\d+(\\.\\d+)?")),
    ),
    actions = persistentListOf(
        RuleAction(id = "1", type = ActionType.SAVE_DATA, isEnabled = true),
        RuleAction(id = "2", type = ActionType.DISMISS_NOTIFICATION, isEnabled = true),
    ),
)

private val guidedState = UiState(mode = EditorMode.GUIDED, rule = previewRule.copy(id = null))
private val singlePageState = UiState(mode = EditorMode.SINGLE_PAGE, isExistingRule = true, rule = previewRule, showDescription = true)
private val templateState = UiState(
    mode = EditorMode.SINGLE_PAGE,
    isFromTemplate = true,
    rule = previewRule.copy(id = null, targetApps = persistentListOf()),
)

@Composable
private fun Host(state: UiState) {
    NotificappTheme {
        RuleEditorScreenContent(uiState = state, onEvent = {})
    }
}

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun GuidedWhenPreview() = Host(guidedState)

@Preview(showBackground = true, device = DEVICE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GuidedWhenDarkPreview() = Host(guidedState)

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun GuidedDoPreview() = Host(guidedState.copy(currentStep = EditorStep.DO))

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun GuidedReviewPreview() = Host(guidedState.copy(currentStep = EditorStep.REVIEW))

@Preview(showBackground = true, device = DEVICE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GuidedReviewDarkPreview() = Host(guidedState.copy(currentStep = EditorStep.REVIEW))

@Preview(showBackground = true, device = DEVICE, fontScale = 1.8f)
@Composable
private fun GuidedReviewLargeFontPreview() = Host(guidedState.copy(currentStep = EditorStep.REVIEW))

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun GuidedReviewErrorPreview() = Host(
    guidedState.copy(
        currentStep = EditorStep.REVIEW,
        rule = guidedState.rule.copy(name = ""),
        validationErrors = mapOf("name" to UiText.StringResource(R.string.rule_editor_error_name_required)),
    ),
)

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun SinglePageEditPreview() = Host(singlePageState)

@Preview(showBackground = true, device = DEVICE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SinglePageEditDarkPreview() = Host(singlePageState)

@Preview(showBackground = true, device = DEVICE, fontScale = 1.8f)
@Composable
private fun SinglePageEditLargeFontPreview() = Host(singlePageState)

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun SinglePageTemplatePreview() = Host(templateState)

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun SinglePageNeedsAttentionPreview() = Host(singlePageState.copy(rule = previewRule.copy(name = "")))

private val emptyRule = RuleUiModel()
private val noConditionsRule = previewRule.copy(triggers = persistentListOf())

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun GuidedWhenTooBroadPreview() = Host(UiState(mode = EditorMode.GUIDED, rule = emptyRule))

@Preview(showBackground = true, device = DEVICE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GuidedWhenTooBroadDarkPreview() = Host(UiState(mode = EditorMode.GUIDED, rule = emptyRule))

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun GuidedWhenWarningPreview() = Host(guidedState.copy(rule = noConditionsRule.copy(id = null)))

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun GuidedDoNoActionPreview() = Host(
    guidedState.copy(currentStep = EditorStep.DO, rule = guidedState.rule.copy(actions = persistentListOf(), fields = persistentListOf())),
)

@Preview(showBackground = true, device = DEVICE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GuidedReviewEarlierIssuesDarkPreview() = Host(
    guidedState.copy(currentStep = EditorStep.REVIEW, rule = emptyRule),
)

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun GuidedReviewEarlierIssuesPreview() = Host(
    guidedState.copy(currentStep = EditorStep.REVIEW, rule = emptyRule),
)

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun SinglePageWarningPreview() = Host(singlePageState.copy(rule = noConditionsRule))

@Preview(showBackground = true, device = DEVICE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SinglePageWarningDarkPreview() = Host(singlePageState.copy(rule = noConditionsRule))

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun SinglePageAllIssuesPreview() = Host(singlePageState.copy(rule = emptyRule))

@Preview(showBackground = true, device = DEVICE, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SinglePageAllIssuesDarkPreview() = Host(singlePageState.copy(rule = emptyRule))

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun SinglePagePrefillHintPreview() = Host(singlePageState.copy(rule = previewRule.copy(id = null), showPrefillHint = true))

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun LoadingPreview() = Host(UiState(isLoading = true))

@Preview(showBackground = true, device = DEVICE)
@Composable
private fun LoadErrorPreview() = Host(
    UiState(loadError = LoadError(UiText.StringResource(R.string.rule_editor_error_load), canRetry = true)),
)

private val longTextRule = previewRule.copy(
    id = null,
    name = "A rule with a very long name that has to wrap across several lines at large font sizes",
    triggers = persistentListOf(
        RuleCondition.ContentMatchCondition(
            id = "1",
            condition = MatchingCondition.TEXT_CONTENT,
            operator = MatchingOperator.CONTAINS,
            value = "your monthly statement is ready to be reviewed in the banking application",
        ),
    ),
)

@Preview(showBackground = true, device = DEVICE, fontScale = 2f)
@Composable
private fun GuidedWhenLongTextLargestFontPreview() = Host(guidedState.copy(rule = longTextRule))

@Preview(showBackground = true, device = DEVICE, fontScale = 2f)
@Composable
private fun GuidedWhenBlockedReasonLargestFontPreview() = Host(
    UiState(mode = EditorMode.GUIDED, rule = emptyRule),
)

@Preview(showBackground = true, device = DEVICE, fontScale = 2f)
@Composable
private fun GuidedDoLargestFontPreview() = Host(guidedState.copy(currentStep = EditorStep.DO))

@Preview(showBackground = true, device = DEVICE, fontScale = 2f)
@Composable
private fun GuidedReviewLongTextLargestFontPreview() = Host(
    guidedState.copy(currentStep = EditorStep.REVIEW, rule = longTextRule),
)

@Preview(showBackground = true, device = DEVICE, fontScale = 2f, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SinglePageAllIssuesLargestFontDarkPreview() = Host(singlePageState.copy(rule = emptyRule))
