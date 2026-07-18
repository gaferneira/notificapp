package dev.gaferneira.notificapp.domain.model

import java.net.URI
import java.net.URISyntaxException
import java.util.UUID

/**
 * A user-defined webhook: a name, a target URL, optional custom headers, and an optional auth
 * mechanism. This is CRUD + connection infrastructure only (Phase 4 PR1) - no rule-action
 * wiring, retry, or queueing yet.
 */
data class Webhook(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val auth: WebhookAuth = WebhookAuth.None,
    val method: HttpMethod = HttpMethod.POST,
    val queryParams: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis(),
    /** Delivery outcome of the most recent `SEND_WEBHOOK` attempt (Phase 4 PR2). */
    val lastDeliveryStatus: WebhookDeliveryStatus = WebhookDeliveryStatus.UNKNOWN,
    /** Timestamp of the most recent delivery attempt, or null if none recorded yet. */
    val lastDeliveryAt: Long? = null,
) {

    /**
     * Validates this webhook per the Validation section of design.md:
     * - [name] must not be blank.
     * - [url] must parse as a `http`/`https` URL.
     * - a [headers] entry must not collide (case-insensitively) with the active auth header
     *   name. Duplicate-header-KEY detection (two rows with the same key) does NOT belong here -
     *   [headers] is already a `Map`, which cannot structurally hold duplicate keys by the time
     *   this runs; that check lives earlier, in the editor ViewModel, against the pre-collapse
     *   header rows.
     */
    fun validate(): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        if (name.isBlank()) {
            errors += ValidationError.BlankName
        }
        if (!isWellFormedHttpUrl(url)) {
            errors += ValidationError.MalformedUrl
        }
        if (hasHeaderAuthCollision()) {
            errors += ValidationError.HeaderAuthCollision
        }
        return errors
    }

    private fun hasHeaderAuthCollision(): Boolean {
        val activeAuthHeaderName = auth.activeHeaderName() ?: return false
        return headers.keys.any { it.equals(activeAuthHeaderName, ignoreCase = true) }
    }

    /**
     * Redacts [headers] values AND [auth] (via [WebhookAuth]'s own `toString()`) so a secret
     * pasted directly into a custom header row (e.g. `Authorization: Bearer <token>`) never
     * leaks via the default data-class `toString()`.
     */
    override fun toString(): String = "Webhook(id=$id, name=$name, url=$url, " +
        "headers=${headers.mapValues { "REDACTED" }}, auth=$auth, method=$method, " +
        "queryParams=$queryParams, createdAt=$createdAt, " +
        "lastDeliveryStatus=$lastDeliveryStatus, lastDeliveryAt=$lastDeliveryAt)"

    private companion object {
        fun isWellFormedHttpUrl(url: String): Boolean = try {
            val scheme = URI(url).scheme
            scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
        } catch (expected: URISyntaxException) {
            false
        } catch (expected: IllegalArgumentException) {
            // URI(String) wraps a null-scheme/malformed input as IllegalArgumentException in
            // some JVM implementations, in addition to the documented URISyntaxException.
            false
        }
    }
}

/** Validation failures produced by [Webhook.validate]. */
sealed interface ValidationError {
    data object BlankName : ValidationError
    data object MalformedUrl : ValidationError
    data object HeaderAuthCollision : ValidationError
}

/**
 * Auth mechanism applied to a webhook's outgoing request. `None` sends no extra auth header;
 * `ApiKeyHeader` adds a configurable header (default `X-API-Key`); `BearerToken` adds the
 * standard `Authorization: Bearer <value>` header.
 */
sealed interface WebhookAuth {

    /** The header name this auth mechanism would occupy, or null if it uses none. */
    fun activeHeaderName(): String? = when (this) {
        is None -> null
        is ApiKeyHeader -> headerName
        is BearerToken -> AUTHORIZATION_HEADER
    }

    data object None : WebhookAuth

    data class ApiKeyHeader(val headerName: String = DEFAULT_API_KEY_HEADER_NAME, val value: String) : WebhookAuth {
        /** MUST NOT interpolate [value] directly - see the no-log guarantee in design.md. */
        override fun toString(): String = "ApiKeyHeader(headerName=$headerName, value=REDACTED)"
    }

    data class BearerToken(val value: String) : WebhookAuth {
        /** MUST NOT interpolate [value] directly - see the no-log guarantee in design.md. */
        override fun toString(): String = "BearerToken(value=REDACTED)"
    }

    companion object {
        const val DEFAULT_API_KEY_HEADER_NAME = "X-API-Key"
        const val AUTHORIZATION_HEADER = "Authorization"
    }
}
