package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.WebhookEntity
import dev.gaferneira.notificapp.domain.model.HttpMethod
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookAuth
import dev.gaferneira.notificapp.domain.model.WebhookDeliveryStatus

/**
 * Mapper functions for converting between [Webhook] domain models and [WebhookEntity] database
 * entities, flattening/reconstructing the sealed [WebhookAuth].
 */
internal object WebhookMapper {

    private const val AUTH_TYPE_NONE = "NONE"
    private const val AUTH_TYPE_API_KEY = "API_KEY"
    private const val AUTH_TYPE_BEARER = "BEARER"

    fun toEntity(domain: Webhook): WebhookEntity {
        val (authType, authHeaderName, authValue) = when (val auth = domain.auth) {
            is WebhookAuth.None -> Triple(AUTH_TYPE_NONE, null, null)
            is WebhookAuth.ApiKeyHeader -> Triple(AUTH_TYPE_API_KEY, auth.headerName, auth.value)
            is WebhookAuth.BearerToken -> Triple(AUTH_TYPE_BEARER, null, auth.value)
        }
        return WebhookEntity(
            id = domain.id,
            name = domain.name,
            url = domain.url,
            headers = domain.headers,
            authType = authType,
            authHeaderName = authHeaderName,
            authValue = authValue,
            method = domain.method.name,
            queryParams = domain.queryParams,
            createdAt = domain.createdAt,
            lastDeliveryStatus = domain.lastDeliveryStatus.name,
            lastDeliveryAt = domain.lastDeliveryAt,
        )
    }

    fun toDomain(entity: WebhookEntity): Webhook = Webhook(
        id = entity.id,
        name = entity.name,
        url = entity.url,
        headers = entity.headers,
        auth = toAuth(entity),
        method = runCatching { HttpMethod.valueOf(entity.method) }
            .getOrDefault(HttpMethod.POST),
        queryParams = entity.queryParams,
        createdAt = entity.createdAt,
        lastDeliveryStatus = runCatching { WebhookDeliveryStatus.valueOf(entity.lastDeliveryStatus) }
            .getOrDefault(WebhookDeliveryStatus.UNKNOWN),
        lastDeliveryAt = entity.lastDeliveryAt,
    )

    private fun toAuth(entity: WebhookEntity): WebhookAuth = when (entity.authType) {
        AUTH_TYPE_API_KEY -> WebhookAuth.ApiKeyHeader(
            headerName = entity.authHeaderName ?: WebhookAuth.DEFAULT_API_KEY_HEADER_NAME,
            value = entity.authValue.orEmpty(),
        )
        AUTH_TYPE_BEARER -> WebhookAuth.BearerToken(value = entity.authValue.orEmpty())
        else -> WebhookAuth.None
    }
}
