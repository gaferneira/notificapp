package dev.gaferneira.notificapp.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class VibrationPatternTest {

    @Test
    fun `fromId resolves a known id to its pattern`() {
        VibrationPattern.fromId("pulse") shouldBe VibrationPattern.PULSE
        VibrationPattern.fromId("long") shouldBe VibrationPattern.LONG
        VibrationPattern.fromId("basic_call") shouldBe VibrationPattern.BASIC_CALL
    }

    @Test
    fun `fromId falls back to BASIC_CALL for an unrecognized id`() {
        VibrationPattern.fromId("not-a-real-pattern") shouldBe VibrationPattern.BASIC_CALL
    }

    @Test
    fun `fromId falls back to BASIC_CALL for a null id`() {
        VibrationPattern.fromId(null) shouldBe VibrationPattern.BASIC_CALL
    }
}
