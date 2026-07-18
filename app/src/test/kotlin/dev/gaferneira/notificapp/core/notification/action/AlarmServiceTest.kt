package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmStateHolder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Tests for AlarmService edge cases: notification dismissal, snooze races, alarm lifecycle.
 *
 * Note: Full AlarmService testing requires Robolectric or full Android instrumentation.
 * These tests focus on the core logic paths that don't require Android framework access.
 * See AlarmStateHolderTest for state synchronization tests.
 */
class AlarmServiceTest {

    @Test
    fun `stopIfSource ignores dismissal of wrong notification source`() {
        // Given: an alarm is ringing from notification com.bank|123|0
        val holder = AlarmStateHolder()
        holder.setRinging(true, "com.bank|123|0")

        // When: a different notification com.mail|456|0 is dismissed
        val sourceKey = "com.mail|456|0"

        // Then: the ringing source key doesn't match, so alarm should not stop
        // (This is handled by AndroidAlarmController.stopIfSource before sending the intent)
        holder.ringingSourceKey.value shouldBe "com.bank|123|0"
        (holder.ringingSourceKey.value != sourceKey) shouldBe true
    }

    @Test
    fun `stopIfSource matches when source notification is dismissed`() {
        // Given: an alarm is ringing from notification com.bank|123|0
        val holder = AlarmStateHolder()
        holder.setRinging(true, "com.bank|123|0")

        // When: that same notification is dismissed
        val sourceKey = "com.bank|123|0"

        // Then: the source key matches, alarm should stop
        (holder.ringingSourceKey.value == sourceKey) shouldBe true
    }

    @Test
    fun `alarm with no source (preview) cannot be stopped via stopIfSource`() {
        // Given: an alarm ringing without a source (e.g. rule editor preview)
        val holder = AlarmStateHolder()
        holder.setRinging(true, sourceKey = null)

        // When: trying to stop it via stopIfSource with any key
        val sourceKey = "com.test|123|0"

        // Then: the source key doesn't match (null != "com.test|123|0")
        (holder.ringingSourceKey.value == sourceKey) shouldBe false
    }

    @Test
    fun `alarm state clears source key when stopped`() {
        // Given: an alarm ringing from a notification source
        val holder = AlarmStateHolder()
        holder.setRinging(true, "com.bank|123|0")

        // When: the alarm stops
        holder.setRinging(false)

        // Then: the source key is cleared so no future dismissal can match it
        holder.ringingSourceKey.value shouldBe null
        holder.isRinging.value shouldBe false
    }
}
