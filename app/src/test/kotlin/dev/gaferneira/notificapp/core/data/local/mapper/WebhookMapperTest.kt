package dev.gaferneira.notificapp.core.data.local.mapper

import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookAuth
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WebhookMapperTest {

    @Test
    fun `round-trips a webhook with no auth`() {
        val webhook = Webhook(
            id = "wh-1",
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            headers = mapOf("X-Custom" to "value"),
            auth = WebhookAuth.None,
            createdAt = 1_000L,
        )

        val roundTripped = WebhookMapper.toDomain(WebhookMapper.toEntity(webhook))

        roundTripped shouldBe webhook
    }

    @Test
    fun `round-trips a webhook with default api key header name`() {
        val webhook = Webhook(
            id = "wh-2",
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            auth = WebhookAuth.ApiKeyHeader(value = "secret"),
        )

        val roundTripped = WebhookMapper.toDomain(WebhookMapper.toEntity(webhook))

        roundTripped shouldBe webhook
        (roundTripped.auth as WebhookAuth.ApiKeyHeader).headerName shouldBe "X-API-Key"
    }

    @Test
    fun `round-trips a webhook with a custom api key header name`() {
        val webhook = Webhook(
            id = "wh-3",
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            auth = WebhookAuth.ApiKeyHeader(headerName = "X-Custom-Key", value = "secret"),
        )

        val roundTripped = WebhookMapper.toDomain(WebhookMapper.toEntity(webhook))

        roundTripped shouldBe webhook
        (roundTripped.auth as WebhookAuth.ApiKeyHeader).headerName shouldBe "X-Custom-Key"
    }

    @Test
    fun `round-trips a webhook with bearer auth`() {
        val webhook = Webhook(
            id = "wh-4",
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            auth = WebhookAuth.BearerToken(value = "token-123"),
        )

        val roundTripped = WebhookMapper.toDomain(WebhookMapper.toEntity(webhook))

        roundTripped shouldBe webhook
        (roundTripped.auth as WebhookAuth.BearerToken).value shouldBe "token-123"
    }

    @Test
    fun `headers map is preserved through the round trip`() {
        val webhook = Webhook(
            id = "wh-5",
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            headers = mapOf("X-One" to "1", "X-Two" to "2"),
        )

        val roundTripped = WebhookMapper.toDomain(WebhookMapper.toEntity(webhook))

        roundTripped.headers shouldBe mapOf("X-One" to "1", "X-Two" to "2")
    }
}
