package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmActionExecutor
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmController
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmRequest
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundConfig
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundPreset
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.AlarmOptionsConfig
import dev.gaferneira.notificapp.domain.model.AlarmSnoozeConfig
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.VibrationPattern
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

private class AlarmFixedTimeProvider(private val fixed: LocalDateTime, var fixedEpochMillis: Long) : CurrentTimeProvider {
    override fun now(): LocalDateTime = fixed

    override fun nowEpochMillis(): Long = fixedEpochMillis
}

private fun noopThrottleTracker(timeProvider: AlarmFixedTimeProvider = AlarmFixedTimeProvider(LocalDateTime.of(2026, 7, 11, 9, 30), 0L)): NotificationThrottleTracker {
    val ruleExecutionRepository: RuleExecutionRepository = mockk {
        coEvery { lastThrottleDeliveryAt(any(), any(), any()) } returns Result.success(null)
    }
    return NotificationThrottleTracker(ruleExecutionRepository, timeProvider)
}

class AlarmActionExecutorTest {

    @Test
    fun `default alarm action starts the alarm with default sound, vibration, and source content`() = runTest {
        // Given: an alarm action with no explicit sound and vibration left at its default (on)
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
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
                    title = "Payment received",
                    text = "You got \$50",
                    appName = "Bank",
                ),
            )
        }
    }

    @Test
    fun `alarm request carries the triggering notification's sbnKey as its source`() = runTest {
        // Given: a triggering notification with a known sbnKey
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
        val notification = createTestNotification(sbnKey = "com.test.app|123|0")
        val action = RuleAction.createAlarm(id = "action-1")

        // When: executing the action
        executor.execute(notification, action)

        // Then: the request's sourceKey is the notification's sbnKey, so a later dismissal of that
        // notification can stop this specific alarm
        verify(exactly = 1) {
            alarmController.start(match { it.sourceKey == "com.test.app|123|0" })
        }
    }

    @Test
    fun `alarm falls back to app name when the notification has no title`() = runTest {
        // Given: a triggering notification with no title
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
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
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
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
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
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
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(id = "action-1", options = AlarmOptionsConfig(fullScreenEnabled = false))

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
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
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

    @Test
    fun `alarm action with sound disabled starts the alarm with sound off`() = runTest {
        // Given: an alarm action with sound explicitly turned off
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(id = "action-1", options = AlarmOptionsConfig(soundEnabled = false))

        // When: executing the action
        executor.execute(notification, action)

        // Then: the request carries soundEnabled = false
        verify(exactly = 1) {
            alarmController.start(match { !it.soundEnabled })
        }
    }

    @Test
    fun `alarm action maps the selected vibration pattern into the request`() = runTest {
        // Given: an alarm action configured with a non-default vibration pattern
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(id = "action-1", options = AlarmOptionsConfig(vibrationPattern = VibrationPattern.PULSE))

        // When: executing the action
        executor.execute(notification, action)

        // Then: the request carries the selected pattern
        verify(exactly = 1) {
            alarmController.start(match { it.vibrationPattern == VibrationPattern.PULSE })
        }
    }

    @Test
    fun `alarm action maps snooze enabled, duration, and max count into the request`() = runTest {
        // Given: an alarm action with non-default snooze settings
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(
            id = "action-1",
            options = AlarmOptionsConfig(snooze = AlarmSnoozeConfig(enabled = false, durationMinutes = 10, maxCount = 5)),
        )

        // When: executing the action
        executor.execute(notification, action)

        // Then: the request carries every snooze field
        verify(exactly = 1) {
            alarmController.start(
                match {
                    !it.snoozeEnabled && it.snoozeDurationMinutes == 10 && it.snoozeMaxCount == 5
                },
            )
        }
    }

    @Test
    fun `alarm action maps the background preset selection into the request`() = runTest {
        // Given: an alarm action configured with a preset background
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(
            id = "action-1",
            options = AlarmOptionsConfig(
                background = AlarmBackgroundConfig(type = AlarmBackgroundType.PRESET, presetId = AlarmBackgroundPreset.OCEAN.id),
            ),
        )

        // When: executing the action
        executor.execute(notification, action)

        // Then: the request carries the background type and preset id
        verify(exactly = 1) {
            alarmController.start(
                match {
                    it.backgroundType == AlarmBackgroundType.PRESET && it.backgroundPresetId == AlarmBackgroundPreset.OCEAN.id
                },
            )
        }
    }

    @Test
    fun `alarm action maps the background image uri into the request`() = runTest {
        // Given: an alarm action configured with a custom background image
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
        val notification = createTestNotification()
        val action = RuleAction.createAlarm(
            id = "action-1",
            options = AlarmOptionsConfig(
                background = AlarmBackgroundConfig(type = AlarmBackgroundType.IMAGE, imageUri = "content://media/bg.jpg"),
            ),
        )

        // When: executing the action
        executor.execute(notification, action)

        // Then: the request carries the background type and image uri
        verify(exactly = 1) {
            alarmController.start(
                match {
                    it.backgroundType == AlarmBackgroundType.IMAGE && it.backgroundImageUri == "content://media/bg.jpg"
                },
            )
        }
    }

    @Test
    fun `alarm within its cooldown window is suppressed and never started`() = runTest {
        // Given: an alarm action with a 60s cooldown, already delivered once at t=0
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val timeProvider = AlarmFixedTimeProvider(LocalDateTime.of(2026, 7, 11, 9, 30), 0L)
        val throttleTracker = noopThrottleTracker(timeProvider)
        val executor = AlarmActionExecutor(alarmController, throttleTracker)
        val notification = createTestNotification(packageName = "com.test.app")
        val action = RuleAction.createAlarm(id = "action-1", options = AlarmOptionsConfig(cooldownSeconds = 60))
        executor.execute(notification, action)

        // When: a second match arrives 30s later, still inside the window
        timeProvider.fixedEpochMillis = 30_000L
        val outcome = executor.execute(notification, action)

        // Then: it is suppressed and the controller is never started again
        outcome shouldBe ActionOutcome.SUPPRESSED
        verify(exactly = 1) { alarmController.start(any()) }
    }

    @Test
    fun `alarm delivers again once its cooldown window elapses`() = runTest {
        // Given: an alarm action with a 60s cooldown, delivered once at t=0
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val timeProvider = AlarmFixedTimeProvider(LocalDateTime.of(2026, 7, 11, 9, 30), 0L)
        val throttleTracker = noopThrottleTracker(timeProvider)
        val executor = AlarmActionExecutor(alarmController, throttleTracker)
        val notification = createTestNotification(packageName = "com.test.app")
        val action = RuleAction.createAlarm(id = "action-1", options = AlarmOptionsConfig(cooldownSeconds = 60))
        executor.execute(notification, action)

        // When: the 60s window has fully elapsed
        timeProvider.fixedEpochMillis = 60_000L
        val outcome = executor.execute(notification, action)

        // Then: it delivers again
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 2) { alarmController.start(any()) }
    }

    @Test
    fun `alarm with cooldown disabled always starts, ignoring repeated matches`() = runTest {
        // Given: an alarm action with cooldown left at its default (disabled)
        val alarmController = mockk<AlarmController>()
        every { alarmController.start(any()) } returns true
        val executor = AlarmActionExecutor(alarmController, noopThrottleTracker())
        val notification = createTestNotification(packageName = "com.test.app")
        val action = RuleAction.createAlarm(id = "action-1")
        executor.execute(notification, action)

        // When: a second immediate match arrives
        val outcome = executor.execute(notification, action)

        // Then: it still delivers - no cooldown was configured
        outcome shouldBe ActionOutcome.SUCCESS
        verify(exactly = 2) { alarmController.start(any()) }
    }
}
