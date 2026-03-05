package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.entity.NotificationEntity
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementation of [NotificationRepository].
 *
 * Follows Repository Pattern (ADR 005) with:
 * - Local data source (Room DAO) for database operations
 * - Result<T> return type for explicit error handling (ADR 006)
 * - Injected coroutine dispatchers for testability (ADR 008)
 */
class NotificationRepositoryImpl @Inject constructor(
    private val dao: NotificationDao,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : NotificationRepository {

    override fun observeAllNotifications(): Flow<List<Notification>> = dao.getAll()
        .map { entities -> entities.map { it.toModel() } }
        .flowOn(ioDispatcher)

    override fun observeNotificationsByApps(packageNames: List<String>): Flow<List<Notification>> = dao.getByPackageNames(packageNames)
        .map { entities -> entities.map { it.toModel() } }
        .flowOn(ioDispatcher)

    override suspend fun getAllNotifications(): Result<List<Notification>> = withContext(ioDispatcher) {
        try {
            val entities = dao.getAllSync()
            Result.success(entities.map { it.toModel() })
        } catch (e: Exception) {
            Timber.e(e, "Failed to get all notifications")
            Result.failure(e)
        }
    }

    override suspend fun getNotification(id: String): Result<Notification?> = withContext(ioDispatcher) {
        try {
            val entity = dao.getById(id)
            Result.success(entity?.toModel())
        } catch (e: Exception) {
            Timber.e(e, "Failed to get notification: $id")
            Result.failure(e)
        }
    }

    override suspend fun getUnprocessedNotifications(): Result<List<Notification>> = withContext(ioDispatcher) {
        try {
            val entities = dao.getUnprocessed()
            Result.success(entities.map { it.toModel() })
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unprocessed notifications")
            Result.failure(e)
        }
    }

    override suspend fun searchNotifications(query: String): Result<List<Notification>> = withContext(ioDispatcher) {
        try {
            val entities = dao.search(query)
            Result.success(entities.map { it.toModel() })
        } catch (e: Exception) {
            Timber.e(e, "Failed to search notifications: $query")
            Result.failure(e)
        }
    }

    override suspend fun saveNotification(notification: Notification): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.insert(notification.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save notification: ${notification.id}")
            Result.failure(e)
        }
    }

    override suspend fun markAsProcessed(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.markAsProcessed(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to mark notification as processed: $id")
            Result.failure(e)
        }
    }

    override suspend fun deleteNotification(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete notification: $id")
            Result.failure(e)
        }
    }

    override suspend fun deleteOlderThan(timestamp: Long): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.deleteOlderThan(timestamp)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete old notifications")
            Result.failure(e)
        }
    }

    override suspend fun deleteByApp(packageName: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.deleteByPackageName(packageName)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete notifications for app: $packageName")
            Result.failure(e)
        }
    }

    override suspend fun getNotificationCount(): Result<Int> = withContext(ioDispatcher) {
        try {
            val count = dao.getCount()
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get notification count")
            Result.failure(e)
        }
    }

    override suspend fun getUnprocessedCount(): Result<Int> = withContext(ioDispatcher) {
        try {
            val count = dao.getUnprocessedCount()
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get unprocessed count")
            Result.failure(e)
        }
    }

    override suspend fun getRecentNotifications(packageName: String, lookbackMs: Long): Result<List<Notification>> = withContext(ioDispatcher) {
        try {
            val since = System.currentTimeMillis() - lookbackMs
            val entities = dao.getRecentByPackageName(packageName, since)
            Result.success(entities.map { it.toModel() })
        } catch (e: Exception) {
            Timber.e(e, "Failed to get recent notifications for: $packageName")
            Result.failure(e)
        }
    }
}

/**
 * Convert Entity to Domain Model.
 */
private fun NotificationEntity.toModel(): Notification = Notification(
    id = this.id,
    packageName = this.packageName,
    appName = this.appName,
    title = this.title,
    content = this.content,
    rawContent = this.rawContent,
    timestamp = this.timestamp,
    isProcessed = this.isProcessed,
    appliedRulesCount = this.appliedRulesCount,
)

/**
 * Convert Domain Model to Entity.
 */
private fun Notification.toEntity(): NotificationEntity = NotificationEntity(
    id = this.id,
    packageName = this.packageName,
    appName = this.appName,
    title = this.title,
    content = this.content,
    rawContent = this.rawContent,
    timestamp = this.timestamp,
    isProcessed = this.isProcessed,
    appliedRulesCount = this.appliedRulesCount,
)
