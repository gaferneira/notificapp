package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmRequest
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmRingOptions
import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmSnoozeSettings
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for [dev.gaferneira.notificapp.core.notification.action.alarm.AlarmRequest.canSnoozeAgain], the pure decision `AlarmService.handleSnooze` and
 * `buildNotification()` both gate on. Extracted as a pure property specifically so the
 * snooze-exhausted / snooze-disabled behavior is unit-testable without the Android `Service`
 * class it's used from - this project's test stack has no Robolectric, so `AlarmService` itself
 * cannot be instantiated in a JVM unit test.
 */
class AlarmRequestTest {

    private fun requestWithSnooze(enabled: Boolean, count: Int, maxCount: Int): AlarmRequest = AlarmRequest(
        soundUri = null,
        title = "",
        text = "",
        appName = "",
        snoozeCount = count,
        options = AlarmRingOptions(
            snooze = AlarmSnoozeSettings(
                enabled = enabled,
                durationMinutes = 5,
                maxCount = maxCount,
            ),
        ),
    )

    @Test
    fun `can snooze again when enabled and under the max count`() {
        val request = requestWithSnooze(enabled = true, count = 1, maxCount = 3)

        request.canSnoozeAgain shouldBe true
    }

    @Test
    fun `cannot snooze again once the count reaches the max`() {
        val request = requestWithSnooze(enabled = true, count = 3, maxCount = 3)

        request.canSnoozeAgain shouldBe false
    }

    @Test
    fun `cannot snooze again once the count exceeds the max`() {
        val request = requestWithSnooze(enabled = true, count = 4, maxCount = 3)

        request.canSnoozeAgain shouldBe false
    }

    @Test
    fun `cannot snooze again when snooze is disabled, even under the max count`() {
        val request = requestWithSnooze(enabled = false, count = 0, maxCount = 3)

        request.canSnoozeAgain shouldBe false
    }
}
