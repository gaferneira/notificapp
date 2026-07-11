package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.common.toFailureResult
import dev.gaferneira.notificapp.core.data.local.dao.SelectedAppDao
import dev.gaferneira.notificapp.core.data.local.entity.SelectedAppEntity
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementation of [SelectedAppRepository].
 *
 * Follows Repository Pattern (ADR 005) with:
 * - Local data source (Room DAO) for database operations
 * - Result<T> return type for explicit error handling (ADR 006)
 * - Injected coroutine dispatchers for testability (ADR 008)
 */
internal class SelectedAppRepositoryImpl @Inject constructor(
    private val dao: SelectedAppDao,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : SelectedAppRepository {

    override fun observeAllApps(): Flow<List<SelectedApp>> = dao.getAll()
        .map { entities -> entities.map { it.toModel() } }
        .flowOn(ioDispatcher)

    override fun observeEnabledApps(): Flow<List<SelectedApp>> = dao.getEnabledApps()
        .map { entities -> entities.map { it.toModel() } }
        .flowOn(ioDispatcher)

    override suspend fun getAllApps(): Result<List<SelectedApp>> = withContext(ioDispatcher) {
        try {
            val entities = dao.getAllSync()
            Result.success(entities.map { it.toModel() })
        } catch (e: Exception) {
            Timber.e(e, "Failed to get all apps")
            e.toFailureResult()
        }
    }

    override suspend fun getEnabledApps(): Result<List<SelectedApp>> = withContext(ioDispatcher) {
        try {
            val entities = dao.getEnabledAppsSync()
            Result.success(entities.map { it.toModel() })
        } catch (e: Exception) {
            Timber.e(e, "Failed to get enabled apps")
            e.toFailureResult()
        }
    }

    override suspend fun getApp(packageName: String): Result<SelectedApp?> = withContext(ioDispatcher) {
        try {
            val entity = dao.getByPackageName(packageName)
            Result.success(entity?.toModel())
        } catch (e: Exception) {
            Timber.e(e, "Failed to get app: $packageName")
            e.toFailureResult()
        }
    }

    override suspend fun isAppSelected(packageName: String): Result<Boolean> = withContext(ioDispatcher) {
        try {
            val isSelected = dao.isAppSelected(packageName)
            Result.success(isSelected)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check if app is selected: $packageName")
            e.toFailureResult()
        }
    }

    override suspend fun isAppEnabled(packageName: String): Result<Boolean> = withContext(ioDispatcher) {
        try {
            val isEnabled = dao.isAppEnabled(packageName)
            Result.success(isEnabled)
        } catch (e: Exception) {
            Timber.e(e, "Failed to check if app is enabled: $packageName")
            e.toFailureResult()
        }
    }

    override suspend fun addApp(app: SelectedApp): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.insert(app.toEntity())
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add app: ${app.packageName}")
            e.toFailureResult()
        }
    }

    override suspend fun addApps(apps: List<SelectedApp>): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.insertAll(apps.map { it.toEntity() })
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add ${apps.size} apps")
            e.toFailureResult()
        }
    }

    override suspend fun removeApp(packageName: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.deleteByPackageName(packageName)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove app: $packageName")
            e.toFailureResult()
        }
    }

    override suspend fun setAppEnabled(packageName: String, isEnabled: Boolean): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.setEnabled(packageName, isEnabled)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to set app enabled state: $packageName")
            e.toFailureResult()
        }
    }

    override suspend fun removeAllApps(): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.deleteAll()
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to remove all apps")
            e.toFailureResult()
        }
    }

    override suspend fun getSelectedCount(): Result<Int> = withContext(ioDispatcher) {
        try {
            val count = dao.getCount()
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get selected count")
            e.toFailureResult()
        }
    }

    override suspend fun getEnabledCount(): Result<Int> = withContext(ioDispatcher) {
        try {
            val count = dao.getEnabledCount()
            Result.success(count)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get enabled count")
            e.toFailureResult()
        }
    }
}

/**
 * Convert Entity to Domain Model.
 */
private fun SelectedAppEntity.toModel(): SelectedApp = SelectedApp(
    packageName = this.packageName,
    appName = this.appName,
    isEnabled = this.isEnabled,
    createdAt = this.createdAt,
)

/**
 * Convert Domain Model to Entity.
 */
private fun SelectedApp.toEntity(): SelectedAppEntity = SelectedAppEntity(
    packageName = this.packageName,
    appName = this.appName,
    isEnabled = this.isEnabled,
    createdAt = this.createdAt,
)
