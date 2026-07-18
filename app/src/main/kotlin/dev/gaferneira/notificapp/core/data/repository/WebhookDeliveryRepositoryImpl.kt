package dev.gaferneira.notificapp.core.data.repository

import dev.gaferneira.notificapp.core.data.local.dao.WebhookDeliveryDao
import dev.gaferneira.notificapp.core.data.local.mapper.WebhookDeliveryMapper
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.domain.model.WebhookDelivery
import dev.gaferneira.notificapp.domain.repository.WebhookDeliveryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implementation of [WebhookDeliveryRepository]. Mirrors [RuleRepositoryImpl]'s conventions:
 * `Result<T>` (ADR 006) via the shared [dbCatching] boundary, injected IO dispatcher (ADR 008).
 */
internal class WebhookDeliveryRepositoryImpl @Inject constructor(
    private val dao: WebhookDeliveryDao,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : WebhookDeliveryRepository {

    override suspend fun enqueue(delivery: WebhookDelivery): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to enqueue webhook delivery: ${delivery.id}") {
            dao.insert(WebhookDeliveryMapper.toEntity(delivery))
        }
    }

    override suspend fun getById(id: String): Result<WebhookDelivery?> = withContext(ioDispatcher) {
        dbCatching("Failed to load webhook delivery: $id") {
            dao.getById(id)?.let { WebhookDeliveryMapper.toDomain(it) }
        }
    }

    override suspend fun markDelivered(id: String): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to mark webhook delivery delivered: $id") {
            dao.deleteById(id)
        }
    }

    override suspend fun markFailed(id: String, failureType: String, attemptCount: Int, at: Long): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to mark webhook delivery failed: $id") {
            dao.updateFailure(id, failureType, attemptCount, at)
        }
    }

    override suspend fun drop(id: String): Result<Unit> = withContext(ioDispatcher) {
        dbCatching("Failed to drop webhook delivery: $id") {
            dao.deleteById(id)
        }
    }

    override suspend fun pendingFailures(): Result<List<WebhookDelivery>> = withContext(ioDispatcher) {
        dbCatching("Failed to load pending webhook delivery failures") {
            dao.getAllUnresolved().map { WebhookDeliveryMapper.toDomain(it) }
        }
    }
}
