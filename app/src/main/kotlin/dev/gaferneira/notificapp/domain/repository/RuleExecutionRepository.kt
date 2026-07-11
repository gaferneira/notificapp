package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField
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
     * Delete all executions recorded for a notification and reset its applied-rules
     * counter, so it can be re-evaluated from scratch.
     */
    suspend fun deleteExecutionsForNotification(notificationId: String): Result<Unit>

    /**
     * Look up the most recent time [actionId] was successfully delivered (outcome `SUCCESS`) for
     * a notification from [packageName], no earlier than [sinceMs] (epoch millis). Returns
     * `null` when there is no such delivery in range. Used by the throttle tracker's DB-lookback
     * fallback - callers should treat [Result.failure] as fail-open (no known prior delivery).
     */
    suspend fun lastThrottleDeliveryAt(actionId: String, packageName: String, sinceMs: Long): Result<Long?>
}
