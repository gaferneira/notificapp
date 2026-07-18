package dev.gaferneira.notificapp.core.network

import android.content.Context
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.gaferneira.notificapp.core.data.local.dao.WebhookDao
import dev.gaferneira.notificapp.core.data.local.dao.WebhookDeliveryDao
import dev.gaferneira.notificapp.core.data.repository.WebhookDeliveryRepositoryImpl
import dev.gaferneira.notificapp.core.data.repository.WebhookRepositoryImpl
import dev.gaferneira.notificapp.core.notification.action.WebhookDeliveryEnqueuer
import dev.gaferneira.notificapp.core.notification.action.WebhookDeliveryWorker
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookAuth
import dev.gaferneira.notificapp.domain.model.WebhookDelivery
import dev.gaferneira.notificapp.domain.model.WebhookTestResult
import dev.gaferneira.notificapp.domain.repository.WebhookDeliveryRepository
import dev.gaferneira.notificapp.domain.repository.WebhookRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeWebhookDeliveryRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeWebhookRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import timber.log.Timber
import java.io.IOException
import java.sql.SQLException

/**
 * Automated no-log guarantee test (design.md): exercises [WebhookRepositoryImpl],
 * [WebhookTestClient], [WebhookDeliveryClient], [WebhookDeliveryRepositoryImpl], and
 * [WebhookDeliveryWorker] failure paths (dao exception, malformed header value, network error,
 * client-error delivery) with a fixed sample `authValue`/payload and asserts it never appears in
 * any Timber-logged message, throwable message, or throwable `toString()`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebhookNoLogTest {

    private val testDispatcher = StandardTestDispatcher()

    /** Captures every Timber log call across the test run. */
    private class ProbeTree : Timber.Tree() {
        val entries = mutableListOf<Triple<String?, String, Throwable?>>()

        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            entries += Triple(tag, message, t)
        }
    }

    private lateinit var probeTree: ProbeTree

    private companion object {
        /** Fixed sample secret - must never appear in any captured log output. */
        const val SECRET_AUTH_VALUE = "sk-test-secret-should-never-log"
    }

    @BeforeEach
    fun setUp() {
        probeTree = ProbeTree()
        Timber.plant(probeTree)
    }

    @AfterEach
    fun tearDown() {
        Timber.uprootAll()
    }

    private fun webhookWithSecret(id: String = "wh-1") = Webhook(
        id = id,
        name = "Home Assistant",
        url = "https://ha.local/api/hook",
        auth = WebhookAuth.BearerToken(value = SECRET_AUTH_VALUE),
    )

    private fun assertSecretNeverLogged() {
        probeTree.entries.forEach { (_, message, throwable) ->
            message.contains(SECRET_AUTH_VALUE) shouldBe false
            (throwable?.message?.contains(SECRET_AUTH_VALUE) ?: false) shouldBe false
            (throwable?.toString()?.contains(SECRET_AUTH_VALUE) ?: false) shouldBe false
        }
    }

    @Test
    fun `dao exception during saveWebhook never logs the auth value`() = runTest(testDispatcher) {
        val dao: WebhookDao = mockk()
        val testClient: WebhookTestClient = mockk()
        coEvery { dao.insert(any()) } throws SQLException("db locked")
        val repository = WebhookRepositoryImpl(dao, testClient, testDispatcher)

        val result = repository.saveWebhook(webhookWithSecret())

        result.isFailure shouldBe true
        assertSecretNeverLogged()
    }

    @Test
    fun `malformed header value never logs the auth value`() = runTest(testDispatcher) {
        val client: OkHttpClient = mockk()
        val testClient = WebhookTestClient(client, testDispatcher)
        // A newline in a header value is disallowed by OkHttp's Headers.Builder.add() and
        // triggers a real IllegalArgumentException - never reaching client.newCall().
        val webhook = Webhook(
            id = "wh-2",
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            auth = WebhookAuth.ApiKeyHeader(value = "$SECRET_AUTH_VALUE\ninjected"),
        )

        val result = testClient.post(webhook)

        result shouldBe WebhookTestResult.InvalidHeaderValue
        assertSecretNeverLogged()
    }

    @Test
    fun `url rejected by OkHttp is reported as InvalidUrl, not InvalidHeaderValue`() = runTest(testDispatcher) {
        val client: OkHttpClient = mockk()
        val testClient = WebhookTestClient(client, testDispatcher)
        // WebhookTestClient.post doesn't re-validate the URL, so a caller bypassing
        // Webhook.validate() can still reach Request.Builder.url() with an unparseable string -
        // OkHttp rejects a URL with no http/https scheme with IllegalArgumentException.
        val webhook = Webhook(
            id = "wh-3",
            name = "Home Assistant",
            url = "not-a-valid-url",
            auth = WebhookAuth.BearerToken(value = SECRET_AUTH_VALUE),
        )

        val result = testClient.post(webhook)

        result shouldBe WebhookTestResult.InvalidUrl
        assertSecretNeverLogged()
    }

    @Test
    fun `network error never logs the auth value`() = runTest(testDispatcher) {
        val client: OkHttpClient = mockk()
        val call: Call = mockk()
        every { client.newCall(any()) } returns call
        every { call.execute() } throws IOException("connection refused: $SECRET_AUTH_VALUE should not reach here regardless")
        val testClient = WebhookTestClient(client, testDispatcher)

        val result = testClient.post(webhookWithSecret())

        result shouldBe WebhookTestResult.NetworkError
        assertSecretNeverLogged()
    }

    @Test
    fun `WebhookDeliveryClient network error never logs the auth value or payload`() = runTest(testDispatcher) {
        val client: OkHttpClient = mockk()
        val call: Call = mockk()
        every { client.newCall(any()) } returns call
        every { call.execute() } throws IOException("connection refused: $SECRET_AUTH_VALUE should not reach here regardless")
        val deliveryClient = WebhookDeliveryClient(client, testDispatcher)

        val result = deliveryClient.post(webhookWithSecret(), body = """{"secret":"$SECRET_AUTH_VALUE"}""")

        result shouldBe DeliveryResult.NetworkError
        assertSecretNeverLogged()
    }

    @Test
    fun `WebhookDeliveryClient invalid request config never logs the auth value or payload`() = runTest(testDispatcher) {
        val client: OkHttpClient = mockk()
        val deliveryClient = WebhookDeliveryClient(client, testDispatcher)
        val webhook = Webhook(
            id = "wh-4",
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            auth = WebhookAuth.ApiKeyHeader(value = "$SECRET_AUTH_VALUE\ninjected"),
        )

        val result = deliveryClient.post(webhook, body = """{"secret":"$SECRET_AUTH_VALUE"}""")

        result shouldBe DeliveryResult.ClientError(0)
        assertSecretNeverLogged()
    }

    @Test
    fun `WebhookDeliveryRepositoryImpl dao exception during enqueue never logs the payload`() = runTest(testDispatcher) {
        val dao: WebhookDeliveryDao = mockk()
        coEvery { dao.insert(any()) } throws SQLException("db locked")
        val repository = WebhookDeliveryRepositoryImpl(dao, testDispatcher)

        val result = repository.enqueue(WebhookDelivery(id = "delivery-1", webhookId = "wh-1", payload = SECRET_AUTH_VALUE))

        result.isFailure shouldBe true
        assertSecretNeverLogged()
    }

    @Test
    fun `WebhookDeliveryWorker client-error failure path never logs the auth value or payload`() = runTest(testDispatcher) {
        val webhook = webhookWithSecret()
        val delivery = WebhookDelivery(id = "delivery-1", webhookId = webhook.id, payload = """{"secret":"$SECRET_AUTH_VALUE"}""")
        val deliveryRepo: WebhookDeliveryRepository = FakeWebhookDeliveryRepository(listOf(delivery))
        val webhookRepo: WebhookRepository = FakeWebhookRepository(listOf(webhook))
        val okHttpClient: OkHttpClient = mockk()
        val call: Call = mockk()
        every { okHttpClient.newCall(any()) } returns call
        val response: Response = mockk {
            every { code } returns 401
            every { close() } returns Unit
        }
        every { call.execute() } returns response
        val deliveryClient = WebhookDeliveryClient(okHttpClient, testDispatcher)
        val enqueuer = mockk<WebhookDeliveryEnqueuer>(relaxed = true)
        val context: Context = mockk(relaxed = true)
        val factory = object : WorkerFactory() {
            override fun createWorker(
                appContext: Context,
                workerClassName: String,
                workerParameters: WorkerParameters,
            ): ListenableWorker = WebhookDeliveryWorker(appContext, workerParameters, deliveryRepo, webhookRepo, deliveryClient, enqueuer, testDispatcher)
        }
        val worker = TestListenableWorkerBuilder.from(context, WebhookDeliveryWorker::class.java)
            .setWorkerFactory(factory)
            .setInputData(workDataOf(WebhookDeliveryWorker.KEY_DELIVERY_ID to "delivery-1"))
            .build()

        worker.doWork()

        assertSecretNeverLogged()
    }
}
