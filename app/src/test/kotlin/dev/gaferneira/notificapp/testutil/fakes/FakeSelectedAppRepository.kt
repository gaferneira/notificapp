package dev.gaferneira.notificapp.testutil.fakes

import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * Deterministic in-memory [SelectedAppRepository] fake for VM tests, backed by a [MutableStateFlow].
 */
class FakeSelectedAppRepository(initial: List<SelectedApp> = emptyList()) : SelectedAppRepository {

    private val apps = MutableStateFlow(initial)

    fun currentApps(): List<SelectedApp> = apps.value

    fun setApps(newApps: List<SelectedApp>) {
        apps.value = newApps
    }

    override fun observeAllApps(): Flow<List<SelectedApp>> = apps.asStateFlow()

    override fun observeEnabledApps(): Flow<List<SelectedApp>> = apps.map { list -> list.filter { it.isEnabled } }

    override suspend fun getAllApps(): Result<List<SelectedApp>> = Result.success(apps.value)

    override suspend fun getEnabledApps(): Result<List<SelectedApp>> = Result.success(apps.value.filter { it.isEnabled })

    override suspend fun getApp(packageName: String): Result<SelectedApp?> = Result.success(apps.value.find { it.packageName == packageName })

    override suspend fun isAppSelected(packageName: String): Result<Boolean> = Result.success(apps.value.any { it.packageName == packageName })

    override suspend fun isAppEnabled(packageName: String): Result<Boolean> = Result.success(apps.value.any { it.packageName == packageName && it.isEnabled })

    override suspend fun addApp(app: SelectedApp): Result<Unit> {
        apps.update { list -> list.filterNot { it.packageName == app.packageName } + app }
        return Result.success(Unit)
    }

    override suspend fun addApps(apps: List<SelectedApp>): Result<Unit> {
        this.apps.update { list ->
            val newPackages = apps.map { it.packageName }.toSet()
            list.filterNot { it.packageName in newPackages } + apps
        }
        return Result.success(Unit)
    }

    override suspend fun removeApp(packageName: String): Result<Unit> {
        apps.update { list -> list.filterNot { it.packageName == packageName } }
        return Result.success(Unit)
    }

    override suspend fun removeApps(packageNames: List<String>): Result<Unit> {
        val toRemove = packageNames.toSet()
        apps.update { list -> list.filterNot { it.packageName in toRemove } }
        return Result.success(Unit)
    }

    override suspend fun setAppEnabled(packageName: String, isEnabled: Boolean): Result<Unit> {
        apps.update { list -> list.map { if (it.packageName == packageName) it.copy(isEnabled = isEnabled) else it } }
        return Result.success(Unit)
    }

    override suspend fun removeAllApps(): Result<Unit> {
        apps.value = emptyList()
        return Result.success(Unit)
    }

    override suspend fun getSelectedCount(): Result<Int> = Result.success(apps.value.size)

    override suspend fun getEnabledCount(): Result<Int> = Result.success(apps.value.count { it.isEnabled })
}
