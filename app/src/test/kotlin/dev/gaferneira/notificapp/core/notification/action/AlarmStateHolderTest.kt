package dev.gaferneira.notificapp.core.notification.action

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
}
