package dev.gaferneira.notificapp.core.notification.action

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.repository.WebhookDeliveryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import timber.log.Timber
import androidx.work.ListenableWorker.Result as WorkResult

/**
 * Drains [WebhookDeliveryRepository.pendingFailures] on app open, re-enqueuing each row for
 * another delivery attempt (reset to `PENDING`, no delay) so unresolved failures survive process
 * death instead of being stuck forever - see design.md's Data Flow table and `MyApplication`'s
 * guarded once-per-process enqueue.
 */
@HiltWorker
class WebhookRetrySweepWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val deliveryRepo: WebhookDeliveryRepository,
    private val enqueuer: WebhookDeliveryEnqueuer,
    @Dispatcher(DispatcherType.IO) private val io: CoroutineDispatcher,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): WorkResult = withContext(io) {
        deliveryRepo.pendingFailures().fold(
            onSuccess = { failures ->
                failures.forEach { delivery -> enqueuer.enqueue(delivery.copy(failureType = null)) }
                Timber.d("Re-enqueued ${failures.size} pending webhook delivery failure(s)")
                WorkResult.success()
            },
            onFailure = {
                Timber.e("Failed to load pending webhook delivery failures for retry sweep")
                WorkResult.failure()
            },
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "webhook_retry_sweep"
    }
}
