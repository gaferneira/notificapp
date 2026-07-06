package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class FlashAlertActionExecutorTest {

    @Test
    fun `device without a flash yields SKIPPED`() = runTest {
        // Given: a device with no camera flash
        val torchController = mockk<TorchController>(relaxed = true)
        every { torchController.hasFlash() } returns false
        val executor = FlashAlertActionExecutor(torchController)
        val notification = createTestNotification()
        val action = RuleAction.createFlashAlert(id = "action-1")

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the outcome is SKIPPED and the torch is never blinked
        outcome shouldBe ActionOutcome.SKIPPED
        coVerify(exactly = 0) { torchController.blink(any(), any()) }
    }

    @Test
    fun `battery saver mode yields SKIPPED`() = runTest {
        // Given: a device with a flash, but battery saver is on
        val torchController = mockk<TorchController>(relaxed = true)
        every { torchController.hasFlash() } returns true
        every { torchController.isPowerSaveMode() } returns true
        val executor = FlashAlertActionExecutor(torchController)
        val notification = createTestNotification()
        val action = RuleAction.createFlashAlert(id = "action-1")

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the outcome is SKIPPED and the torch is never blinked
        outcome shouldBe ActionOutcome.SKIPPED
        coVerify(exactly = 0) { torchController.blink(any(), any()) }
    }

    @Test
    fun `device with a flash and no battery saver blinks the torch and yields SUCCESS`() = runTest {
        // Given: a device with a flash and no battery saver
        val torchController = mockk<TorchController>(relaxed = true)
        every { torchController.hasFlash() } returns true
        every { torchController.isPowerSaveMode() } returns false
        val executor = FlashAlertActionExecutor(torchController)
        val notification = createTestNotification()
        val action = RuleAction.createFlashAlert(id = "action-1", flashCount = 5, flashDurationMs = 250)

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the torch blinks with the configured count/duration and the outcome is SUCCESS
        outcome shouldBe ActionOutcome.SUCCESS
        coVerify(exactly = 1) { torchController.blink(5, 250) }
    }
}
