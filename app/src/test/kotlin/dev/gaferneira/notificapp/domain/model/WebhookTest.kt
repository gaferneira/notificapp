package dev.gaferneira.notificapp.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class WebhookTest {

    @Test
    fun `valid webhook passes validation`() {
        val webhook = Webhook(name = "Home Assistant", url = "https://ha.local/api/hook")

        webhook.validate() shouldBe emptyList()
    }

    @Test
    fun `blank name is rejected`() {
        val webhook = Webhook(name = "  ", url = "https://ha.local/api/hook")

        webhook.validate() shouldBe listOf(ValidationError.BlankName)
    }

    @Test
    fun `malformed url is rejected`() {
        val webhook = Webhook(name = "Home Assistant", url = "not-a-url")

        webhook.validate() shouldBe listOf(ValidationError.MalformedUrl)
    }

    @Test
    fun `url with unsupported scheme is rejected`() {
        val webhook = Webhook(name = "Home Assistant", url = "ftp://ha.local/api/hook")

        webhook.validate() shouldBe listOf(ValidationError.MalformedUrl)
    }

    @Test
    fun `http scheme is accepted for LAN self-hosted targets`() {
        val webhook = Webhook(name = "Home Assistant", url = "http://192.168.1.5:8123/api/hook")

        webhook.validate() shouldBe emptyList()
    }

    @Test
    fun `blank name and malformed url are both reported`() {
        val webhook = Webhook(name = "", url = "nope")

        webhook.validate() shouldBe listOf(ValidationError.BlankName, ValidationError.MalformedUrl)
    }

    @Test
    fun `header colliding case-insensitively with active api key auth header name is rejected`() {
        val webhook = Webhook(
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            headers = mapOf("x-api-key" to "duplicate"),
            auth = WebhookAuth.ApiKeyHeader(headerName = "X-API-Key", value = "secret"),
        )

        webhook.validate() shouldBe listOf(ValidationError.HeaderAuthCollision)
    }

    @Test
    fun `header colliding with bearer auth Authorization header is rejected`() {
        val webhook = Webhook(
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            headers = mapOf("authorization" to "duplicate"),
            auth = WebhookAuth.BearerToken(value = "token"),
        )

        webhook.validate() shouldBe listOf(ValidationError.HeaderAuthCollision)
    }

    @Test
    fun `header not colliding with auth is accepted`() {
        val webhook = Webhook(
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            headers = mapOf("X-Custom" to "value"),
            auth = WebhookAuth.ApiKeyHeader(headerName = "X-API-Key", value = "secret"),
        )

        webhook.validate() shouldBe emptyList()
    }

    @Test
    fun `toString redacts headers and auth value`() {
        val webhook = Webhook(
            name = "Home Assistant",
            url = "https://ha.local/api/hook",
            headers = mapOf("X-Custom" to "super-secret-header-value"),
            auth = WebhookAuth.BearerToken(value = "super-secret-token"),
        )

        val text = webhook.toString()

        (text.contains("super-secret-header-value")) shouldBe false
        (text.contains("super-secret-token")) shouldBe false
        (text.contains("REDACTED")) shouldBe true
    }

    @Test
    fun `ApiKeyHeader toString redacts value`() {
        val auth = WebhookAuth.ApiKeyHeader(headerName = "X-API-Key", value = "super-secret-token")

        auth.toString().contains("super-secret-token") shouldBe false
        auth.toString().contains("REDACTED") shouldBe true
    }

    @Test
    fun `BearerToken toString redacts value`() {
        val auth = WebhookAuth.BearerToken(value = "super-secret-token")

        auth.toString().contains("super-secret-token") shouldBe false
        auth.toString().contains("REDACTED") shouldBe true
    }
}
