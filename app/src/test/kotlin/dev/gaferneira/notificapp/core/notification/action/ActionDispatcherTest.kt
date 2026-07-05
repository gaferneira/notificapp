package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.action.ActionExecutor
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import javax.inject.Provider

class ActionDispatcherTest {

    private fun providerOf(executor: ActionExecutor): Provider<ActionExecutor> = Provider { executor }

    private class FixedOutcomeExecutor(private val outcome: ActionOutcome) : ActionExecutor {
        override suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome = outcome
    }

    private class ThrowingExecutor : ActionExecutor {
        override suspend fun execute(notification: Notification, action: RuleAction): ActionOutcome = throw IllegalStateException("boom")
    }

    @Test
    fun `disabled action is excluded from the outcome map entirely`() = runTest {
        // Given: a dispatcher with a registered executor, and a disabled action of that type
        val dispatcher = ActionDispatcher(
            mapOf(ActionType.SAVE_DATA to providerOf(FixedOutcomeExecutor(ActionOutcome.SUCCESS))),
        )
        val notification = createTestNotification()
        val disabledAction = createTestAction(id = "action-1", type = ActionType.SAVE_DATA, isEnabled = false)

        // When: dispatching the actions
        val result = dispatcher.executeAll(notification, listOf(disabledAction))

        // Then: the disabled action has no entry at all in the result map
        result shouldNotContainKey "action-1"
        result shouldHaveSize 0
    }

    @Test
    fun `missing handler for a registered action type yields SKIPPED`() = runTest {
        // Given: a dispatcher with no executor registered for CREATE_ALARM
        val dispatcher = ActionDispatcher(emptyMap())
        val notification = createTestNotification()
        val action = createTestAction(id = "action-1", type = ActionType.CREATE_ALARM, isEnabled = true)

        // When: dispatching the action
        val result = dispatcher.executeAll(notification, listOf(action))

        // Then: the outcome is SKIPPED
        result["action-1"] shouldBe ActionOutcome.SKIPPED
    }

    @Test
    fun `executor throwing is caught and yields FAILED`() = runTest {
        // Given: a dispatcher whose registered executor throws
        val dispatcher = ActionDispatcher(
            mapOf(ActionType.DISMISS_NOTIFICATION to providerOf(ThrowingExecutor())),
        )
        val notification = createTestNotification()
        val action = createTestAction(id = "action-1", type = ActionType.DISMISS_NOTIFICATION, isEnabled = true)

        // When: dispatching the action
        val result = dispatcher.executeAll(notification, listOf(action))

        // Then: the outcome is FAILED, and no exception propagates
        result["action-1"] shouldBe ActionOutcome.FAILED
    }

    @Test
    fun `executor returning normally passes its outcome through`() = runTest {
        // Given: a dispatcher whose registered executor returns SUCCESS
        val dispatcher = ActionDispatcher(
            mapOf(ActionType.SAVE_DATA to providerOf(FixedOutcomeExecutor(ActionOutcome.SUCCESS))),
        )
        val notification = createTestNotification()
        val action = createTestAction(id = "action-1", type = ActionType.SAVE_DATA, isEnabled = true)

        // When: dispatching the action
        val result = dispatcher.executeAll(notification, listOf(action))

        // Then: the outcome matches what the executor returned
        result["action-1"] shouldBe ActionOutcome.SUCCESS
    }

    @Test
    fun `multiple enabled actions each get one outcome entry`() = runTest {
        // Given: a dispatcher with two registered executors and two enabled actions
        val dispatcher = ActionDispatcher(
            mapOf(
                ActionType.SAVE_DATA to providerOf(FixedOutcomeExecutor(ActionOutcome.SUCCESS)),
                ActionType.DISMISS_NOTIFICATION to providerOf(FixedOutcomeExecutor(ActionOutcome.SUCCESS)),
            ),
        )
        val notification = createTestNotification()
        val firstAction = createTestAction(id = "action-1", type = ActionType.SAVE_DATA, isEnabled = true)
        val secondAction = createTestAction(id = "action-2", type = ActionType.DISMISS_NOTIFICATION, isEnabled = true)

        // When: dispatching both actions
        val result = dispatcher.executeAll(notification, listOf(firstAction, secondAction))

        // Then: both action ids are present in the outcome map
        result shouldHaveSize 2
        result shouldContainKey "action-1"
        result shouldContainKey "action-2"
    }
}
