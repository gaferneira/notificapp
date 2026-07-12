package dev.gaferneira.notificapp.testutil.fakes

import androidx.paging.PagingData
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

/**
 * Deterministic in-memory [NotificationRepository] fake for VM tests, backed by a
 * [MutableStateFlow]. The paginated methods return an empty [PagingData] by default (faking
 * Paging3's real pipeline isn't worth the complexity for VM-level tests) - override
 * [pagedNotifications] if a test needs paging content.
 */
class FakeNotificationRepository(initial: List<Notification> = emptyList()) : NotificationRepository {

    private val notifications = MutableStateFlow(initial)

    /** Paging content returned by [observeNotificationsPaged] / [searchNotificationsPaged]. */
    var pagedNotifications: Flow<PagingData<Notification>> = flowOf(PagingData.empty())

    /** Opt-in failure injection: set before a call to make [getNotificationsForBacktest] fail. */
    var backtestError: Throwable? = null

    /** Number of times [getNotificationsForBacktest] has been called, for interaction assertions. */
    var backtestCallCount: Int = 0
        private set

    fun currentNotifications(): List<Notification> = notifications.value

    fun setNotifications(newNotifications: List<Notification>) {
        notifications.value = newNotifications
    }

    override fun observeAllNotifications(): Flow<List<Notification>> = notifications.asStateFlow()

    override fun observeNotificationsByApps(packageNames: List<String>): Flow<List<Notification>> = notifications.asStateFlow()

    override fun observeNotificationsPaged(packageNames: List<String>, isProcessed: Boolean?): Flow<PagingData<Notification>> = pagedNotifications

    override fun searchNotificationsPaged(
        query: String,
        packageNames: List<String>,
        isProcessed: Boolean?,
    ): Flow<PagingData<Notification>> = pagedNotifications

    override suspend fun getNotificationsForBacktest(targetPackages: List<String>?, limit: Int): Result<List<Notification>> {
        backtestCallCount++
        backtestError?.let { return Result.failure(it) }
        val filtered = if (targetPackages.isNullOrEmpty()) {
            notifications.value
        } else {
            notifications.value.filter { it.packageName in targetPackages }
        }
        return Result.success(filtered.take(limit))
    }

    override suspend fun getNotification(id: String): Result<Notification?> = Result.success(notifications.value.find { it.id == id })

    override suspend fun getUnprocessedNotifications(): Result<List<Notification>> = Result.success(notifications.value.filterNot { it.isProcessed })

    override suspend fun searchNotifications(query: String): Result<List<Notification>> = Result.success(
        notifications.value.filter {
            it.title?.contains(query, ignoreCase = true) == true || it.content?.contains(query, ignoreCase = true) == true
        },
    )

    override suspend fun saveNotification(notification: Notification): Result<Unit> {
        notifications.update { list -> list.filterNot { it.id == notification.id } + notification }
        return Result.success(Unit)
    }

    override suspend fun markAsProcessed(id: String): Result<Unit> {
        notifications.update { list -> list.map { if (it.id == id) it.copy(isProcessed = true) else it } }
        return Result.success(Unit)
    }

    override suspend fun deleteNotification(id: String): Result<Unit> {
        notifications.update { list -> list.filterNot { it.id == id } }
        return Result.success(Unit)
    }

    override suspend fun deleteOlderThan(timestamp: Long): Result<Unit> {
        notifications.update { list -> list.filterNot { it.timestamp < timestamp } }
        return Result.success(Unit)
    }

    override suspend fun deleteByApp(packageName: String): Result<Unit> {
        notifications.update { list -> list.filterNot { it.packageName == packageName } }
        return Result.success(Unit)
    }

    override suspend fun getNotificationCount(): Result<Int> = Result.success(notifications.value.size)

    override suspend fun getRecentNotifications(packageName: String, lookbackMs: Long): Result<List<Notification>> = Result.success(notifications.value.filter { it.packageName == packageName })

    override suspend fun getUnprocessedCount(): Result<Int> = Result.success(notifications.value.count { !it.isProcessed })

    override fun observeAppsWithNotifications(): Flow<List<AppInfo>> = MutableStateFlow(
        notifications.value.map { AppInfo(packageName = it.packageName, name = it.appName) }.distinctBy { it.packageName },
    ).asStateFlow()
}
