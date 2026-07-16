package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.core.notification.action.alarm.AlarmStateHolder
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class AlarmStateHolderTest {

    @Test
    fun `starts not ringing`() {
        AlarmStateHolder().isRinging.value shouldBe false
    }

    @Test
    fun `reflects ringing start and stop`() {
        // Given: a fresh holder
        val holder = AlarmStateHolder()

        // When: an alarm starts ringing
        holder.setRinging(true)

        // Then: the state reflects it
        holder.isRinging.value shouldBe true

        // When: the alarm stops
        holder.setRinging(false)

        // Then: the state reflects it
        holder.isRinging.value shouldBe false
    }

    @Test
    fun `tracks the ringing alarm's source key while ringing, and clears it once stopped`() {
        // Given: a fresh holder
        val holder = AlarmStateHolder()

        // When: an alarm starts ringing with a known source
        holder.setRinging(true, "com.test.app|123|0")

        // Then: the source key is exposed
        holder.ringingSourceKey.value shouldBe "com.test.app|123|0"

        // When: the alarm stops
        holder.setRinging(false)

        // Then: the source key is cleared
        holder.ringingSourceKey.value shouldBe null
    }

    @Test
    fun `has no source key when an alarm rings without one`() {
        // Given: a fresh holder (e.g. the rule editor's alarm preview has no triggering notification)
        val holder = AlarmStateHolder()

        // When: the alarm starts ringing with no source key
        holder.setRinging(true)

        // Then: the source key stays null
        holder.ringingSourceKey.value shouldBe null
    }
}
