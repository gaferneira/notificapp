package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

private class FlashFixedTimeProvider(private val fixed: LocalDateTime, var fixedEpochMillis: Long) : CurrentTimeProvider {
    override fun now(): LocalDateTime = fixed

    override fun nowEpochMillis(): Long = fixedEpochMillis
}

private fun noopThrottleTracker(timeProvider: FlashFixedTimeProvider = FlashFixedTimeProvider(LocalDateTime.of(2026, 7, 11, 9, 30), 0L)): NotificationThrottleTracker {
    val ruleExecutionRepository: RuleExecutionRepository = mockk {
        coEvery { lastThrottleDeliveryAt(any(), any(), any()) } returns Result.success(null)
    }
    return NotificationThrottleTracker(ruleExecutionRepository, timeProvider)
}

class FlashAlertActionExecutorTest {

    @Test
    fun `device without a flash yields SKIPPED`() = runTest {
        // Given: a device with no camera flash
        val torchController = mockk<TorchController>(relaxed = true)
        every { torchController.hasFlash() } returns false
        val executor = FlashAlertActionExecutor(torchController, noopThrottleTracker())
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
        val executor = FlashAlertActionExecutor(torchController, noopThrottleTracker())
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
        val executor = FlashAlertActionExecutor(torchController, noopThrottleTracker())
        val notification = createTestNotification()
        val action = RuleAction.createFlashAlert(id = "action-1", flashCount = 5, flashDurationMs = 250)

        // When: executing the action
        val outcome = executor.execute(notification, action)

        // Then: the torch blinks with the configured count/duration and the outcome is SUCCESS
        outcome shouldBe ActionOutcome.SUCCESS
        coVerify(exactly = 1) { torchController.blink(5, 250) }
    }

    @Test
    fun `flash within its cooldown window is suppressed and the torch never blinks again`() = runTest {
        // Given: a flash action with a 60s cooldown, already delivered once at t=0
        val torchController = mockk<TorchController>(relaxed = true)
        every { torchController.hasFlash() } returns true
        every { torchController.isPowerSaveMode() } returns false
        val timeProvider = FlashFixedTimeProvider(LocalDateTime.of(2026, 7, 11, 9, 30), 0L)
        val throttleTracker = noopThrottleTracker(timeProvider)
        val executor = FlashAlertActionExecutor(torchController, throttleTracker)
        val notification = createTestNotification(packageName = "com.test.app")
        val action = RuleAction.createFlashAlert(id = "action-1", cooldownSeconds = 60)
        executor.execute(notification, action)

        // When: a second match arrives 30s later, still inside the window
        timeProvider.fixedEpochMillis = 30_000L
        val outcome = executor.execute(notification, action)

        // Then: it is suppressed and the torch is only blinked once
        outcome shouldBe ActionOutcome.SUPPRESSED
        coVerify(exactly = 1) { torchController.blink(any(), any()) }
    }

    @Test
    fun `flash blinks again once its cooldown window elapses`() = runTest {
        // Given: a flash action with a 60s cooldown, delivered once at t=0
        val torchController = mockk<TorchController>(relaxed = true)
        every { torchController.hasFlash() } returns true
        every { torchController.isPowerSaveMode() } returns false
        val timeProvider = FlashFixedTimeProvider(LocalDateTime.of(2026, 7, 11, 9, 30), 0L)
        val throttleTracker = noopThrottleTracker(timeProvider)
        val executor = FlashAlertActionExecutor(torchController, throttleTracker)
        val notification = createTestNotification(packageName = "com.test.app")
        val action = RuleAction.createFlashAlert(id = "action-1", cooldownSeconds = 60)
        executor.execute(notification, action)

        // When: the 60s window has fully elapsed
        timeProvider.fixedEpochMillis = 60_000L
        val outcome = executor.execute(notification, action)

        // Then: it blinks again
        outcome shouldBe ActionOutcome.SUCCESS
        coVerify(exactly = 2) { torchController.blink(any(), any()) }
    }

    @Test
    fun `flash with cooldown disabled always blinks, ignoring repeated matches`() = runTest {
        // Given: a flash action with cooldown left at its default (disabled)
        val torchController = mockk<TorchController>(relaxed = true)
        every { torchController.hasFlash() } returns true
        every { torchController.isPowerSaveMode() } returns false
        val executor = FlashAlertActionExecutor(torchController, noopThrottleTracker())
        val notification = createTestNotification(packageName = "com.test.app")
        val action = RuleAction.createFlashAlert(id = "action-1")
        executor.execute(notification, action)

        // When: a second immediate match arrives
        val outcome = executor.execute(notification, action)

        // Then: it still blinks - no cooldown was configured
        outcome shouldBe ActionOutcome.SUCCESS
        coVerify(exactly = 2) { torchController.blink(any(), any()) }
    }
}
