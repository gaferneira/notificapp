package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.READ_ALOUD_TEMPLATE_KEY
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ReadAloudActionExecutor] per the Read aloud feature's Testing section: a
 * blank/unset template is SKIPPED without ever calling the controller; a template with
 * placeholders resolves against built-in tokens and [extractedFields] and is spoken as-is
 * (no JSON escaping, unlike `SEND_WEBHOOK`'s TEMPLATE mode); a controller failure yields FAILED.
 */
class ReadAloudActionExecutorTest {

    private val ttsController: TtsController = mockk()
    private val executor = ReadAloudActionExecutor(ttsController)

    @Test
    fun `unset template yields SKIPPED and never speaks`() = runTest {
        val notification = createTestNotification()
        val action = createTestAction(type = ActionType.READ_ALOUD, config = emptyMap())

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.SKIPPED
        coVerify(exactly = 0) { ttsController.speak(any()) }
    }

    @Test
    fun `blank template yields SKIPPED and never speaks`() = runTest {
        val notification = createTestNotification()
        val action = createTestAction(type = ActionType.READ_ALOUD, config = mapOf(READ_ALOUD_TEMPLATE_KEY to "   "))

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.SKIPPED
        coVerify(exactly = 0) { ttsController.speak(any()) }
    }

    @Test
    fun `template with builtin and field placeholders resolves and is spoken, yielding SUCCESS`() = runTest {
        val notification = createTestNotification(appName = "Bank App")
        val action = createTestAction(
            type = ActionType.READ_ALOUD,
            config = mapOf(READ_ALOUD_TEMPLATE_KEY to "Received {{field.amount}} from {{field.sender}} via {{app_name}}"),
        )
        coEvery { ttsController.speak(any()) } returns Unit

        val outcome = executor.execute(notification, action, mapOf("amount" to "45 euros", "sender" to "Maria"))

        outcome shouldBe ActionOutcome.SUCCESS
        coVerify(exactly = 1) { ttsController.speak("Received 45 euros from Maria via Bank App") }
    }

    @Test
    fun `unresolved field placeholder substitutes to empty rather than failing`() = runTest {
        val notification = createTestNotification()
        val action = createTestAction(
            type = ActionType.READ_ALOUD,
            config = mapOf(READ_ALOUD_TEMPLATE_KEY to "Amount: {{field.amount}}"),
        )
        coEvery { ttsController.speak(any()) } returns Unit

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.SUCCESS
        coVerify(exactly = 1) { ttsController.speak("Amount: ") }
    }

    @Test
    fun `controller throwing yields FAILED`() = runTest {
        val notification = createTestNotification()
        val action = createTestAction(
            type = ActionType.READ_ALOUD,
            config = mapOf(READ_ALOUD_TEMPLATE_KEY to "Hello"),
        )
        coEvery { ttsController.speak(any()) } throws IllegalStateException("tts unavailable")

        val outcome = executor.execute(notification, action, emptyMap())

        outcome shouldBe ActionOutcome.FAILED
    }
}
