package dev.gaferneira.notificapp.core.data.repository

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.data.local.dao.SelectedAppDao
import dev.gaferneira.notificapp.core.data.local.entity.SelectedAppEntity
import dev.gaferneira.notificapp.domain.model.SelectedApp
import io.kotest.matchers.shouldBe
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
class SelectedAppRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dao: SelectedAppDao = mockk()
    private val repository = SelectedAppRepositoryImpl(dao, testDispatcher)

    private fun entity(packageName: String, isEnabled: Boolean = true) = SelectedAppEntity(packageName = packageName, appName = "App $packageName", isEnabled = isEnabled)

    @Test
    fun `observeAllApps maps every entity to a domain model`() = runTest(testDispatcher) {
        every { dao.getAll() } returns flowOf(listOf(entity("com.a")))

        repository.observeAllApps().test {
            awaitItem().single().packageName shouldBe "com.a"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `observeEnabledApps reflects only dao-enabled rows`() = runTest(testDispatcher) {
        every { dao.getEnabledApps() } returns flowOf(listOf(entity("com.a", isEnabled = true)))

        repository.observeEnabledApps().test {
            awaitItem().single().isEnabled shouldBe true
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getAllApps maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { dao.getAllSync() } throws SQLException("db locked")

        val result = repository.getAllApps()

        result.isFailure shouldBe true
    }

    @Test
    fun `getApp returns null when the dao has no matching row`() = runTest(testDispatcher) {
        coEvery { dao.getByPackageName("missing") } returns null

        val result = repository.getApp("missing")

        result.getOrThrow() shouldBe null
    }

    @Test
    fun `isAppSelected and isAppEnabled delegate to the dao`() = runTest(testDispatcher) {
        coEvery { dao.isAppSelected("com.a") } returns true
        coEvery { dao.isAppEnabled("com.a") } returns false

        repository.isAppSelected("com.a").getOrThrow() shouldBe true
        repository.isAppEnabled("com.a").getOrThrow() shouldBe false
    }

    @Test
    fun `addApp persists the mapped entity`() = runTest(testDispatcher) {
        coEvery { dao.insert(any()) } returns Unit
        val app = SelectedApp(packageName = "com.a", appName = "App", isEnabled = true)

        val result = repository.addApp(app)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `addApps inserts all mapped entities in one call`() = runTest(testDispatcher) {
        coEvery { dao.insertAll(any()) } returns Unit
        val apps = listOf(
            SelectedApp(packageName = "com.a", appName = "A", isEnabled = true),
            SelectedApp(packageName = "com.b", appName = "B", isEnabled = true),
        )

        val result = repository.addApps(apps)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.insertAll(any()) }
    }

    @Test
    fun `removeApp delegates to the dao delete`() = runTest(testDispatcher) {
        coEvery { dao.deleteByPackageName("com.a") } returns Unit

        val result = repository.removeApp("com.a")

        result.isSuccess shouldBe true
    }

    @Test
    fun `setAppEnabled delegates to the dao with the given flag`() = runTest(testDispatcher) {
        coEvery { dao.setEnabled("com.a", false) } returns Unit

        val result = repository.setAppEnabled("com.a", false)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.setEnabled("com.a", false) }
    }

    @Test
    fun `getSelectedCount and getEnabledCount return the dao counts`() = runTest(testDispatcher) {
        coEvery { dao.getCount() } returns 10
        coEvery { dao.getEnabledCount() } returns 4

        repository.getSelectedCount().getOrThrow() shouldBe 10
        repository.getEnabledCount().getOrThrow() shouldBe 4
    }
}
