package dev.gaferneira.notificapp.core.network

/**
 * Classified outcome of a real `SEND_WEBHOOK` delivery attempt (Phase 4 PR2), returned by
 * [WebhookDeliveryClient.post]. Distinct from `WebhookTestResult` (PR1's one-shot test payload
 * flow): this classification drives `WebhookDeliveryWorker`'s retry/backoff decision, per
 * design.md's Data Flow table.
 */
sealed interface DeliveryResult {
    /** 2xx response. */
    data class Delivered(val httpCode: Int) : DeliveryResult

    /** [java.io.IOException] (connect/read/write timeout, DNS failure, etc.) - no HTTP response at all. */
    data object NetworkError : DeliveryResult

    /** 5xx, 408 (Request Timeout), or 429 (Too Many Requests) - retriable. */
    data class ServerError(val httpCode: Int) : DeliveryResult

    /** Any other 4xx (bad URL/auth/payload) - not retriable, fail fast. */
    data class ClientError(val httpCode: Int) : DeliveryResult
}
