package dev.gaferneira.notificapp.core.data.repository

import androidx.room.withTransaction
import dev.gaferneira.notificapp.core.common.Failure
import dev.gaferneira.notificapp.core.common.toFailureResult
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao
import dev.gaferneira.notificapp.core.data.local.mapper.ExtractedFieldValueMapper
import dev.gaferneira.notificapp.core.data.local.mapper.RecentActivityMapper
import dev.gaferneira.notificapp.core.data.local.mapper.RuleExecutionMapper
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.notification.action.CurrentTimeProvider
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ExtractedDataUpdate
import dev.gaferneira.notificapp.domain.model.RecentActivity
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleStats
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Implementation of [RuleExecutionRepository].
 *
 * Follows Repository Pattern (ADR 005) with:
 * - Local data source (Room DAOs) for database operations
 * - Result<T> return type for explicit error handling (ADR 006)
 * - Injected coroutine dispatchers for testability (ADR 008)
 *
 * The three writes performed by [saveExecution] (rule execution insert, field value
 * inserts, notification counter update) are wrapped in a single Room transaction so
 * they succeed or fail atomically.
 */
internal class RuleExecutionRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val ruleExecutionDao: RuleExecutionDao,
    private val extractedFieldValueDao: ExtractedFieldValueDao,
    private val notificationDao: NotificationDao,
    private val timeProvider: CurrentTimeProvider,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : RuleExecutionRepository {

    override suspend fun saveExecution(
        execution: RuleExecution,
        fields: List<RuleField>,
    ): Result<Unit> = withContext(ioDispatcher) {
        try {
            database.withTransaction {
                val executionEntity = RuleExecutionMapper.toEntity(execution)
                ruleExecutionDao.insert(executionEntity)

                val fieldValues = ExtractedFieldValueMapper.fromExtractedData(
                    executionId = execution.id,
                    extractedData = execution.extractedData,
                    fields = fields,
                )
                if (fieldValues.isNotEmpty()) {
                    extractedFieldValueDao.insertAll(fieldValues)
                }

                notificationDao.incrementAppliedRulesCount(execution.notificationId)
            }
            Timber.d("Saved rule execution ${execution.id}")
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to save rule execution ${execution.id}")
            e.toFailureResult()
        }
    }

    override fun observeExecutionsForNotification(notificationId: String): Flow<List<RuleExecution>> = ruleExecutionDao.observeExecutionsForNotification(notificationId)
        .map { entities -> RuleExecutionMapper.toDomainList(entities) }
        .flowOn(ioDispatcher)

    override suspend fun lastThrottleDeliveryAt(
        actionId: String,
        packageName: String,
        sinceMs: Long,
    ): Result<Long?> = withContext(ioDispatcher) {
        try {
            val entities = ruleExecutionDao.getRecentExecutionsForPackageSince(packageName, sinceMs)
            val lastDelivery = RuleExecutionMapper.toDomainList(entities)
                .filter { it.actionOutcomes[actionId] == ActionOutcome.SUCCESS }
                .maxOfOrNull { it.createdAt }
            Result.success(lastDelivery)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to look up last throttle delivery for action $actionId, package $packageName")
            e.toFailureResult()
        }
    }

    override suspend fun updateExtractedData(updates: List<ExtractedDataUpdate>): Result<Unit> = withContext(ioDispatcher) {
        try {
            database.withTransaction {
                updates.forEach { update ->
                    ruleExecutionDao.updateExtractedData(
                        id = update.executionId,
                        extractedData = RuleExecutionMapper.encodeExtractedData(update.extractedData),
                    )
                    extractedFieldValueDao.deleteValuesForExecutionFields(
                        executionId = update.executionId,
                        fieldIds = update.fields.map { it.id },
                    )
                    val values = ExtractedFieldValueMapper.fromExtractedData(
                        executionId = update.executionId,
                        extractedData = update.extractedData,
                        fields = update.fields,
                    )
                    if (values.isNotEmpty()) {
                        extractedFieldValueDao.insertAll(values)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to update extracted data for ${updates.size} executions")
            e.toFailureResult()
        }
    }

    override fun observeExecutionCountSince(since: Long): Flow<Int> = ruleExecutionDao.observeExecutionCountSince(since)
        .flowOn(ioDispatcher)

    override fun observeRecentActivity(limit: Int): Flow<List<RecentActivity>> = ruleExecutionDao.observeRecentActivity(limit)
        .map { rows -> rows.map { RecentActivityMapper.toDomain(it) } }
        .flowOn(ioDispatcher)

    /**
     * "Now" is read once when collection starts, so the 7/30-day windows are fixed for the life of
     * the flow and only re-evaluated against new rows (Room re-runs the query on table changes).
     */
    override fun observeRuleStats(ruleId: String): Flow<RuleStats> {
        val now = timeProvider.nowEpochMillis()
        return ruleExecutionDao.observeRuleStats(
            ruleId = ruleId,
            since7Days = now - TimeUnit.DAYS.toMillis(DAYS_7),
            since30Days = now - TimeUnit.DAYS.toMillis(DAYS_30),
        )
            .map { row ->
                RuleStats(
                    totalMatches = row.total,
                    matchesLast7Days = row.last7Days,
                    matchesLast30Days = row.last30Days,
                    liveMatches = row.live,
                    testModeMatches = row.testMode,
                    lastTriggeredAt = row.lastTriggeredAt,
                )
            }
            .catch { e ->
                Timber.e(e, "Failed to observe stats for rule $ruleId")
                throw Failure.analyzeCause(e)
            }
            .flowOn(ioDispatcher)
    }

    private companion object {
        const val DAYS_7 = 7L
        const val DAYS_30 = 30L
    }
}
