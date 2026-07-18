package dev.gaferneira.notificapp.domain.model

/**
 * Coarse, at-a-glance delivery outcome for a [Webhook]'s most recent send attempt, surfaced as
 * the tri-state indicator on the webhook list (Rule Editor UI, Phase 4 PR2).
 */
enum class WebhookDeliveryStatus {
    /** No delivery attempt recorded yet - e.g. a webhook never referenced by any action. */
    UNKNOWN,

    /** The most recent attempt reached the endpoint and got a 2xx response. */
    DELIVERED,

    /** The most recent attempt got a non-retriable 4xx response (bad URL/auth/payload). */
    CONFIG_ERROR,

    /** The most recent attempt exhausted retries against a 5xx/network-classified failure. */
    UNREACHABLE,
}
