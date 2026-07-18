package dev.gaferneira.notificapp.core.network

import dev.gaferneira.notificapp.domain.model.Webhook
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Unit tests for [WebhookDeliveryClient]'s outcome classification per design.md's Testing
 * Strategy: 200/201 -> Delivered, 500/408/429 -> ServerError, 400/401/404 -> ClientError,
 * IOException -> NetworkError.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WebhookDeliveryClientTest {

    private val testDispatcher = StandardTestDispatcher()
    private val okHttpClient: OkHttpClient = mockk()
    private val deliveryClient = WebhookDeliveryClient(okHttpClient, testDispatcher)

    private fun webhook(url: String = "https://ha.local/api/hook") = Webhook(id = "wh-1", name = "Home Assistant", url = url)

    private fun mockResponse(code: Int): Response = mockk {
        every { this@mockk.code } returns code
        every { close() } returns Unit
    }

    private fun stubCallReturning(code: Int) {
        val call: Call = mockk()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } returns mockResponse(code)
    }

    @Test
    fun `200 is classified as Delivered`() = runTest(testDispatcher) {
        stubCallReturning(200)

        val result = deliveryClient.post(webhook(), "{}")

        result shouldBe DeliveryResult.Delivered(200)
    }

    @Test
    fun `201 is classified as Delivered`() = runTest(testDispatcher) {
        stubCallReturning(201)

        val result = deliveryClient.post(webhook(), "{}")

        result shouldBe DeliveryResult.Delivered(201)
    }

    @Test
    fun `500 is classified as ServerError`() = runTest(testDispatcher) {
        stubCallReturning(500)

        val result = deliveryClient.post(webhook(), "{}")

        result shouldBe DeliveryResult.ServerError(500)
    }

    @Test
    fun `408 is classified as ServerError`() = runTest(testDispatcher) {
        stubCallReturning(408)

        val result = deliveryClient.post(webhook(), "{}")

        result shouldBe DeliveryResult.ServerError(408)
    }

    @Test
    fun `429 is classified as ServerError`() = runTest(testDispatcher) {
        stubCallReturning(429)

        val result = deliveryClient.post(webhook(), "{}")

        result shouldBe DeliveryResult.ServerError(429)
    }

    @Test
    fun `400 is classified as ClientError`() = runTest(testDispatcher) {
        stubCallReturning(400)

        val result = deliveryClient.post(webhook(), "{}")

        result shouldBe DeliveryResult.ClientError(400)
    }

    @Test
    fun `401 is classified as ClientError`() = runTest(testDispatcher) {
        stubCallReturning(401)

        val result = deliveryClient.post(webhook(), "{}")

        result shouldBe DeliveryResult.ClientError(401)
    }

    @Test
    fun `404 is classified as ClientError`() = runTest(testDispatcher) {
        stubCallReturning(404)

        val result = deliveryClient.post(webhook(), "{}")

        result shouldBe DeliveryResult.ClientError(404)
    }

    @Test
    fun `IOException is classified as NetworkError`() = runTest(testDispatcher) {
        val call: Call = mockk()
        every { okHttpClient.newCall(any()) } returns call
        every { call.execute() } throws IOException("connection refused")

        val result = deliveryClient.post(webhook(), "{}")

        result shouldBe DeliveryResult.NetworkError
    }

    @Test
    fun `invalid url is classified as ClientError without throwing`() = runTest(testDispatcher) {
        val result = deliveryClient.post(webhook(url = "not-a-valid-url"), "{}")

        result shouldBe DeliveryResult.ClientError(0)
    }
}
