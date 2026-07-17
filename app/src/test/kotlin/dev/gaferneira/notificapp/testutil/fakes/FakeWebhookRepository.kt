package dev.gaferneira.notificapp.testutil.fakes

import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookTestResult
import dev.gaferneira.notificapp.domain.repository.WebhookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deterministic in-memory [WebhookRepository] fake for VM tests, backed by a [MutableStateFlow].
 * Mirrors [FakeSelectedAppRepository]'s conventions.
 */
class FakeWebhookRepository(initial: List<Webhook> = emptyList()) : WebhookRepository {

    private val webhooks = MutableStateFlow(initial)

    /** Result returned by [sendTestPayload]; override in tests to simulate failure paths. */
    var testPayloadResult: Result<WebhookTestResult> = Result.success(WebhookTestResult.Success(200))

    fun currentWebhooks(): List<Webhook> = webhooks.value

    fun setWebhooks(newWebhooks: List<Webhook>) {
        webhooks.value = newWebhooks
    }

    override fun observeWebhooks(): Flow<List<Webhook>> = webhooks.asStateFlow()

    override suspend fun getWebhook(id: String): Result<Webhook?> = Result.success(webhooks.value.find { it.id == id })

    override suspend fun saveWebhook(webhook: Webhook): Result<Unit> {
        webhooks.value = webhooks.value.filterNot { it.id == webhook.id } + webhook
        return Result.success(Unit)
    }

    override suspend fun deleteWebhook(id: String): Result<Unit> {
        webhooks.value = webhooks.value.filterNot { it.id == id }
        return Result.success(Unit)
    }

    override suspend fun sendTestPayload(webhook: Webhook): Result<WebhookTestResult> = testPayloadResult
}
