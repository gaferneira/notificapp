package dev.gaferneira.notificapp.features.insights.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.CountBucket
import dev.gaferneira.notificapp.domain.model.FieldTotal
import dev.gaferneira.notificapp.domain.model.InsightsPeriod
import dev.gaferneira.notificapp.domain.model.InsightsSummary
import dev.gaferneira.notificapp.domain.model.OutcomeCounts
import dev.gaferneira.notificapp.domain.model.RuleActivity
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleRef
import dev.gaferneira.notificapp.domain.model.TrendPoint
import dev.gaferneira.notificapp.domain.model.WebhookDeliveryStatus
import dev.gaferneira.notificapp.domain.model.WebhookHealth
import dev.gaferneira.notificapp.features.insights.contract.InsightsUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate

private val sampleCounts = listOf(3, 5, 2, 8, 4, 0, 1)

private fun sampleSummary() = InsightsSummary(
    period = InsightsPeriod.DAYS_7,
    windowStart = 1_791_158_400_000L, // 2026-10-05, the first trend day
    capturedCount = 128,
    handledCount = sampleCounts.sum(),
    actionsRunCount = 42,
    actionsFailedCount = 3,
    valuesExtractedCount = 57,
    trend = sampleCounts.mapIndexed { i, count ->
        TrendPoint(LocalDate.of(2026, 10, 5).plusDays(i.toLong()).toString(), count)
    }.toImmutableList(),
    fieldTotals = persistentListOf(
        FieldTotal("r1", "ICA Purchase", "f1", "Amount", RuleField.FieldType.CURRENCY, 2340.0, 12),
        FieldTotal("r2", "Gym visits", "f2", "Minutes", RuleField.FieldType.NUMBER, 540.0, 9),
    ),
    topRules = persistentListOf(RuleActivity("r1", "ICA Purchase", 12), RuleActivity("r2", "Gym visits", 9)),
    quietRules = persistentListOf(RuleRef("r3", "Parcel delivered")),
    actionOutcomes = persistentMapOf(
        ActionType.SAVE_DATA to OutcomeCounts(success = 30, failed = 0, skipped = 2),
        ActionType.DISMISS_NOTIFICATION to OutcomeCounts(success = 9, failed = 3, skipped = 0),
    ),
    webhooks = persistentListOf(
        WebhookHealth("w1", "Budget sheet", WebhookDeliveryStatus.DELIVERED, System.currentTimeMillis() - 3_600_000L, 0),
        WebhookHealth("w2", "Home server", WebhookDeliveryStatus.UNREACHABLE, System.currentTimeMillis() - 86_400_000L, 2),
    ),
    topApps = persistentListOf(CountBucket("Swish", 40), CountBucket("ICA", 31)),
    hasAnyRules = true,
)

@Composable
private fun InsightsPreviewContent() {
    NotificappTheme(dynamicColor = false) {
        InsightsScreenContent(
            uiState = InsightsUiState(period = InsightsPeriod.DAYS_7, summary = sampleSummary(), isLoading = false),
            onEvent = {},
            navigateTo = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, heightDp = 1600)
@Composable
private fun InsightsPreview() = InsightsPreviewContent()

@Preview(showBackground = true, heightDp = 1600, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun InsightsPreviewDark() = InsightsPreviewContent()
