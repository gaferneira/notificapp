package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.ExtractedDataUpdate
import dev.gaferneira.notificapp.domain.model.RecentActivity
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleStats
import kotlinx.coroutines.flow.Flow

/**
 * Repository for persisting and querying rule execution results.
 *
 * A rule execution records when a rule matched a notification, together with
 * the fields extracted from it. This is the sole abstraction consumers should
 * use to reach "extracted data" — no DAO or entity should be referenced
 * outside `core/data` and `core/di` (see Roadmap TD-1).
 */
interface RuleExecutionRepository {

    /**
     * Persist one execution and its typed field values atomically.
     *
     * @param execution The rule execution to record
     * @param fields The rule's field definitions (used to type the extracted values)
     */
    suspend fun saveExecution(execution: RuleExecution, fields: List<RuleField>): Result<Unit>

    /**
     * Observe executions for one notification, most recent first.
     */
    fun observeExecutionsForNotification(notificationId: String): Flow<List<RuleExecution>>

    /**
     * Replace the extracted values of existing executions, all in one transaction (all or none).
     *
     * Only `extractedData` and the typed field values of the given fields change: executions are
     * never created or deleted, and outcomes, dry-run flag, `createdAt` and the notification's
     * applied-rules counter are untouched. No action is dispatched.
     */
    suspend fun updateExtractedData(updates: List<ExtractedDataUpdate>): Result<Unit>

    /**
     * Look up the most recent time [actionId] was successfully delivered (outcome `SUCCESS`) for
     * a notification from [packageName], no earlier than [sinceMs] (epoch millis). Returns
     * `null` when there is no such delivery in range. Used by the throttle tracker's DB-lookback
     * fallback - callers should treat [Result.failure] as fail-open (no known prior delivery).
     */
    suspend fun lastThrottleDeliveryAt(actionId: String, packageName: String, sinceMs: Long): Result<Long?>

    /**
     * Count executions recorded at or after [since] (epoch millis), excluding dry-run rules.
     */
    fun observeExecutionCountSince(since: Long): Flow<Int>

    /**
     * The most recent non-dry-run executions, joined with source notification + rule name,
     * bounded by [limit], most recent first.
     */
    fun observeRecentActivity(limit: Int): Flow<List<RecentActivity>>

    /**
     * Observe aggregate match statistics for one rule (totals, 7/30-day windows, live vs test
     * mode, last trigger). Computed in the database, never by loading executions. The flow
     * errors with a `Failure` if the query fails; with no executions it emits a zeroed [RuleStats].
     */
    fun observeRuleStats(ruleId: String): Flow<RuleStats>
}
