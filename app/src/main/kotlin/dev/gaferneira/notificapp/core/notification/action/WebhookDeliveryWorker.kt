package dev.gaferneira.notificapp.core.notification.action

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.network.DeliveryResult
import dev.gaferneira.notificapp.core.network.WebhookDeliveryClient
import dev.gaferneira.notificapp.domain.model.WebhookDelivery
import dev.gaferneira.notificapp.domain.model.WebhookDeliveryStatus
import dev.gaferneira.notificapp.domain.repository.WebhookDeliveryRepository
import dev.gaferneira.notificapp.domain.repository.WebhookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.work.ListenableWorker.Result as WorkResult

/**
 * Attempts one delivery of the `webhook_deliveries` row referenced by [KEY_DELIVERY_ID],
 * classifying the outcome and mapping it to a WorkManager [WorkResult] + the persisted queue +
 * the webhook's tri-state indicator, per design.md's Data Flow table.
 *
 * `runAttemptCount` is NOT the source of truth for the server-retry cap - the persisted row's
 * `attemptCount` is, since a fresh [WebhookDeliveryEnqueuer]-built [androidx.work.WorkRequest] per
 * attempt (design.md's retry-timing decision) resets `runAttemptCount` back to 0 every time.
 * [WorkResult.retry] is reserved exclusively for the network-classified path, where a single
 * long-lived request legitimately waits on the `Constraints(NetworkType.CONNECTED)` gate.
 */
@HiltWorker
@Suppress("LongParameterList") // Hilt worker: assisted (context, params) + 4 collaborators + injected dispatcher, mirrors ProcessNotificationUseCase's precedent
internal class WebhookDeliveryWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val deliveryRepo: WebhookDeliveryRepository,
    private val webhookRepo: WebhookRepository,
    private val deliveryClient: WebhookDeliveryClient,
    private val enqueuer: WebhookDeliveryEnqueuer,
    @Dispatcher(DispatcherType.IO) private val io: CoroutineDispatcher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): WorkResult = withContext(io) {
        val deliveryId = inputData.getString(KEY_DELIVERY_ID)
        if (deliveryId.isNullOrBlank()) {
            Timber.e("WebhookDeliveryWorker started without a delivery id")
            return@withContext WorkResult.failure()
        }

        val delivery = deliveryRepo.getById(deliveryId).getOrNull()
        if (delivery == null) {
            // Row already resolved (delivered/dropped) or never persisted - nothing to do.
            Timber.d("Webhook delivery $deliveryId no longer queued, skipping")
            return@withContext WorkResult.success()
        }

        val webhook = webhookRepo.getWebhook(delivery.webhookId).getOrNull()
        if (webhook == null) {
            Timber.w("Webhook ${delivery.webhookId} no longer exists, dropping delivery $deliveryId")
            deliveryRepo.drop(deliveryId)
            return@withContext WorkResult.failure()
        }

        when (deliveryClient.post(webhook, delivery.payload)) {
            is DeliveryResult.Delivered -> onDelivered(delivery.id, webhook.id)
            DeliveryResult.NetworkError -> onNetworkError(delivery.id)
            is DeliveryResult.ServerError -> onServerError(delivery, webhook.id)
            is DeliveryResult.ClientError -> onClientError(delivery, webhook.id)
        }
    }

    private suspend fun onDelivered(deliveryId: String, webhookId: String): WorkResult {
        deliveryRepo.markDelivered(deliveryId)
        webhookRepo.updateDeliveryStatus(webhookId, WebhookDeliveryStatus.DELIVERED, System.currentTimeMillis())
            .onFailure { Timber.w("Failed to update delivery status for webhook $webhookId") }
        return WorkResult.success()
    }

    private fun onNetworkError(deliveryId: String): WorkResult {
        // Row stays PENDING - the same WorkRequest retries under its original CONNECTED
        // constraint once connectivity returns.
        Timber.d("Network error delivering webhook delivery $deliveryId, will retry")
        return WorkResult.retry()
    }

    private suspend fun onServerError(delivery: WebhookDelivery, webhookId: String): WorkResult {
        val at = System.currentTimeMillis()
        val nextAttempt = delivery.attemptCount + 1
        return if (nextAttempt < MAX_SERVER_ATTEMPTS) {
            enqueuer.enqueue(
                delivery.copy(attemptCount = nextAttempt, lastAttemptAt = at),
                initialDelayMillis = SERVER_RETRY_DELAYS_MS[delivery.attemptCount],
            )
            WorkResult.success()
        } else {
            deliveryRepo.markFailed(delivery.id, FAILURE_TYPE_SERVER, nextAttempt, at)
            webhookRepo.updateDeliveryStatus(webhookId, WebhookDeliveryStatus.UNREACHABLE, at)
                .onFailure { Timber.w("Failed to update delivery status for webhook $webhookId") }
            WorkResult.failure()
        }
    }

    private suspend fun onClientError(delivery: WebhookDelivery, webhookId: String): WorkResult {
        val at = System.currentTimeMillis()
        deliveryRepo.markFailed(delivery.id, FAILURE_TYPE_CLIENT, delivery.attemptCount, at)
        webhookRepo.updateDeliveryStatus(webhookId, WebhookDeliveryStatus.CONFIG_ERROR, at)
            .onFailure { Timber.w("Failed to update delivery status for webhook $webhookId") }
        return WorkResult.failure()
    }

    companion object {
        /** [androidx.work.Data] key carrying the `webhook_deliveries` row id - never the payload itself. */
        const val KEY_DELIVERY_ID = "delivery_id"

        private const val FAILURE_TYPE_SERVER = "SERVER"
        private const val FAILURE_TYPE_CLIENT = "CLIENT"

        private const val MAX_SERVER_ATTEMPTS = 3

        /** Roadmap-locked per-attempt delay schedule for server-classified failures: 1m, 5m, 30m. */
        private val SERVER_RETRY_DELAYS_MS = listOf(1L, 5L, 30L).map { it * 60_000L }
    }
}
