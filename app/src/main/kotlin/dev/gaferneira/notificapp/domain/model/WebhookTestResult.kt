package dev.gaferneira.notificapp.domain.model

/**
 * Outcome of sending a webhook test payload. Distinguishes network failure, server-side failure,
 * and a 2xx response with a body that fails to parse as JSON from a genuine success - see
 * design.md's "Send Test Payload" data flow for why each case exists.
 */
sealed interface WebhookTestResult {
    data class Success(val httpCode: Int) : WebhookTestResult

    /** 2xx status, non-empty body that fails to parse as JSON. */
    data object MalformedBody : WebhookTestResult

    /** Non-2xx status. */
    data class ServerError(val httpCode: Int) : WebhookTestResult

    /** IOException, e.g. no connection/timeout. */
    data object NetworkError : WebhookTestResult

    /** IllegalArgumentException from `Headers.Builder.add()` (disallowed characters in a header value). */
    data object InvalidHeaderValue : WebhookTestResult

    /** IllegalArgumentException from `Request.Builder.url()` (URL rejected by OkHttp's stricter parser). */
    data object InvalidUrl : WebhookTestResult
}
