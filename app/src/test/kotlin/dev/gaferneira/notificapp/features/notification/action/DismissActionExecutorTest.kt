package dev.gaferneira.notificapp.features.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class DismissActionExecutorTest {

    @Test
    fun `disconnected controller yields SKIPPED`() = runTest {
        // Given: a holder with no controller set
        val holder = SystemNotificationControllerHolder()
        val executor = DismissActionExecutor(holder)
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = createTestAction(type = ActionType.DISMISS_NOTIFICATION)

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the outcome is SKIPPED
        outcome shouldBe ActionOutcome.SKIPPED
    }

    @Test
    fun `notification without sbnKey yields SKIPPED`() = runTest {
        // Given: a connected controller but a notification without an sbnKey
        val controller = mockk<SystemNotificationController>(relaxed = true)
        val holder = SystemNotificationControllerHolder().apply { set(controller) }
        val executor = DismissActionExecutor(holder)
        val notification = createTestNotification(sbnKey = null)
        val action = createTestAction(type = ActionType.DISMISS_NOTIFICATION)

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the outcome is SKIPPED and the controller is never invoked
        outcome shouldBe ActionOutcome.SKIPPED
        verify(exactly = 0) { controller.cancel(any()) }
    }

    @Test
    fun `connected controller with a valid key cancels the notification and yields SUCCESS`() = runTest {
        // Given: a connected controller and a notification with an sbnKey
        val controller = mockk<SystemNotificationController>(relaxed = true)
        val holder = SystemNotificationControllerHolder().apply { set(controller) }
        val executor = DismissActionExecutor(holder)
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = createTestAction(type = ActionType.DISMISS_NOTIFICATION)

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the controller cancels the notification and the outcome is SUCCESS
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 1) { controller.cancel("sbn-key") }
    }
}
