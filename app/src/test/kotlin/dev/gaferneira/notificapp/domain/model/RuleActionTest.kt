package dev.gaferneira.notificapp.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class RuleActionTest {

    @Test
    fun `flash count defaults when not configured`() {
        val action = RuleAction(id = "action-1", type = ActionType.FLASH_ALERT)

        action.getFlashCount() shouldBe DEFAULT_FLASH_COUNT
    }

    @Test
    fun `flash count is clamped to the safe maximum regardless of stored config`() {
        // Given: a maliciously or accidentally excessive flash count (e.g. an imported rule)
        val action = RuleAction(
            id = "action-1",
            type = ActionType.FLASH_ALERT,
            config = mapOf(FLASH_COUNT_KEY to "999"),
        )

        // Then: it is clamped to the safe maximum, not the raw stored value
        action.getFlashCount() shouldBe MAX_FLASH_COUNT
    }

    @Test
    fun `flash count is clamped to the safe minimum regardless of stored config`() {
        val action = RuleAction(
            id = "action-1",
            type = ActionType.FLASH_ALERT,
            config = mapOf(FLASH_COUNT_KEY to "-5"),
        )

        action.getFlashCount() shouldBe MIN_FLASH_COUNT
    }

    @Test
    fun `flash duration is clamped to the photosensitivity-safe minimum`() {
        // Given: a duration fast enough to risk a photosensitive reaction
        val action = RuleAction(
            id = "action-1",
            type = ActionType.FLASH_ALERT,
            config = mapOf(FLASH_DURATION_MS_KEY to "10"),
        )

        // Then: it is clamped up to the safe floor
        action.getFlashDurationMs() shouldBe MIN_FLASH_DURATION_MS
    }

    @Test
    fun `flash duration is clamped to the safe maximum`() {
        val action = RuleAction(
            id = "action-1",
            type = ActionType.FLASH_ALERT,
            config = mapOf(FLASH_DURATION_MS_KEY to "999999"),
        )

        action.getFlashDurationMs() shouldBe MAX_FLASH_DURATION_MS
    }

    @Test
    fun `createFlashAlert stores the requested count and duration`() {
        val action = RuleAction.createFlashAlert(id = "action-1", flashCount = 5, flashDurationMs = 400)

        action.type shouldBe ActionType.FLASH_ALERT
        action.getFlashCount() shouldBe 5
        action.getFlashDurationMs() shouldBe 400L
    }
}
