package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.WebhookDelivery

/**
 * Repository for the `webhook_deliveries` queue/journal: one row per unresolved `SEND_WEBHOOK`
 * delivery. Success deletes the row ([markDelivered]); a terminal failure updates it in place
 * ([markFailed]) so [pendingFailures] can drain it on next app open.
 */
interface WebhookDeliveryRepository {

    /** Persists a new (or replaces an existing) delivery row. */
    suspend fun enqueue(delivery: WebhookDelivery): Result<Unit>

    /** Loads a single row by id, or null if it no longer exists (e.g. already resolved). */
    suspend fun getById(id: String): Result<WebhookDelivery?>

    /** Delivery succeeded (2xx): removes the row - it's fully resolved. */
    suspend fun markDelivered(id: String): Result<Unit>

    /** Delivery terminally failed: records [failureType] (`NETWORK`/`SERVER`/`CLIENT`), the new [attemptCount], and [at]. */
    suspend fun markFailed(id: String, failureType: String, attemptCount: Int, at: Long): Result<Unit>

    /**
     * Removes the row without recording a failure: there is no longer a [dev.gaferneira.notificapp.domain.model.Webhook]
     * to ever retry against (its target was deleted), so nothing about this delivery should survive
     * to be picked up by [pendingFailures] again.
     */
    suspend fun drop(id: String): Result<Unit>

    /** All rows carrying a terminal failure, for the app-open retry sweep. */
    suspend fun pendingFailures(): Result<List<WebhookDelivery>>
}
