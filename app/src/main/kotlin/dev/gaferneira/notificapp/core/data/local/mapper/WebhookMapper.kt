package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.core.data.local.entity.WebhookEntity
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookAuth

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
            createdAt = domain.createdAt,
        )
    }

    fun toDomain(entity: WebhookEntity): Webhook = Webhook(
        id = entity.id,
        name = entity.name,
        url = entity.url,
        headers = entity.headers,
        auth = toAuth(entity),
        createdAt = entity.createdAt,
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
