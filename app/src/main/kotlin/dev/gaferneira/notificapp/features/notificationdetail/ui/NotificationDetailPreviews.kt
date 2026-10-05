package dev.gaferneira.notificapp.features.notificationdetail.ui

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.ExtractedDataUpdate
import dev.gaferneira.notificapp.domain.model.ExtractionPreview
import dev.gaferneira.notificapp.domain.model.FieldChange
import dev.gaferneira.notificapp.domain.model.FieldDiff
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField.FieldType
import dev.gaferneira.notificapp.domain.model.RuleMatchPreview
import dev.gaferneira.notificapp.domain.model.UnmatchedExecution
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ExecutionWithDetails
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ExtractedFieldDisplay
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.LoadStatus
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.RuleRef
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.TriggeredActionDisplay
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiState
import dev.gaferneira.notificapp.features.notificationdetail.ui.components.TestRulesPreviewContent

private val sampleNotification = Notification(
    id = "n1",
    packageName = "com.example.bank",
    appName = "Example Bank",
    title = "Card purchase",
    content = "You spent \$42.50 at Corner Cafe on card ending 1234. Available balance: \$1,203.10.",
    rawContent = "Card purchase You spent \$42.50 at Corner Cafe on card ending 1234.",
    timestamp = System.currentTimeMillis() - 3 * 60 * 60 * 1000L,
)

private fun sampleExecution(id: String, dryRun: Boolean = false) = RuleExecution(
    id = id,
    notificationId = "n1",
    ruleId = "r-$id",
    extractedData = mapOf("amount" to "42.50"),
    triggeredActions = listOf("a1", "a2"),
    wasDryRun = dryRun,
)

private fun sampleDetails(id: String, name: String?, dryRun: Boolean = false) = ExecutionWithDetails(
    execution = sampleExecution(id, dryRun),
    ruleId = "r-$id",
    ruleName = name,
    extractedFields = listOf(
        ExtractedFieldDisplay("amount", "Amount", FieldType.NUMBER, "42.50"),
        ExtractedFieldDisplay("when", "Purchase date", FieldType.DATE, "2026-10-05"),
        ExtractedFieldDisplay("gone", null, FieldType.STRING, "Corner Cafe"),
    ),
    triggeredActions = listOf(
        TriggeredActionDisplay("a1", ActionType.SAVE_DATA, if (dryRun) null else ActionOutcome.SUCCESS),
        TriggeredActionDisplay("a2", ActionType.CREATE_ALARM, if (dryRun) null else ActionOutcome.SUPPRESSED),
        TriggeredActionDisplay("a3", null, null),
    ),
)

private val loadedState = UiState(
    loadStatus = LoadStatus.LOADED,
    notification = sampleNotification,
    executions = listOf(sampleDetails("e1", "Bank purchases"), sampleDetails("e2", null)),
)

private val samplePreview = ExtractionPreview(
    matches = listOf(
        RuleMatchPreview(
            ruleId = "r-e1",
            ruleName = "Bank purchases",
            isDryRun = false,
            executionId = "e1",
            fieldDiffs = listOf(
                FieldDiff("amount", "Amount", FieldType.NUMBER, "42.50", "42.50", FieldChange.UNCHANGED),
                FieldDiff("merchant", "Merchant", FieldType.STRING, "Corner", "Corner Cafe", FieldChange.CHANGED),
                FieldDiff("card", "Card", FieldType.STRING, null, "1234", FieldChange.NEW),
                FieldDiff("old", "Category", FieldType.STRING, "Food", null, FieldChange.REMOVED),
            ),
            actionTypes = listOf(ActionType.SAVE_DATA, ActionType.DISMISS_NOTIFICATION),
            update = ExtractedDataUpdate("e1", emptyList(), emptyMap()),
        ),
        RuleMatchPreview(
            ruleId = "r-new",
            ruleName = "Large spends",
            isDryRun = true,
            executionId = null,
            fieldDiffs = emptyList(),
            actionTypes = listOf(ActionType.FLASH_ALERT),
            update = null,
        ),
    ),
    noLongerMatching = listOf(UnmatchedExecution("e9", "r-9", "Old rule"), UnmatchedExecution("e8", "r-8", null)),
)

@Composable
private fun PreviewContent(state: UiState, isAppLaunchable: Boolean = true) {
    NotificappTheme(dynamicColor = false) {
        NotificationDetailScreenContent(
            uiState = state,
            snackbarHostState = remember { SnackbarHostState() },
            isAppLaunchable = isAppLaunchable,
            onEvent = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun NotificationDetailPreview() = PreviewContent(loadedState)

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationDetailDarkPreview() = PreviewContent(loadedState)

@Preview(showBackground = true, fontScale = 1.6f)
@Composable
private fun NotificationDetailLargeFontPreview() = PreviewContent(loadedState)

@Preview(showBackground = true)
@Composable
private fun NotificationDetailRedactedPreview() = PreviewContent(
    loadedState.copy(
        notification = sampleNotification.copy(title = null, content = null, rawContent = ""),
        redactedByRule = RuleRef("r-e1", "Bank purchases"),
        executions = listOf(sampleDetails("e1", "Bank purchases").copy(wasRedactionSource = true)),
    ),
)

@Preview(showBackground = true)
@Composable
private fun NotificationDetailDryRunPreview() = PreviewContent(
    loadedState.copy(executions = listOf(sampleDetails("e1", "Bank purchases", dryRun = true))),
)

@Preview(showBackground = true)
@Composable
private fun NotificationDetailEmptyPreview() = PreviewContent(loadedState.copy(executions = emptyList()))

@Preview(showBackground = true)
@Composable
private fun NotificationDetailNotFoundPreview() = PreviewContent(UiState(loadStatus = LoadStatus.NOT_FOUND))

@Preview(showBackground = true)
@Composable
private fun NotificationDetailErrorPreview() = PreviewContent(UiState(loadStatus = LoadStatus.ERROR))

@Preview(showBackground = true)
@Composable
private fun NotificationDetailLoadingPreview() = PreviewContent(UiState())

@Preview(showBackground = true)
@Composable
private fun TestRulesPreviewSheetPreview() {
    NotificappTheme(dynamicColor = false) {
        TestRulesPreviewContent(
            preview = samplePreview,
            failure = null,
            canApply = true,
            isApplying = false,
            onApply = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 1.6f)
@Composable
private fun TestRulesPreviewSheetDarkLargeFontPreview() {
    NotificappTheme(dynamicColor = false) {
        TestRulesPreviewContent(
            preview = samplePreview,
            failure = null,
            canApply = true,
            isApplying = true,
            onApply = {},
            onRetry = {},
        )
    }
}
