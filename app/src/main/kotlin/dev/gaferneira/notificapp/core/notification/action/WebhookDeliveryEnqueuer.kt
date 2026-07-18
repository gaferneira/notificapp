package dev.gaferneira.notificapp.core.notification.action

import android.content.Context
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.domain.model.WebhookDelivery
import dev.gaferneira.notificapp.domain.repository.WebhookDeliveryRepository
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Writes a new `webhook_deliveries` row (PENDING) and enqueues the [WebhookDeliveryWorker] that
 * will attempt to deliver it, referenced only by the row id (design.md: WorkManager's `Data`
 * store is uncapped-unencrypted, so the payload itself never goes through `inputData`).
 *
 * Also used by [WebhookRetrySweepWorker] to re-enqueue rows that already exist (app-open drain),
 * so [enqueue] takes the already-built [WebhookDelivery] rather than constructing one itself.
 */
class WebhookDeliveryEnqueuer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deliveryRepository: WebhookDeliveryRepository,
) {

    /**
     * Persists [delivery] (insert-or-replace) then enqueues delivery work for its row id, gated on
     * connectivity. Errors persisting the row are logged and swallowed - `SendWebhookActionExecutor`
     * treats the action as "handed off" regardless (see design.md's executor contract).
     *
     * [initialDelayMillis] lets [WebhookDeliveryWorker] re-enqueue a server-classified failure on
     * the roadmap-locked `[1m, 5m, 30m]` schedule - WorkManager's own `EXPONENTIAL` backoff from a
     * 1-minute base can't express that curve (design.md's retry-timing decision).
     */
    suspend fun enqueue(delivery: WebhookDelivery, initialDelayMillis: Long = 0L) {
        deliveryRepository.enqueue(delivery)
            .onSuccess { enqueueWork(delivery.id, initialDelayMillis) }
            .onFailure { e -> Timber.e(e, "Failed to enqueue webhook delivery ${delivery.id}") }
    }

    private fun enqueueWork(deliveryId: String, initialDelayMillis: Long) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<WebhookDeliveryWorker>()
            .setInputData(workDataOf(WebhookDeliveryWorker.KEY_DELIVERY_ID to deliveryId))
            .setConstraints(constraints)
            .setInitialDelay(initialDelayMillis, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
