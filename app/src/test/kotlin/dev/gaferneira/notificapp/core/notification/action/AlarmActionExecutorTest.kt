package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AlarmActionExecutorTest {

    @Test
    fun `default alarm action starts the alarm with default sound, vibration, and source content`() = runTest {
        // Given: an alarm action with no explicit sound and vibration left at its default (on)
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController)
        val notification = createTestNotification(title = "Payment received", content = "You got \$50", appName = "Bank")
        val action = RuleAction.createAlarm(id = "action-1")

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the alarm is started with defaults and the triggering notification's content
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 1) {
            alarmController.start(
                AlarmRequest(
                    soundUri = null,
                    vibrationEnabled = true,
                    fullScreenEnabled = true,
                    title = "Payment received",
                    text = "You got \$50",
                    appName = "Bank",
                ),
            )
        }
    }

    @Test
    fun `alarm falls back to app name when the notification has no title`() = runTest {
        // Given: a triggering notification with no title
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController)
        val notification = createTestNotification(title = null, content = null, appName = "Bank")
        val action = RuleAction.createAlarm(id = "action-1")

        // When: executing the action
        executor.execute(notification, action)

        // Then: the app name is used as the title and the text is blank
        verify(exactly = 1) {
            alarmController.start(match { it.title == "Bank" && it.text == "" })
        }
    }

    @Test
    fun `alarm action with a custom sound starts the alarm with that sound`() = runTest {
        // Given: an alarm action configured with a specific sound URI
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController)
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(id = "action-1", soundUri = "content://media/custom-alarm")

        // When: executing the action
        executor.execute(notification, action)

        // Then: the custom sound URI is passed through to the controller
        verify(exactly = 1) {
            alarmController.start(match { it.soundUri == "content://media/custom-alarm" })
        }
    }

    @Test
    fun `alarm action with vibration disabled starts the alarm without vibration`() = runTest {
        // Given: an alarm action with vibration explicitly turned off
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController)
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(id = "action-1", vibrationEnabled = false)

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the alarm is started with vibration off
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 1) {
            alarmController.start(match { !it.vibrationEnabled })
        }
    }

    @Test
    fun `alarm action with full-screen disabled starts the alarm without full-screen`() = runTest {
        // Given: an alarm action with the full-screen (call-style) option turned off
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController)
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(id = "action-1", fullScreenEnabled = false)

        // When: executing the action
        executor.execute(notification, action)

        // Then: the request carries fullScreenEnabled = false
        verify(exactly = 1) {
            alarmController.start(match { !it.fullScreenEnabled })
        }
    }

    @Test
    fun `alarm action is skipped when it cannot be started`() = runTest {
        // Given: the controller refuses to start (e.g. notifications disabled -> unstoppable)
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns false
        val executor = AlarmActionExecutor(alarmController)
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(id = "action-1")

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the outcome is SKIPPED, not SUCCESS
        outcome shouldBe ActionOutcome.SKIPPED
    }

    @Test
    fun `action type is CREATE_ALARM for a created alarm action`() {
        // Given/When: creating an alarm action
        val action = RuleAction.createAlarm(id = "action-1")

        // Then: its type is CREATE_ALARM
        action.type shouldBe ActionType.CREATE_ALARM
    }
}
