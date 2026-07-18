package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.data.local.dao.WebhookDeliveryDao
import dev.gaferneira.notificapp.core.data.local.mapper.WebhookDeliveryMapper
import dev.gaferneira.notificapp.domain.model.WebhookDelivery
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.sql.SQLException

/**
 * Unit tests for [WebhookDeliveryRepositoryImpl] per design.md's Testing Strategy:
 * enqueue/markFailed/pendingFailures entity<->domain mapping, dao exception -> Result.failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebhookDeliveryRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dao: WebhookDeliveryDao = mockk()
    private val repository = WebhookDeliveryRepositoryImpl(dao, testDispatcher)

    private fun delivery(id: String = "delivery-1") = WebhookDelivery(id = id, webhookId = "wh-1", payload = "{}", createdAt = 1_000L)

    @Test
    fun `enqueue persists the mapped entity`() = runTest(testDispatcher) {
        coEvery { dao.insert(any()) } returns Unit

        val result = repository.enqueue(delivery())

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.insert(WebhookDeliveryMapper.toEntity(delivery())) }
    }

    @Test
    fun `enqueue maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { dao.insert(any()) } throws SQLException("db locked")

        val result = repository.enqueue(delivery())

        result.isFailure shouldBe true
    }

    @Test
    fun `getById maps the entity to a domain model`() = runTest(testDispatcher) {
        coEvery { dao.getById("delivery-1") } returns WebhookDeliveryMapper.toEntity(delivery())

        val result = repository.getById("delivery-1")

        result.getOrThrow() shouldBe delivery()
    }

    @Test
    fun `getById returns null when the dao has no matching row`() = runTest(testDispatcher) {
        coEvery { dao.getById("missing") } returns null

        val result = repository.getById("missing")

        result.getOrThrow() shouldBe null
    }

    @Test
    fun `markDelivered deletes the row`() = runTest(testDispatcher) {
        coEvery { dao.deleteById("delivery-1") } returns Unit

        val result = repository.markDelivered("delivery-1")

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.deleteById("delivery-1") }
    }

    @Test
    fun `markFailed maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { dao.updateFailure(any(), any(), any(), any()) } throws SQLException("db locked")

        val result = repository.markFailed("delivery-1", "SERVER", 1, 1000L)

        result.isFailure shouldBe true
    }

    @Test
    fun `markFailed delegates to the dao with the given failure type, attempt count, and timestamp`() = runTest(testDispatcher) {
        coEvery { dao.updateFailure(any(), any(), any(), any()) } returns Unit

        val result = repository.markFailed("delivery-1", "CLIENT", 2, 5000L)

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.updateFailure("delivery-1", "CLIENT", 2, 5000L) }
    }

    @Test
    fun `drop deletes the row`() = runTest(testDispatcher) {
        coEvery { dao.deleteById("delivery-1") } returns Unit

        val result = repository.drop("delivery-1")

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.deleteById("delivery-1") }
    }

    @Test
    fun `drop maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { dao.deleteById("delivery-1") } throws SQLException("db locked")

        val result = repository.drop("delivery-1")

        result.isFailure shouldBe true
    }

    @Test
    fun `pendingFailures maps every entity to a domain model`() = runTest(testDispatcher) {
        coEvery { dao.getAllUnresolved() } returns listOf(WebhookDeliveryMapper.toEntity(delivery()))

        val result = repository.pendingFailures()

        result.getOrThrow() shouldBe listOf(delivery())
    }

    @Test
    fun `pendingFailures maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { dao.getAllUnresolved() } throws SQLException("db locked")

        val result = repository.pendingFailures()

        result.isFailure shouldBe true
    }
}
