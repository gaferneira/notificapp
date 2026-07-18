package dev.gaferneira.notificapp.core.network

import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.HttpMethod
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookAuth
import dev.gaferneira.notificapp.domain.model.WebhookTestResult
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
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

/** Marks an [IllegalArgumentException] as coming from [Request.Builder.url], not a header. */
private class WebhookUrlException(cause: Throwable) : Exception(cause)

/**
 * Sends a one-shot test payload to a user-configured [Webhook] URL. This is the app's first
 * network egress (ADR 012, design.md).
 *
 * Deliberately catches ALL relevant exceptions locally and NEVER throws or delegates to
 * `Result<T>`/`Failure.analyzeCause` - it always returns a [WebhookTestResult] directly, so the
 * `Failure` hierarchy is never involved in this flow. See design.md's "Send Test Payload" data
 * flow section for the full rationale.
 */
internal class WebhookTestClient @Inject constructor(
    private val client: OkHttpClient,
    @Dispatcher(DispatcherType.IO) private val io: CoroutineDispatcher,
) {

    suspend fun post(webhook: Webhook): WebhookTestResult = withContext(io) {
        try {
            val request = buildRequest(webhook)
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext WebhookTestResult.ServerError(response.code)
                }
                // Read the body once as a string; never trust Content-Length/contentLength(),
                // since a chunked response (contentLength() == -1) would otherwise bypass this
                // check - see design.md.
                val body = response.body?.string()
                if (body.isNullOrBlank()) {
                    WebhookTestResult.Success(response.code)
                } else if (isValidJson(body)) {
                    WebhookTestResult.Success(response.code)
                } else {
                    WebhookTestResult.MalformedBody
                }
            }
        } catch (e: WebhookUrlException) {
            // Request.Builder.url() rejected webhook.url - it can be stricter than the
            // java.net.URI check in Webhook.validate(). Never log the raw exception
            // message/cause - see design.md's no-log guarantee.
            Timber.e("Invalid URL for webhook %s", webhook.id)
            WebhookTestResult.InvalidUrl
        } catch (e: IllegalArgumentException) {
            // Disallowed characters (e.g. a stray newline/tab) in a header value, raised by
            // Headers.Builder.add(). Never log the raw exception message/cause - see design.md's
            // no-log guarantee.
            Timber.e("Invalid header value for webhook %s", webhook.id)
            WebhookTestResult.InvalidHeaderValue
        } catch (e: IOException) {
            Timber.e("Network error sending test payload for webhook %s", webhook.id)
            WebhookTestResult.NetworkError
        }
    }

    private fun isValidJson(body: String): Boolean = try {
        Json.parseToJsonElement(body)
        true
    } catch (expected: SerializationException) {
        false
    }

    private fun buildRequest(webhook: Webhook): Request {
        val headersBuilder = Headers.Builder()
        webhook.headers.forEach { (name, value) -> headersBuilder.add(name, value) }
        when (val auth = webhook.auth) {
            is WebhookAuth.None -> Unit
            is WebhookAuth.ApiKeyHeader -> headersBuilder.add(auth.headerName, auth.value)
            is WebhookAuth.BearerToken -> headersBuilder.add(AUTHORIZATION_HEADER, "Bearer ${auth.value}")
        }

        val httpUrl = try {
            webhook.url.toHttpUrlOrNull()?.newBuilder()?.apply {
                webhook.queryParams.forEach { (key, value) -> addQueryParameter(key, value) }
            }?.build() ?: throw IllegalArgumentException("Invalid URL")
        } catch (e: IllegalArgumentException) {
            throw WebhookUrlException(e)
        }

        val payload = samplePayload()
        val requestBody = if (webhook.method == HttpMethod.GET) null else payload.toRequestBody(JSON_MEDIA_TYPE)

        return Request.Builder()
            .url(httpUrl)
            .headers(headersBuilder.build())
            .method(webhook.method.name, requestBody)
            .build()
    }

    /** Fixed sample payload per design.md - no per-webhook customization in this PR. */
    private fun samplePayload(): String {
        val payload: JsonObject = buildJsonObject {
            put("event", "test")
            put("source", "Notificapp")
            put("timestamp", System.currentTimeMillis())
            putJsonObject("data") { put("message", "Test payload from Notificapp") }
        }
        return payload.toString()
    }
}
