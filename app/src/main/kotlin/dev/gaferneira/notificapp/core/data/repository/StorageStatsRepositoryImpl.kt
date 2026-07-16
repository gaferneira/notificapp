package dev.gaferneira.notificapp.core.data.repository

import android.content.Context
import android.database.sqlite.SQLiteException
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.core.common.toFailureResult
import dev.gaferneira.notificapp.core.data.local.AppDatabase
import dev.gaferneira.notificapp.core.di.DATABASE_NAME
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.StorageStats
import dev.gaferneira.notificapp.domain.repository.StorageStatsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementation of [StorageStatsRepository].
 *
 * Follows Repository Pattern (ADR 005) with:
 * - Local data sources (Room DAOs) for row counts
 * - Result<T> return type for explicit error handling (ADR 006)
 * - Injected coroutine dispatchers for testability (ADR 008)
 */
internal class StorageStatsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AppDatabase,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : StorageStatsRepository {

    override suspend fun getStorageStats(): Result<StorageStats> = withContext(ioDispatcher) {
        try {
            val databaseSizeBytes = context.getDatabasePath(DATABASE_NAME).length()
            Result.success(
                StorageStats(
                    databaseSizeBytes = databaseSizeBytes,
                    notificationCount = database.notificationDao().getCount(),
                    ruleCount = database.ruleDao().getCount(),
                    ruleExecutionCount = database.ruleExecutionDao().getCount(),
                    extractedFieldValueCount = database.extractedFieldValueDao().getCount(),
                    selectedAppCount = database.selectedAppDao().getCount(),
                ),
            )
        } catch (e: SQLiteException) {
            Timber.e(e, "Failed to compute storage stats")
            e.toFailureResult()
        } catch (e: IllegalStateException) {
            // Room throws this if the database has been closed underneath us.
            Timber.e(e, "Failed to compute storage stats")
            e.toFailureResult()
        }
    }
}
