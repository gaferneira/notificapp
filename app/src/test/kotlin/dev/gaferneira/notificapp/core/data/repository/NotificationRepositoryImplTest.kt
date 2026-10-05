package dev.gaferneira.notificapp.core.data.repository

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.data.local.dao.AppWithLatestName
import dev.gaferneira.notificapp.core.data.local.dao.NotificationDao
import dev.gaferneira.notificapp.core.data.local.entity.NotificationEntity
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.sql.SQLException

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dao: NotificationDao = mockk()
    private val repository = NotificationRepositoryImpl(dao, testDispatcher)

    private fun entity(id: String, packageName: String = "com.bank", isProcessed: Boolean = false, appliedRulesCount: Int = 0) = NotificationEntity(
        id = id,
        packageName = packageName,
        appName = "Bank",
        title = "Title",
        content = "Content",
        rawContent = "Title\nContent",
        timestamp = 0L,
        isProcessed = isProcessed,
        appliedRulesCount = appliedRulesCount,
    )

    @Test
    fun `observeAllNotifications maps every entity to a domain model`() = runTest(testDispatcher) {
        every { dao.getAll() } returns flowOf(listOf(entity("n1")))

        repository.observeAllNotifications().test {
            awaitItem().single().id shouldBe "n1"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `isProcessed is true when the entity has applied rules even if the flag itself is false`() = runTest(testDispatcher) {
        every { dao.getAll() } returns flowOf(listOf(entity("n1", isProcessed = false, appliedRulesCount = 2)))

        repository.observeAllNotifications().test {
            awaitItem().single().isProcessed shouldBe true
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `deleteAll delegates to the dao`() = runTest(testDispatcher) {
        coEvery { dao.deleteAll() } returns Unit

        val result = repository.deleteAll()

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.deleteAll() }
    }

    @Test
    fun `deleteAll maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { dao.deleteAll() } throws SQLException("db locked")

        val result = repository.deleteAll()

        result.isFailure shouldBe true
    }

    @Test
    fun `getNotification returns null when the dao has no matching row`() = runTest(testDispatcher) {
        coEvery { dao.getById("missing") } returns null

        val result = repository.getNotification("missing")

        result.getOrThrow() shouldBe null
    }

    @Test
    fun `getNotification maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { dao.getById("n1") } throws SQLException("db locked")

        val result = repository.getNotification("n1")

        result.isFailure shouldBe true
    }

    @Test
    fun `saveNotification inserts the mapped entity`() = runTest(testDispatcher) {
        coEvery { dao.insert(any()) } returns Unit
        val notification = createTestNotification(id = "n1")

        val result = repository.saveNotification(notification)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `markAsProcessed delegates to the dao`() = runTest(testDispatcher) {
        coEvery { dao.markAsProcessed("n1") } returns Unit

        val result = repository.markAsProcessed("n1")

        result.isSuccess shouldBe true
    }

    @Test
    fun `getUnprocessedNotifications maps every entity`() = runTest(testDispatcher) {
        coEvery { dao.getUnprocessed() } returns listOf(entity("n1"), entity("n2"))

        val result = repository.getUnprocessedNotifications()

        result.getOrThrow().map { it.id } shouldBe listOf("n1", "n2")
    }

    @Test
    fun `getNotificationsForBacktest with no target packages uses the unfiltered recent query`() = runTest(testDispatcher) {
        coEvery { dao.getRecent(10) } returns listOf(entity("n1"))

        val result = repository.getNotificationsForBacktest(targetPackages = null, limit = 10)

        result.getOrThrow().single().id shouldBe "n1"
        coVerify(exactly = 1) { dao.getRecent(10) }
    }

    @Test
    fun `getNotificationsForBacktest with target packages uses the filtered recent query`() = runTest(testDispatcher) {
        coEvery { dao.getRecentByPackageNames(listOf("com.bank"), 10) } returns listOf(entity("n1"))

        val result = repository.getNotificationsForBacktest(targetPackages = listOf("com.bank"), limit = 10)

        result.getOrThrow().single().id shouldBe "n1"
        coVerify(exactly = 1) { dao.getRecentByPackageNames(listOf("com.bank"), 10) }
    }

    @Test
    fun `getNotificationCount and getUnprocessedCount return the dao counts`() = runTest(testDispatcher) {
        coEvery { dao.getCount() } returns 42
        coEvery { dao.getUnprocessedCount() } returns 7

        repository.getNotificationCount().getOrThrow() shouldBe 42
        repository.getUnprocessedCount().getOrThrow() shouldBe 7
    }

    @Test
    fun `redactContent delegates to the dao`() = runTest(testDispatcher) {
        coEvery { dao.redactContent("n1") } returns Unit

        val result = repository.redactContent("n1")

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.redactContent("n1") }
    }

    @Test
    fun `redactContent maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { dao.redactContent("n1") } throws SQLException("db locked")

        val result = repository.redactContent("n1")

        result.isFailure shouldBe true
    }

    @Test
    fun `observeAppsWithNotifications maps dao rows to AppInfo`() = runTest(testDispatcher) {
        every { dao.observeAppsWithLatestName() } returns flowOf(listOf(AppWithLatestName("com.bank", "Bank")))

        repository.observeAppsWithNotifications().test {
            val apps = awaitItem()
            apps.single().packageName shouldBe "com.bank"
            apps.single().name shouldBe "Bank"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeNotification maps the entity and emits null when the row is gone`() = runTest(testDispatcher) {
        // Given: a dao flow that first has the row and then loses it (deleted while observed)
        every { dao.observeById("n1") } returns flowOf(entity("n1"), null)

        // When / Then
        repository.observeNotification("n1").test {
            awaitItem()!!.id shouldBe "n1"
            awaitItem() shouldBe null
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeNotification maps a dao failure to a Failure`() = runTest(testDispatcher) {
        every { dao.observeById("n1") } returns kotlinx.coroutines.flow.flow { throw SQLException("db locked") }

        repository.observeNotification("n1").test {
            awaitError().shouldBeInstanceOf<dev.gaferneira.notificapp.core.common.Failure>()
        }
    }
}
