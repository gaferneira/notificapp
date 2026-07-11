package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.SNOOZE_DURATION_MINUTES_KEY
import dev.gaferneira.notificapp.domain.model.SNOOZE_MODE_KEY
import dev.gaferneira.notificapp.domain.model.SnoozeMode
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

private class FixedTimeProvider(private val fixed: LocalDateTime) : CurrentTimeProvider {
    override fun now(): LocalDateTime = fixed
}

class SnoozeActionExecutorTest {

    private fun executor(
        controller: SystemNotificationController? = null,
        now: LocalDateTime = LocalDateTime.of(2026, 7, 11, 9, 30),
    ) = SnoozeActionExecutor(
        controllerHolder = SystemNotificationControllerHolder().apply { set(controller) },
        timeProvider = FixedTimeProvider(now),
        releaseTracker = SnoozeReleaseTracker(),
    )

    @Test
    fun `disconnected controller yields SKIPPED`() = runTest {
        // Given: an executor with no controller set
        val executor = executor(controller = null)
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
        val executor = executor(controller)
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
        val executor = executor(controller)
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

    @Test
    fun `scheduled mode snoozes until the next checkpoint`() = runTest {
        // Given: a connected controller, now = 09:30, and a scheduled snooze starting at 10:00
        val controller = mockk<SystemNotificationController>(relaxed = true)
        val executor = executor(controller, now = LocalDateTime.of(2026, 7, 11, 9, 30))
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = RuleAction.createScheduledSnooze(id = "a", startHour = 10, startMinute = 0)

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: it snoozes for exactly 30 minutes (until 10:00) and succeeds
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 1) { controller.snooze("sbn-key", 30 * 60_000L) }
    }

    @Test
    fun `scheduled mode does not re-snooze a checkpoint's own release`() = runTest {
        // Given: an executor that already scheduled a release for this key
        val controller = mockk<SystemNotificationController>(relaxed = true)
        val executor = executor(controller, now = LocalDateTime.of(2026, 7, 11, 9, 30))
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = RuleAction.createScheduledSnooze(id = "a", startHour = 10, startMinute = 0)
        executor.execute(notification, action)

        // When: the notification "reappears" (the same key is processed again)
        val outcome = executor.execute(notification, action)

        // Then: it is left visible - snooze() is called only once (from the first execution)
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 1) { controller.snooze(any(), any()) }
    }

    @Test
    fun `scheduled mode outside the recurrence window passes through without snoozing`() = runTest {
        // Given: a recurring schedule 09:00-18:00 and a match at 18:15 (past the window end)
        val controller = mockk<SystemNotificationController>(relaxed = true)
        val executor = executor(controller, now = LocalDateTime.of(2026, 7, 11, 18, 15))
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = RuleAction.createScheduledSnooze(
            id = "a",
            startHour = 9,
            startMinute = 0,
            intervalMinutes = 60,
            windowEndHour = 18,
            windowEndMinute = 0,
        )

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the notification passes through unsnoozed
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 0) { controller.snooze(any(), any()) }
    }

    @Test
    fun `scheduled mode with missing schedule config yields SKIPPED`() = runTest {
        // Given: a SCHEDULED-mode action missing its required start-time keys
        val controller = mockk<SystemNotificationController>(relaxed = true)
        val executor = executor(controller)
        val notification = createTestNotification(sbnKey = "sbn-key")
        val action = createTestAction(
            type = ActionType.SNOOZE_NOTIFICATION,
            config = mapOf(SNOOZE_MODE_KEY to SnoozeMode.SCHEDULED.name.lowercase()),
        )

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the outcome is SKIPPED and the controller is never invoked
        outcome shouldBe ActionOutcome.SKIPPED
        verify(exactly = 0) { controller.snooze(any(), any()) }
    }
}
