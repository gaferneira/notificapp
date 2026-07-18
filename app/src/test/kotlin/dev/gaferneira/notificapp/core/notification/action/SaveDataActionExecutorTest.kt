package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class SaveDataActionExecutorTest {

    @Test
    fun `execute always yields SUCCESS`() = runTest {
        // Given: any notification and action
        val executor = SaveDataActionExecutor()
        val notification = createTestNotification(sbnKey = null)
        val action = createTestAction(type = ActionType.SAVE_DATA)

        // When: executing the action
        val outcome = executor.execute(notification, action, emptyMap())

        // Then: the outcome is always SUCCESS
        outcome shouldBe ActionOutcome.SUCCESS
    }
}
