package dev.gaferneira.notificapp.testutil.fakes

import dev.gaferneira.notificapp.domain.model.ExtractedDataUpdate
import dev.gaferneira.notificapp.domain.model.ExtractedFieldValue
import dev.gaferneira.notificapp.domain.model.RecentActivity
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleStats
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.util.concurrent.TimeUnit

/**
 * Deterministic in-memory [RuleExecutionRepository] fake for VM tests, backed by a
 * [MutableStateFlow] keyed by notification id.
 */
class FakeRuleExecutionRepository(
    initial: Map<String, List<RuleExecution>> = emptyMap(),
) : RuleExecutionRepository {

    private val executionsByNotification = MutableStateFlow(initial)

    /** Opt-in failure injection: set before a call to make [updateExtractedData] fail. */
    var updateError: Throwable? = null

    /** Batches passed to [updateExtractedData], in call order. */
    val appliedUpdates: MutableList<List<ExtractedDataUpdate>> = mutableListOf()

    /** Opt-in failure injection: when set, [observeRuleStats] fails with this error. */
    var statsError: Throwable? = null

    /** Epoch millis used as "now" for the 7/30-day windows in [observeRuleStats]. */
    var now: Long = 0L

    fun currentExecutions(notificationId: String): List<RuleExecution> = executionsByNotification.value[notificationId].orEmpty()

    override suspend fun saveExecution(
        execution: RuleExecution,
        extractedData: Map<String, String>,
        fields: List<RuleField>,
    ): Result<Unit> {
        val stored = execution.copy(extractedValues = valuesFor(execution.id, extractedData, fields))
        executionsByNotification.update { map ->
            val existing = map[execution.notificationId].orEmpty()
            map + (execution.notificationId to (existing + stored))
        }
        return Result.success(Unit)
    }

    override fun observeExecutionsForNotification(notificationId: String): Flow<List<RuleExecution>> = executionsByNotification.map { map -> map[notificationId].orEmpty() }

    /**
     * Mirrors the real transactional contract: all updates apply or none, and only the values of the
     * update's fields change (values of removed fields are kept) - executions are never added or removed, and outcomes/createdAt are kept.
     */
    override suspend fun updateExtractedData(updates: List<ExtractedDataUpdate>): Result<Unit> {
        updateError?.let { return Result.failure(it) }
        appliedUpdates += updates
        val byId = updates.associateBy { it.executionId }
        executionsByNotification.update { map ->
            map.mapValues { (_, list) ->
                list.map { e ->
                    byId[e.id]?.let { update ->
                        val fieldIds = update.fields.map { it.id }.toSet()
                        e.copy(
                            extractedValues = e.extractedValues.filter { it.ruleFieldId !in fieldIds } +
                                valuesFor(e.id, update.extractedData, update.fields),
                        )
                    } ?: e
                }
            }
        }
        return Result.success(Unit)
    }

    private fun valuesFor(executionId: String, data: Map<String, String>, fields: List<RuleField>) = data.mapNotNull { (fieldId, text) ->
        fields.find { it.id == fieldId }?.let { field ->
            ExtractedFieldValue(
                id = "$executionId-$fieldId",
                ruleExecutionId = executionId,
                ruleFieldId = field.id,
                fieldName = field.name,
                fieldType = field.fieldType,
                valueText = text,
                valueNumber = null,
                valueDate = null,
            )
        }
    }

    /** Test helper: replace the stored executions of a notification (simulates a Room emission). */
    fun setExecutions(notificationId: String, executions: List<RuleExecution>) {
        executionsByNotification.update { it + (notificationId to executions) }
    }

    override suspend fun lastThrottleDeliveryAt(actionId: String, packageName: String, sinceMs: Long): Result<Long?> = Result.success(null)

    override fun observeExecutionCountSince(since: Long): Flow<Int> = executionsByNotification.map { map ->
        map.values.flatten().count { it.createdAt >= since && !it.wasDryRun }
    }

    override fun observeRecentActivity(limit: Int): Flow<List<RecentActivity>> = executionsByNotification.map { map ->
        map.values.flatten()
            .filter { !it.wasDryRun }
            .sortedByDescending { it.createdAt }
            .take(limit)
            .map { execution ->
                RecentActivity(
                    executionId = execution.id,
                    ruleId = execution.ruleId,
                    ruleName = "",
                    notificationId = execution.notificationId,
                    notificationTitle = null,
                    notificationContent = null,
                    packageName = "",
                    appName = "",
                    executedAt = execution.createdAt,
                )
            }
    }

    override fun observeRuleStats(ruleId: String): Flow<RuleStats> {
        statsError?.let { error -> return flow { throw error } }
        val since7 = now - TimeUnit.DAYS.toMillis(7)
        val since30 = now - TimeUnit.DAYS.toMillis(30)
        return executionsByNotification.map { map ->
            val rows = map.values.flatten().filter { it.ruleId == ruleId }
            RuleStats(
                totalMatches = rows.size,
                matchesLast7Days = rows.count { it.createdAt >= since7 },
                matchesLast30Days = rows.count { it.createdAt >= since30 },
                liveMatches = rows.count { !it.wasDryRun },
                testModeMatches = rows.count { it.wasDryRun },
                lastTriggeredAt = rows.maxOfOrNull { it.createdAt },
            )
        }
    }
}
