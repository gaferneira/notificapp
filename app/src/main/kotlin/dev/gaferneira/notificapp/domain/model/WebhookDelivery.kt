package dev.gaferneira.notificapp.domain.model

import java.util.UUID

/**
 * A queued `SEND_WEBHOOK` delivery job: the JSON [payload] to POST to [webhookId], plus enough
 * retry bookkeeping for [dev.gaferneira.notificapp.domain.repository.WebhookDeliveryRepository]
 * and the WorkManager worker (Phase 4 PR2) to classify and re-enqueue it. One row exists per
 * unresolved delivery; success deletes the row (see [dev.gaferneira.notificapp.domain.repository.WebhookDeliveryRepository.markDelivered]),
 * so the underlying table naturally drains.
 */
data class WebhookDelivery(
    val id: String = UUID.randomUUID().toString(),
    val webhookId: String,
    val payload: String,
    /** `NETWORK`/`SERVER`/`CLIENT`, or null while still `PENDING` (no terminal failure yet). */
    val failureType: String? = null,
    val attemptCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAttemptAt: Long? = null,
) {
    /**
     * Redacts [payload] (may carry extracted-field secrets) so a future maintainer logging this
     * value never leaks plaintext via the default data-class `toString()` - see design.md's
     * no-log guarantee.
     */
    override fun toString(): String = "WebhookDelivery(id=$id, webhookId=$webhookId, payload=REDACTED, " +
        "failureType=$failureType, attemptCount=$attemptCount, createdAt=$createdAt, " +
        "lastAttemptAt=$lastAttemptAt)"
}
