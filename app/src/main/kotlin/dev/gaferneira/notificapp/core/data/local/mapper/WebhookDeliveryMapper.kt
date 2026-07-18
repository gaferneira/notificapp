package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.WebhookDeliveryEntity
import dev.gaferneira.notificapp.domain.model.WebhookDelivery

/**
 * Mapper functions for converting between [WebhookDelivery] domain models and
 * [WebhookDeliveryEntity] database entities.
 */
internal object WebhookDeliveryMapper {

    fun toEntity(domain: WebhookDelivery): WebhookDeliveryEntity = WebhookDeliveryEntity(
        id = domain.id,
        webhookId = domain.webhookId,
        payload = domain.payload,
        failureType = domain.failureType,
        attemptCount = domain.attemptCount,
        createdAt = domain.createdAt,
        lastAttemptAt = domain.lastAttemptAt,
    )

    fun toDomain(entity: WebhookDeliveryEntity): WebhookDelivery = WebhookDelivery(
        id = entity.id,
        webhookId = entity.webhookId,
        payload = entity.payload,
        failureType = entity.failureType,
        attemptCount = entity.attemptCount,
        createdAt = entity.createdAt,
        lastAttemptAt = entity.lastAttemptAt,
    )
}
