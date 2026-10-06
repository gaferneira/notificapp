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

/** Per-rule run count (non-dry-run executions) within the period. */
@Immutable
data class RuleActivity(val ruleId: String, val ruleName: String, val runCount: Int)

/** Lightweight rule reference (id + display name). */
@Immutable
data class RuleRef(val ruleId: String, val ruleName: String)

/**
 * Sum and count of one numeric/currency extraction field. Sums are per field, so currencies are
 * never mixed across fields.
 */
@Immutable
data class FieldTotal(
    val ruleId: String,
    val ruleName: String,
    val fieldId: String,
    val fieldName: String,
    val fieldType: RuleField.FieldType,
    val sum: Double,
    val count: Int,
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
 * @property trend Daily rule matches for every local day of the period, oldest first, zero-filled
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
    val trend: ImmutableList<TrendPoint>,
    val fieldTotals: ImmutableList<FieldTotal>,
    val topRules: ImmutableList<RuleActivity>,
    val quietRules: ImmutableList<RuleRef>,
    val actionOutcomes: ImmutableMap<ActionType, OutcomeCounts>,
    val webhooks: ImmutableList<WebhookHealth>,
    val topApps: ImmutableList<CountBucket>,
    val hasAnyRules: Boolean,
)
