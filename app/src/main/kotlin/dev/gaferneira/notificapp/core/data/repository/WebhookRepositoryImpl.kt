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
        try {
            val entity = dao.getById(id)
            Result.success(entity?.let { WebhookMapper.toDomain(it) })
        } catch (e: Exception) {
            Timber.e(e, "Failed to get webhook: $id")
            e.toFailureResult()
        }
    }

    override suspend fun saveWebhook(webhook: Webhook): Result<Unit> = withContext(ioDispatcher) {
        val validationErrors = webhook.validate()
        if (validationErrors.isNotEmpty()) {
            Timber.w("Rejected saveWebhook for ${webhook.id}: $validationErrors")
            return@withContext Failure.ApplicationException("Webhook failed validation: $validationErrors").asResult()
        }
        try {
            dao.insert(WebhookMapper.toEntity(webhook))
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to save webhook: ${webhook.id}")
            e.toFailureResult()
        }
    }

    override suspend fun deleteWebhook(id: String): Result<Unit> = withContext(ioDispatcher) {
        try {
            dao.deleteById(id)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete webhook: $id")
            e.toFailureResult()
        }
    }

    override suspend fun sendTestPayload(webhook: Webhook): Result<WebhookTestResult> = withContext(ioDispatcher) {
        try {
            Result.success(testClient.post(webhook))
        } catch (e: Exception) {
            Timber.e(e, "Unexpected error sending test payload for webhook: ${webhook.id}")
            e.toFailureResult()
        }
    }
}
