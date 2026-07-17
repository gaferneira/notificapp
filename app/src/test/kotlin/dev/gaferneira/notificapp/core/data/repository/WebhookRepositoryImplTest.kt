package dev.gaferneira.notificapp.core.data.repository

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.data.local.dao.WebhookDao
import dev.gaferneira.notificapp.core.data.local.mapper.WebhookMapper
import dev.gaferneira.notificapp.core.network.WebhookTestClient
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookTestResult
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
class WebhookRepositoryImplTest {

    private val testDispatcher = StandardTestDispatcher()
    private val dao: WebhookDao = mockk()
    private val testClient: WebhookTestClient = mockk()
    private val repository = WebhookRepositoryImpl(dao, testClient, testDispatcher)

    private fun webhook(id: String = "wh-1") = Webhook(id = id, name = "Home Assistant", url = "https://ha.local/api/hook")

    @Test
    fun `observeWebhooks maps every entity to a domain model`() = runTest(testDispatcher) {
        every { dao.getAll() } returns flowOf(listOf(WebhookMapper.toEntity(webhook())))

        repository.observeWebhooks().test {
            awaitItem().single().id shouldBe "wh-1"
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `getWebhook returns null when the dao has no matching row`() = runTest(testDispatcher) {
        coEvery { dao.getById("missing") } returns null

        val result = repository.getWebhook("missing")

        result.getOrThrow() shouldBe null
    }

    @Test
    fun `getWebhook maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { dao.getById("wh-1") } throws SQLException("db locked")

        val result = repository.getWebhook("wh-1")

        result.isFailure shouldBe true
    }

    @Test
    fun `saveWebhook persists the mapped entity for a valid webhook`() = runTest(testDispatcher) {
        coEvery { dao.insert(any()) } returns Unit

        val result = repository.saveWebhook(webhook())

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.insert(any()) }
    }

    @Test
    fun `saveWebhook rejects an invalid webhook without calling the dao`() = runTest(testDispatcher) {
        val invalid = Webhook(id = "wh-1", name = "", url = "not-a-url")

        val result = repository.saveWebhook(invalid)

        result.isFailure shouldBe true
        coVerify(exactly = 0) { dao.insert(any()) }
    }

    @Test
    fun `saveWebhook maps a dao exception to Result_failure without throwing`() = runTest(testDispatcher) {
        coEvery { dao.insert(any()) } throws SQLException("db locked")

        val result = repository.saveWebhook(webhook())

        result.isFailure shouldBe true
    }

    @Test
    fun `deleteWebhook delegates to the dao delete`() = runTest(testDispatcher) {
        coEvery { dao.deleteById("wh-1") } returns Unit

        val result = repository.deleteWebhook("wh-1")

        result.isSuccess shouldBe true
        coVerify(exactly = 1) { dao.deleteById("wh-1") }
    }

    @Test
    fun `sendTestPayload delegates to the WebhookTestClient`() = runTest(testDispatcher) {
        coEvery { testClient.post(any()) } returns WebhookTestResult.Success(200)

        val result = repository.sendTestPayload(webhook())

        result.getOrThrow() shouldBe WebhookTestResult.Success(200)
        coVerify(exactly = 1) { testClient.post(any()) }
    }
}
