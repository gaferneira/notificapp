package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.common.Failure
import dev.gaferneira.notificapp.core.common.asResult
import dev.gaferneira.notificapp.core.common.toFailureResult
import dev.gaferneira.notificapp.core.data.local.dao.WebhookDao
import dev.gaferneira.notificapp.core.data.local.mapper.WebhookMapper
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.network.WebhookTestClient
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookDeliveryStatus
import dev.gaferneira.notificapp.domain.model.WebhookTestResult
import dev.gaferneira.notificapp.domain.repository.WebhookRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Implementation of [WebhookRepository]. Mirrors [SelectedAppRepositoryImpl]'s conventions:
 * `Result<T>` (ADR 006), injected IO dispatcher (ADR 008). `saveWebhook` re-runs
 * [Webhook.validate] as a defense-in-depth guard so any caller - not just the editor ViewModel -
 * is protected (see design.md's Validation section).
 */
internal class WebhookRepositoryImpl @Inject constructor(
    private val dao: WebhookDao,
    private val testClient: WebhookTestClient,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : WebhookRepository {

    override fun observeWebhooks(): Flow<List<Webhook>> = dao.getAll()
        .map { entities -> entities.map { WebhookMapper.toDomain(it) } }
        .flowOn(ioDispatcher)

    override suspend fun getWebhook(id: String): Result<Webhook?> = withContext(ioDispatcher) {
        dbCatching("Failed to get webhook: $id") {
            dao.getById(id)?.let { WebhookMapper.toDomain(it) }
        }
    }

    override suspend fun saveWebhook(webhook: Webhook): Result<Unit> = withContext(ioDispatcher) {
        val validationErrors = webhook.validate()
        if (validationErrors.isNotEmpty()) {
            Timber.w("Rejected saveWebhook for ${webhook.id}: $validationErrors")
            return@withContext Failure.ApplicationException("Webhook failed validation: $validationErrors").asResult()
        }
        dbCatching("Failed to save webhook: ${webhook.id}") {
            dao.insert(WebhookMapper.toEntity(webhook))
        }
    }

    override suspend fun deleteWebhook(id: String): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to delete webhook: $id") {
            dao.deleteById(id)
        }
    }

    override suspend fun sendTestPayload(webhook: Webhook): Result<WebhookTestResult> = withContext(ioDispatcher) {
        dbCatching("Unexpected error sending test payload for webhook: ${webhook.id}") {
            testClient.post(webhook)
        }
    }

    override suspend fun updateDeliveryStatus(id: String, status: WebhookDeliveryStatus, at: Long): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to update delivery status for webhook: $id") {
            dao.updateDeliveryStatus(id, status.name, at)
        }.fold(
            onSuccess = { rowsUpdated ->
                if (rowsUpdated == 0) {
                    Timber.w("Rejected updateDeliveryStatus for unknown webhook: $id")
                    Failure.ApplicationException("Webhook not found: $id").asResult()
                } else {
                    Result.success(Unit)
                }
            },
            onFailure = { it.toFailureResult() },
        )
    }
}
