package dev.gaferneira.notificapp.core.data.repository

import androidx.room.withTransaction
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.data.local.dao.ExtractedFieldValueDao
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.dao.RuleExecutionDao
import dev.gaferneira.notificapp.core.data.local.mapper.ExtractedFieldValueMapper
import dev.gaferneira.notificapp.core.data.local.mapper.RuleExecutionMapper
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
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
class RuleExecutionRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val ruleExecutionDao: RuleExecutionDao,
    private val extractedFieldValueDao: ExtractedFieldValueDao,
    private val notificationDao: NotificationDao,
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
            Result.failure(e)
        }
    }

    override fun observeExecutionsForNotification(notificationId: String): Flow<List<RuleExecution>> = ruleExecutionDao.observeExecutionsForNotification(notificationId)
        .map { entities -> RuleExecutionMapper.toDomainList(entities) }
        .flowOn(ioDispatcher)

    override suspend fun deleteExecutionsForNotification(notificationId: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            database.withTransaction {
                ruleExecutionDao.deleteExecutionsForNotification(notificationId)
                notificationDao.resetAppliedRulesCount(notificationId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to delete executions for notification: $notificationId")
            Result.failure(e)
        }
    }
}
