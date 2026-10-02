package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.SEND_REPLY_TEMPLATE_KEY
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for [NotificationReplyActionExecutor] (BETA `SEND_REPLY`) per its Testing
 * requirements: a missing `sbnKey` (e.g. the re-evaluate path) is SKIPPED without ever calling the
 * controller; a blank/unset template is SKIPPED; a disconnected listener is SKIPPED; the common
 * "app has no reply action" case (`controller.reply` returns `false`) is SKIPPED, not FAILED;
 * `true` yields SUCCESS with the resolved template text; and an exception yields FAILED.
 */
class NotificationReplyActionExecutorTest {

    @Test
    fun `missing sbnKey yields SKIPPED and never calls the controller`() = runTest {
        val controller = mockk<SystemNotificationController>(relaxed = true)
        val holder = SystemNotificationControllerHolder().apply { set(controller) }
        val executor = NotificationReplyActionExecutor(holder)
        val notification = createTestNotification(sbnKey = null)
        val action = createTestAction(type = ActionType.SEND_REPLY, config = mapOf(SEND_REPLY_TEMPLATE_KEY to "Hello"))

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.SKIPPED
        verify(exactly = 0) { controller.reply(any(), any()) }
    }

    @Test
    fun `blank template yields SKIPPED and never calls the controller`() = runTest {
        val controller = mockk<SystemNotificationController>(relaxed = true)
        val holder = SystemNotificationControllerHolder().apply { set(controller) }
        val executor = NotificationReplyActionExecutor(holder)
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = createTestAction(type = ActionType.SEND_REPLY, config = mapOf(SEND_REPLY_TEMPLATE_KEY to "   "))

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.SKIPPED
        verify(exactly = 0) { controller.reply(any(), any()) }
    }

    @Test
    fun `unset template yields SKIPPED`() = runTest {
        val holder = SystemNotificationControllerHolder()
        val executor = NotificationReplyActionExecutor(holder)
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = createTestAction(type = ActionType.SEND_REPLY, config = emptyMap())

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.SKIPPED
    }

    @Test
    fun `disconnected controller yields SKIPPED`() = runTest {
        // Given: a holder with no controller set (listener disconnected)
        val holder = SystemNotificationControllerHolder()
        val executor = NotificationReplyActionExecutor(holder)
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = createTestAction(type = ActionType.SEND_REPLY, config = mapOf(SEND_REPLY_TEMPLATE_KEY to "Hello"))

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.SKIPPED
    }

    @Test
    fun `controller reporting no reply action yields SKIPPED, not FAILED`() = runTest {
        // Given: a connected controller whose reply() reports the app has no RemoteInput action
        val controller = mockk<SystemNotificationController>()
        every { controller.reply(any(), any()) } returns false
        val holder = SystemNotificationControllerHolder().apply { set(controller) }
        val executor = NotificationReplyActionExecutor(holder)
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = createTestAction(type = ActionType.SEND_REPLY, config = mapOf(SEND_REPLY_TEMPLATE_KEY to "Hello"))

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.SKIPPED
    }

    @Test
    fun `template placeholders resolve and are passed to the controller, yielding SUCCESS`() = runTest {
        // Given: a connected controller that successfully dispatches the reply
        val controller = mockk<SystemNotificationController>()
        every { controller.reply(any(), any()) } returns true
        val holder = SystemNotificationControllerHolder().apply { set(controller) }
        val executor = NotificationReplyActionExecutor(holder)
        val notification = createTestNotification(sbnKey = "sbn-key", appName = "Bank App")
        val action = createTestAction(
            type = ActionType.SEND_REPLY,
            config = mapOf(SEND_REPLY_TEMPLATE_KEY to "Got {{field.amount}} from {{field.sender}} via {{app_name}}"),
        )

        val outcome = executor.execute(notification, action, mapOf("amount" to "45 euros", "sender" to "Maria"))

        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 1) { controller.reply("sbn-key", "Got 45 euros from Maria via Bank App") }
    }

    @Test
    fun `controller throwing yields FAILED`() = runTest {
        // Given: a connected controller whose reply() throws
        val controller = mockk<SystemNotificationController>()
        every { controller.reply(any(), any()) } throws IllegalStateException("dispatch boom")
        val holder = SystemNotificationControllerHolder().apply { set(controller) }
        val executor = NotificationReplyActionExecutor(holder)
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = createTestAction(type = ActionType.SEND_REPLY, config = mapOf(SEND_REPLY_TEMPLATE_KEY to "Hello"))

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.FAILED
    }
}
