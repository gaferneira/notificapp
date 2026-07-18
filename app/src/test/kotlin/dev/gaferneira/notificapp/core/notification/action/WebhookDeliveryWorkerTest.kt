package dev.gaferneira.notificapp.core.notification.action

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.gaferneira.notificapp.core.network.DeliveryResult
import dev.gaferneira.notificapp.core.network.WebhookDeliveryClient
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookDelivery
import dev.gaferneira.notificapp.domain.model.WebhookDeliveryStatus
import dev.gaferneira.notificapp.domain.repository.WebhookDeliveryRepository
import dev.gaferneira.notificapp.domain.repository.WebhookRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import androidx.work.ListenableWorker.Result as WorkResult

/**
 * Unit tests for [WebhookDeliveryWorker] per design.md's Testing Strategy, built via
 * `work-testing`'s [TestListenableWorkerBuilder] with a [WorkerFactory] that hands back this
 * worker's mocked collaborators directly (the production `@HiltWorker` constructor isn't a plain
 * `(Context, WorkerParameters)` 2-arg constructor, so `DefaultWorkerFactory`'s reflection-based
 * fallback can't build it).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebhookDeliveryWorkerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val deliveryRepo: WebhookDeliveryRepository = mockk()
    private val webhookRepo: WebhookRepository = mockk()
    private val deliveryClient: WebhookDeliveryClient = mockk()
    private val enqueuer: WebhookDeliveryEnqueuer = mockk()

    private fun delivery(attemptCount: Int = 0) = WebhookDelivery(id = "delivery-1", webhookId = "wh-1", payload = "{}", attemptCount = attemptCount)

    private fun webhook() = Webhook(id = "wh-1", name = "Home Assistant", url = "https://ha.local/api/hook")

    private fun buildWorker(): WebhookDeliveryWorker {
        val context: Context = mockk(relaxed = true)
        val factory = object : WorkerFactory() {
            override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker = WebhookDeliveryWorker(appContext, workerParameters, deliveryRepo, webhookRepo, deliveryClient, enqueuer, testDispatcher)
        }
        return TestListenableWorkerBuilder.from(context, WebhookDeliveryWorker::class.java)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(WebhookDeliveryWorker.KEY_DELIVERY_ID to "delivery-1"))
            .build()
    }

    @Test
    fun `delivered outcome marks delivered, updates status DELIVERED, and succeeds`() = runTest(testDispatcher) {
        coEvery { deliveryRepo.getById("delivery-1") } returns Result.success(delivery())
        coEvery { webhookRepo.getWebhook("wh-1") } returns Result.success(webhook())
        coEvery { deliveryClient.post(any(), any()) } returns DeliveryResult.Delivered(200)
        coEvery { deliveryRepo.markDelivered("delivery-1") } returns Result.success(Unit)
        coEvery { webhookRepo.updateDeliveryStatus(any(), any(), any()) } returns Result.success(Unit)

        val result = buildWorker().doWork()

        result shouldBe WorkResult.success()
        coVerify(exactly = 1) { deliveryRepo.markDelivered("delivery-1") }
        coVerify(exactly = 1) { webhookRepo.updateDeliveryStatus("wh-1", WebhookDeliveryStatus.DELIVERED, any()) }
    }

    @Test
    fun `client error fails fast, marks CLIENT failure, and updates status CONFIG_ERROR`() = runTest(testDispatcher) {
        coEvery { deliveryRepo.getById("delivery-1") } returns Result.success(delivery())
        coEvery { webhookRepo.getWebhook("wh-1") } returns Result.success(webhook())
        coEvery { deliveryClient.post(any(), any()) } returns DeliveryResult.ClientError(404)
        coEvery { deliveryRepo.markFailed(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { webhookRepo.updateDeliveryStatus(any(), any(), any()) } returns Result.success(Unit)

        val result = buildWorker().doWork()

        result shouldBe WorkResult.failure()
        coVerify(exactly = 1) { deliveryRepo.markFailed("delivery-1", "CLIENT", 0, any()) }
        coVerify(exactly = 1) { webhookRepo.updateDeliveryStatus("wh-1", WebhookDeliveryStatus.CONFIG_ERROR, any()) }
    }

    @Test
    fun `server error under the attempt cap re-enqueues instead of marking failed`() = runTest(testDispatcher) {
        coEvery { deliveryRepo.getById("delivery-1") } returns Result.success(delivery(attemptCount = 0))
        coEvery { webhookRepo.getWebhook("wh-1") } returns Result.success(webhook())
        coEvery { deliveryClient.post(any(), any()) } returns DeliveryResult.ServerError(500)
        coEvery { enqueuer.enqueue(any(), any()) } returns Unit

        val result = buildWorker().doWork()

        result shouldBe WorkResult.success()
        coVerify(exactly = 1) {
            enqueuer.enqueue(match<WebhookDelivery> { it.attemptCount == 1 }, 60_000L)
        }
        coVerify(exactly = 0) { deliveryRepo.markFailed(any(), any(), any(), any()) }
    }

    @Test
    fun `server error at the attempt cap marks failed and updates status UNREACHABLE`() = runTest(testDispatcher) {
        coEvery { deliveryRepo.getById("delivery-1") } returns Result.success(delivery(attemptCount = 2))
        coEvery { webhookRepo.getWebhook("wh-1") } returns Result.success(webhook())
        coEvery { deliveryClient.post(any(), any()) } returns DeliveryResult.ServerError(503)
        coEvery { deliveryRepo.markFailed(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { webhookRepo.updateDeliveryStatus(any(), any(), any()) } returns Result.success(Unit)

        val result = buildWorker().doWork()

        result shouldBe WorkResult.failure()
        coVerify(exactly = 1) { deliveryRepo.markFailed("delivery-1", "SERVER", 3, any()) }
        coVerify(exactly = 1) { webhookRepo.updateDeliveryStatus("wh-1", WebhookDeliveryStatus.UNREACHABLE, any()) }
        coVerify(exactly = 0) { enqueuer.enqueue(any(), any()) }
    }

    @Test
    fun `orphaned delivery whose webhook was deleted is dropped instead of marked failed`() = runTest(testDispatcher) {
        coEvery { deliveryRepo.getById("delivery-1") } returns Result.success(delivery())
        coEvery { webhookRepo.getWebhook("wh-1") } returns Result.success(null)
        coEvery { deliveryRepo.drop("delivery-1") } returns Result.success(Unit)

        val result = buildWorker().doWork()

        result shouldBe WorkResult.failure()
        coVerify(exactly = 1) { deliveryRepo.drop("delivery-1") }
        coVerify(exactly = 0) { deliveryRepo.markFailed(any(), any(), any(), any()) }
    }

    @Test
    fun `network error retries without touching the queue or webhook status`() = runTest(testDispatcher) {
        coEvery { deliveryRepo.getById("delivery-1") } returns Result.success(delivery())
        coEvery { webhookRepo.getWebhook("wh-1") } returns Result.success(webhook())
        coEvery { deliveryClient.post(any(), any()) } returns DeliveryResult.NetworkError

        val result = buildWorker().doWork()

        result shouldBe WorkResult.retry()
        coVerify(exactly = 0) { deliveryRepo.markFailed(any(), any(), any(), any()) }
        coVerify(exactly = 0) { deliveryRepo.markDelivered(any()) }
        coVerify(exactly = 0) { webhookRepo.updateDeliveryStatus(any(), any(), any()) }
    }
}
