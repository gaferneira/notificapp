package dev.gaferneira.notificapp.features.ruleeditor.ui

import dev.gaferneira.notificapp.domain.model.RuleAction
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

/**
 * Unit tests for [shouldResetThrottleWatermark], the pure decision function backing D5's
 * window-duration-change half (the disable->re-enable half lives in `RuleEditorViewModel.toggled`
 * and is covered by `RuleEditorViewModelTest`). Exercised directly rather than through a
 * Compose/androidTest harness since the repo has no existing Compose-UI-test convention and this
 * logic has already been extracted into a plain, side-effect-free function.
 */
class SnoozeBottomSheetTest {

    @Test
    fun `a brand new action always resets the watermark`() {
        // Given: no prior action (adding a new throttle action)
        // When: deciding whether to reset the watermark
        val shouldReset = shouldResetThrottleWatermark(initial = null, throttleWindowMinutes = 10)

        // Then: it resets, since there's no prior state to preserve
        shouldReset shouldBe true
    }

    @Test
    fun `entering throttle mode from a different prior mode resets the watermark`() {
        // Given: an existing action that wasn't in throttle mode
        val initial = RuleAction.createSnooze(id = "action-1", durationMinutes = 15)

        // When: switching it to throttle mode
        val shouldReset = shouldResetThrottleWatermark(initial, throttleWindowMinutes = 10)

        // Then: it resets, since the action is newly entering throttle mode
        shouldReset shouldBe true
    }

    @Test
    fun `changing the window duration on an already-throttling action resets the watermark`() {
        // Given: an existing throttle action with a 10-minute window
        val initial = RuleAction.createThrottleSnooze(id = "action-1", windowMinutes = 10, resetAt = 500L)

        // When: the window duration is changed to 30 minutes
        val shouldReset = shouldResetThrottleWatermark(initial, throttleWindowMinutes = 30)

        // Then: the timer resets - a different window shape shouldn't keep the stale watermark
        shouldReset shouldBe true
    }

    @Test
    fun `saving an already-throttling action with an unchanged window duration preserves the watermark`() {
        // Given: an existing throttle action with a 10-minute window
        val initial = RuleAction.createThrottleSnooze(id = "action-1", windowMinutes = 10, resetAt = 500L)

        // When: the action is saved again with the same window duration (an unrelated edit)
        val shouldReset = shouldResetThrottleWatermark(initial, throttleWindowMinutes = 10)

        // Then: the prior watermark is preserved, so an unrelated save doesn't reopen the window
        shouldReset shouldBe false
    }
}
