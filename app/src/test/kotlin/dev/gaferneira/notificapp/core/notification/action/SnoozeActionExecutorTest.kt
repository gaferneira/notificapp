package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.SNOOZE_DURATION_MINUTES_KEY
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SnoozeActionExecutorTest {

    @Test
    fun `disconnected controller yields SKIPPED`() = runTest {
        // Given: a holder with no controller set
        val holder = SystemNotificationControllerHolder()
        val executor = SnoozeActionExecutor(holder)
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = createTestAction(type = ActionType.SNOOZE_NOTIFICATION)

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
        val executor = SnoozeActionExecutor(holder)
        val notification = createTestNotification(sbnKey = null)
        val action = createTestAction(type = ActionType.SNOOZE_NOTIFICATION)

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the outcome is SKIPPED and the controller is never invoked
        outcome shouldBe ActionOutcome.SKIPPED
        verify(exactly = 0) { controller.snooze(any(), any()) }
    }

    @Test
    fun `connected controller with a valid key snoozes with duration in ms and yields SUCCESS`() = runTest {
        // Given: a connected controller and a notification with an sbnKey, and a 20-minute snooze config
        val controller = mockk<SystemNotificationController>(relaxed = true)
        val holder = SystemNotificationControllerHolder().apply { set(controller) }
        val executor = SnoozeActionExecutor(holder)
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = createTestAction(
            type = ActionType.SNOOZE_NOTIFICATION,
            config = mapOf(SNOOZE_DURATION_MINUTES_KEY to "20"),
        )

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the controller snoozes with duration converted to ms (20 * 60_000) and outcome is SUCCESS
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 1) { controller.snooze("sbn-key", 20 * 60_000L) }
    }
}
