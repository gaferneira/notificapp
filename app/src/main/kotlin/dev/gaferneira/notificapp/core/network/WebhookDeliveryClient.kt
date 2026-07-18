package dev.gaferneira.notificapp.core.network

import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.HttpMethod
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookAuth
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

private val JSON_MEDIA_TYPE = "application/json".toMediaType()
private const val AUTHORIZATION_HEADER = "Authorization"

/** Config error building the request (bad URL or a disallowed header value) - not an HTTP response. */
private const val CONFIG_ERROR_HTTP_CODE = 0

private const val HTTP_REQUEST_TIMEOUT = 408
private const val HTTP_TOO_MANY_REQUESTS = 429

/**
 * Sends a real `SEND_WEBHOOK` delivery [post] to a user-configured [Webhook], classifying the
 * outcome into a [DeliveryResult] for `WebhookDeliveryWorker`'s retry decision (design.md's Data
 * Flow table). Shares the [NetworkModule][dev.gaferneira.notificapp.core.di.NetworkModule]
 * `OkHttpClient` with `WebhookTestClient` - distinct because delivery sends a custom [body] and
 * needs 3-way classification, while the test flow sends a fixed sample body.
 *
 * Never throws, never logs URL/headers/body/exception message (only `webhook.id`) - see
 * design.md's no-log guarantee.
 */
internal class WebhookDeliveryClient @Inject constructor(
    private val client: OkHttpClient,
    @Dispatcher(DispatcherType.IO) private val io: CoroutineDispatcher,
) {

    suspend fun post(webhook: Webhook, body: String): DeliveryResult = withContext(io) {
        try {
            val request = buildRequest(webhook, body)
            client.newCall(request).execute().use { response ->
                classify(response.code)
            }
        } catch (e: IllegalArgumentException) {
            // Request.Builder.url() rejected webhook.url, or Headers.Builder.add() rejected a
            // disallowed header value. Never log the raw exception message/cause.
            Timber.e("Invalid request config for webhook %s", webhook.id)
            DeliveryResult.ClientError(CONFIG_ERROR_HTTP_CODE)
        } catch (e: IOException) {
            Timber.e("Network error delivering webhook %s", webhook.id)
            DeliveryResult.NetworkError
        }
    }

    private fun classify(httpCode: Int): DeliveryResult = when {
        httpCode in HTTP_SUCCESS_RANGE -> DeliveryResult.Delivered(httpCode)
        httpCode in HTTP_SERVER_ERROR_RANGE || httpCode == HTTP_REQUEST_TIMEOUT || httpCode == HTTP_TOO_MANY_REQUESTS -> DeliveryResult.ServerError(httpCode)
        else -> DeliveryResult.ClientError(httpCode)
    }

    private fun buildRequest(webhook: Webhook, body: String): Request {
        val headersBuilder = Headers.Builder()
        webhook.headers.forEach { (name, value) -> headersBuilder.add(name, value) }
        when (val auth = webhook.auth) {
            is WebhookAuth.None -> Unit
            is WebhookAuth.ApiKeyHeader -> headersBuilder.add(auth.headerName, auth.value)
            is WebhookAuth.BearerToken -> headersBuilder.add(AUTHORIZATION_HEADER, "Bearer ${auth.value}")
        }

        val httpUrl = webhook.url.toHttpUrlOrNull()?.newBuilder()?.apply {
            webhook.queryParams.forEach { (key, value) -> addQueryParameter(key, value) }
        }?.build() ?: throw IllegalArgumentException("Invalid URL")

        val requestBody = if (webhook.method == HttpMethod.GET) null else body.toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url(httpUrl)
            .headers(headersBuilder.build())
            .method(webhook.method.name, requestBody)
            .build()
    }

    private companion object {
        val HTTP_SUCCESS_RANGE = 200..299
        val HTTP_SERVER_ERROR_RANGE = 500..599
    }
}
