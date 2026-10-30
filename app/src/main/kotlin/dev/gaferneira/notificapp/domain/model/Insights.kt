package dev.gaferneira.notificapp.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap

/** Look-back window of the Insights dashboard, expressed in local calendar days (today included). */
enum class InsightsPeriod(val days: Int) {
    DAYS_7(7),
    DAYS_30(30),
}

/** Success / failed / skipped action counts for one [ActionType]. Suppressed outcomes count as skipped. */
@Immutable
data class OutcomeCounts(val success: Int = 0, val failed: Int = 0, val skipped: Int = 0)

/** What the Insights activity chart counts per day. */
enum class TrendMetric {
    /** Captured notifications. */
    NOTIFICATIONS,

    /** Notifications processed by at least one rule. */
    HANDLED,

    /** Non-dry-run rule executions. */
    RULE_RUNS,

    /** Failed action outcomes. */
    ACTIONS_FAILED,
}

/**
 * Action outcomes of one rule for one [ActionType] in the period. [ruleId] is null when the rule
 * was deleted; [ruleName] then falls back to the execution-time snapshot.
 */
@Immutable
data class ActionBreakdownRow(
    val type: ActionType,
    val ruleId: String?,
    val ruleName: String,
    val counts: OutcomeCounts,
)

/** Per-rule run count (non-dry-run executions) within the period. */
@Immutable
data class RuleActivity(val ruleId: String?, val ruleName: String, val runCount: Int)

/** Source app with its captured-notification count; [packageName] lets the UI open the app's notifications. */
@Immutable
data class AppActivity(val packageName: String, val appName: String, val count: Int)

/** Lightweight rule reference (id + display name). */
@Immutable
data class RuleRef(val ruleId: String, val ruleName: String)

/**
 * Sum and count of one numeric/currency extraction field in one [unit]. Sums are per (field, unit),
 * so currencies and units are never mixed.
 */
@Immutable
data class FieldTotal(
    val ruleId: String?,
    val ruleName: String,
    val fieldId: String?,
    val fieldName: String,
    val fieldType: RuleField.FieldType,
    val sum: Double,
    val count: Int,
    val unit: String? = null,
)

/** At-a-glance health of one webhook: last delivery status/time and queued failed deliveries. */
@Immutable
data class WebhookHealth(
    val webhookId: String,
    val name: String,
    val lastStatus: WebhookDeliveryStatus,
    val lastDeliveryAt: Long?,
    val pendingRetries: Int,
)

/**
 * Aggregated dashboard data for one [InsightsPeriod]. Dry runs are always excluded.
 *
 * @property period The period this snapshot was computed for; the UI renders labels and the chart from it, not from the selected toggle
 * @property windowStart Inclusive epoch-millis start of the period (first local midnight); every count uses it
 * @property handledCount Notifications processed by at least one rule (the Inbox "Processed" list) in the period
 * @property trends Daily counts per [TrendMetric] for every local day of the period, oldest first, zero-filled;
 *   each series sums to the matching count above (rule runs match no tile)
 * @property topRules At most 5 rules by match count within the period
 * @property quietRules Enabled rules with no non-dry-run execution in the last 30 days (independent of the period)
 * @property topApps At most 5 source apps by captured notifications within the period
 * @property hasAnyRules False when no rule exists at all (drives the global empty state)
 */
@Immutable
data class InsightsSummary(
    val period: InsightsPeriod,
    val windowStart: Long,
    val capturedCount: Int,
    val handledCount: Int,
    val actionsRunCount: Int,
    val actionsFailedCount: Int,
    val valuesExtractedCount: Int,
    val trends: ImmutableMap<TrendMetric, ImmutableList<TrendPoint>>,
    val fieldTotals: ImmutableList<FieldTotal>,
    val topRules: ImmutableList<RuleActivity>,
    val quietRules: ImmutableList<RuleRef>,
    val actionOutcomes: ImmutableMap<ActionType, OutcomeCounts>,
    val webhooks: ImmutableList<WebhookHealth>,
    val topApps: ImmutableList<AppActivity>,
    val hasAnyRules: Boolean,
)
