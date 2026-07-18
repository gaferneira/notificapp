package dev.gaferneira.notificapp.testutil.fakes

import dev.gaferneira.notificapp.domain.model.WebhookDelivery
import dev.gaferneira.notificapp.domain.repository.WebhookDeliveryRepository

/**
 * Deterministic in-memory [WebhookDeliveryRepository] fake for VM/worker tests, mirroring
 * [FakeWebhookRepository]'s conventions.
 */
class FakeWebhookDeliveryRepository(initial: List<WebhookDelivery> = emptyList()) : WebhookDeliveryRepository {

    private val deliveries = initial.associateBy { it.id }.toMutableMap()

    fun currentDeliveries(): List<WebhookDelivery> = deliveries.values.toList()

    override suspend fun enqueue(delivery: WebhookDelivery): Result<Unit> {
        deliveries[delivery.id] = delivery
        return Result.success(Unit)
    }

    override suspend fun getById(id: String): Result<WebhookDelivery?> = Result.success(deliveries[id])

    override suspend fun markDelivered(id: String): Result<Unit> {
        deliveries.remove(id)
        return Result.success(Unit)
    }

    override suspend fun markFailed(id: String, failureType: String, attemptCount: Int, at: Long): Result<Unit> {
        deliveries[id]?.let { deliveries[id] = it.copy(failureType = failureType, attemptCount = attemptCount, lastAttemptAt = at) }
        return Result.success(Unit)
    }

    override suspend fun drop(id: String): Result<Unit> {
        deliveries.remove(id)
        return Result.success(Unit)
    }

    override suspend fun pendingFailures(): Result<List<WebhookDelivery>> = Result.success(deliveries.values.filter { it.failureType != null })
}
