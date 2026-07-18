package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.WEBHOOK_ID_KEY
import dev.gaferneira.notificapp.domain.model.WebhookDelivery
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SendWebhookActionExecutor] per design.md's Testing Strategy: missing
 * `webhook_id` -> SKIPPED; happy path builds a payload and enqueues exactly once.
 */
class SendWebhookActionExecutorTest {

    private val payloadBuilder: WebhookPayloadBuilder = mockk()
    private val enqueuer: WebhookDeliveryEnqueuer = mockk()
    private val executor = SendWebhookActionExecutor(payloadBuilder, enqueuer)

    @Test
    fun `missing webhook_id yields SKIPPED and never builds a payload or enqueues`() = runTest {
        val notification = createTestNotification()
        val action = createTestAction(type = ActionType.SEND_WEBHOOK, config = emptyMap())

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.SKIPPED
        coVerify(exactly = 0) { enqueuer.enqueue(any(), any()) }
    }

    @Test
    fun `happy path builds the payload and enqueues it once, returning SUCCESS`() = runTest {
        val notification = createTestNotification()
        val action = createTestAction(type = ActionType.SEND_WEBHOOK, config = mapOf(WEBHOOK_ID_KEY to "wh-1"))
        every { payloadBuilder.build(notification, action, emptyMap()) } returns """{"title":"hi"}"""
        coEvery { enqueuer.enqueue(any(), any()) } returns Unit

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.SUCCESS
        coVerify(exactly = 1) {
            enqueuer.enqueue(
                match<WebhookDelivery> { it.webhookId == "wh-1" && it.payload == """{"title":"hi"}""" },
                any(),
            )
        }
    }
}
