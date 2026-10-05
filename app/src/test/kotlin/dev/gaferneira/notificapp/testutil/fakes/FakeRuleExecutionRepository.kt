package dev.gaferneira.notificapp.testutil.fakes

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

    /** Opt-in failure injection: set before a call to make [deleteExecutionsForNotification] fail. */
    var deleteError: Throwable? = null

    /** Opt-in failure injection: when set, [observeRuleStats] fails with this error. */
    var statsError: Throwable? = null

    /** Epoch millis used as "now" for the 7/30-day windows in [observeRuleStats]. */
    var now: Long = 0L

    fun currentExecutions(notificationId: String): List<RuleExecution> = executionsByNotification.value[notificationId].orEmpty()

    override suspend fun saveExecution(execution: RuleExecution, fields: List<RuleField>): Result<Unit> {
        executionsByNotification.update { map ->
            val existing = map[execution.notificationId].orEmpty()
            map + (execution.notificationId to (existing + execution))
        }
        return Result.success(Unit)
    }

    override fun observeExecutionsForNotification(notificationId: String): Flow<List<RuleExecution>> = executionsByNotification.map { map -> map[notificationId].orEmpty() }

    override suspend fun deleteExecutionsForNotification(notificationId: String): Result<Unit> {
        deleteError?.let { return Result.failure(it) }
        executionsByNotification.update { map -> map + (notificationId to emptyList()) }
        return Result.success(Unit)
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
