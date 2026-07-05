package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class AlarmActionExecutorTest {

    @Test
    fun `default alarm action plays the default sound and vibrates`() = runTest {
        // Given: an alarm action with no explicit sound and vibration left at its default (on)
        val alarmPlayer = mockk<AlarmPlayer>(relaxed = true)
        val executor = AlarmActionExecutor(alarmPlayer)
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(id = "action-1")

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the default sound (null) is played, the device vibrates, and the outcome is SUCCESS
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 1) { alarmPlayer.play(null) }
        verify(exactly = 1) { alarmPlayer.vibrate() }
    }

    @Test
    fun `alarm action with a custom sound plays that sound`() = runTest {
        // Given: an alarm action configured with a specific sound URI
        val alarmPlayer = mockk<AlarmPlayer>(relaxed = true)
        val executor = AlarmActionExecutor(alarmPlayer)
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(id = "action-1", soundUri = "content://media/custom-alarm")

        // When: executing the action
        executor.execute(notification, action)

        // Then: the custom sound URI is played
        verify(exactly = 1) { alarmPlayer.play("content://media/custom-alarm") }
    }

    @Test
    fun `alarm action with vibration disabled does not vibrate`() = runTest {
        // Given: an alarm action with vibration explicitly turned off
        val alarmPlayer = mockk<AlarmPlayer>(relaxed = true)
        val executor = AlarmActionExecutor(alarmPlayer)
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(id = "action-1", vibrationEnabled = false)

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the sound still plays, but vibrate is never called
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 1) { alarmPlayer.play(any()) }
        verify(exactly = 0) { alarmPlayer.vibrate() }
    }

    @Test
    fun `action type is CREATE_ALARM for a created alarm action`() {
        // Given/When: creating an alarm action
        val action = RuleAction.createAlarm(id = "action-1")

        // Then: its type is CREATE_ALARM
        action.type shouldBe ActionType.CREATE_ALARM
    }
}
