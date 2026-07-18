package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookDeliveryStatus
import dev.gaferneira.notificapp.domain.model.WebhookTestResult
import kotlinx.coroutines.flow.Flow

/**
 * Repository for CRUD on user-defined [Webhook]s and sending a one-shot test payload.
 */
interface WebhookRepository {
    fun observeWebhooks(): Flow<List<Webhook>>
    suspend fun getWebhook(id: String): Result<Webhook?>
    suspend fun saveWebhook(webhook: Webhook): Result<Unit>
    suspend fun deleteWebhook(id: String): Result<Unit>
    suspend fun sendTestPayload(webhook: Webhook): Result<WebhookTestResult>

    /**
     * Updates [Webhook.lastDeliveryStatus]/[Webhook.lastDeliveryAt] after a real
     * `SEND_WEBHOOK` delivery attempt (Phase 4 PR2's `WebhookDeliveryWorker`). Distinct from
     * [saveWebhook] so a delivery-outcome update never re-runs [Webhook.validate] against
     * possibly-stale in-memory config.
     */
    suspend fun updateDeliveryStatus(id: String, status: WebhookDeliveryStatus, at: Long): Result<Unit>
}
