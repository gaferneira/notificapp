package dev.gaferneira.notificapp.features.notification

import dev.gaferneira.notificapp.core.extraction.RuleEngine
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.notification.action.ActionDispatcher
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProcessNotificationUseCaseTest {

    private lateinit var deduplicator: NotificationDeduplicator
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var ruleRepository: RuleRepository
    private lateinit var ruleExecutionRepository: RuleExecutionRepository
    private lateinit var actionDispatcher: ActionDispatcher
    private lateinit var useCase: ProcessNotificationUseCase
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setUp() {
        deduplicator = mockk()
        notificationRepository = mockk()
        ruleRepository = mockk()
        ruleExecutionRepository = mockk()
        actionDispatcher = mockk()
        useCase = ProcessNotificationUseCase(
            deduplicator = deduplicator,
            notificationRepository = notificationRepository,
            ruleRepository = ruleRepository,
            ruleEngine = RuleEngine(),
            ruleExecutionRepository = ruleExecutionRepository,
            actionDispatcher = actionDispatcher,
            ioDispatcher = testDispatcher,
        )
    }

    @Test
    fun `duplicate notification is skipped without saving or loading rules`() = runTest(testDispatcher) {
        // Given: a notification detected as a duplicate
        val notification = createTestNotification()
        coEvery { deduplicator.isDuplicate(notification) } returns true

        // When: invoking the use case
        val result = useCase.invoke(notification)

        // Then: an empty success is returned and neither save nor rule lookup happen
        result shouldBe Result.success(emptyList())
        coVerify(exactly = 0) { notificationRepository.saveNotification(any()) }
        coVerify(exactly = 0) { ruleRepository.getRulesForApp(any()) }
    }

    @Test
    fun `saveNotification failure short-circuits before loading rules`() = runTest(testDispatcher) {
        // Given: a non-duplicate notification whose save fails
        val notification = createTestNotification()
        val exception = RuntimeException("db error")
        coEvery { deduplicator.isDuplicate(notification) } returns false
        coEvery { notificationRepository.saveNotification(notification) } returns Result.failure(exception)

        // When: invoking the use case
        val result = useCase.invoke(notification)

        // Then: the failure propagates and rules are never loaded
        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe exception
        coVerify(exactly = 0) { ruleRepository.getRulesForApp(any()) }
    }

    @Test
    fun `no matching rules yields empty success without saving executions`() = runTest(testDispatcher) {
        // Given: a saved notification with no rules configured for its app
        val notification = createTestNotification()
        coEvery { deduplicator.isDuplicate(notification) } returns false
        coEvery { notificationRepository.saveNotification(notification) } returns Result.success(Unit)
        coEvery { ruleRepository.getRulesForApp(notification.packageName) } returns Result.success(emptyList())

        // When: invoking the use case
        val result = useCase.invoke(notification)

        // Then: an empty success is returned and no execution is saved
        result shouldBe Result.success(emptyList())
        coVerify(exactly = 0) { ruleExecutionRepository.saveExecution(any(), any()) }
    }

    @Test
    fun `matching rule executes actions and persists a rule execution with the reported outcomes`() = runTest(testDispatcher) {
        // Given: a saved notification, one matching rule with one enabled action, and a dispatcher
        // that reports SUCCESS for that action
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val action = createTestAction(id = "action-1")
        val rule = createTestRule(id = "rule-1", conditions = listOf(condition), actions = listOf(action))

        coEvery { deduplicator.isDuplicate(notification) } returns false
        coEvery { notificationRepository.saveNotification(notification) } returns Result.success(Unit)
        coEvery { ruleRepository.getRulesForApp(notification.packageName) } returns Result.success(listOf(rule))
        coEvery { actionDispatcher.executeAll(notification, rule.actions) } returns mapOf("action-1" to ActionOutcome.SUCCESS)
        coEvery { ruleExecutionRepository.saveExecution(any(), any()) } returns Result.success(Unit)

        // When: invoking the use case
        val result = useCase.invoke(notification)

        // Then: the result contains one execution reflecting the reported outcome, and it was saved
        result.isSuccess shouldBe true
        val executions = result.getOrThrow()
        executions.size shouldBe 1
        val execution = executions[0]
        execution.ruleId shouldBe "rule-1"
        execution.notificationId shouldBe notification.id
        execution.actionOutcomes shouldBe mapOf("action-1" to ActionOutcome.SUCCESS)
        execution.triggeredActions shouldBe listOf("action-1")

        coVerify(exactly = 1) { ruleExecutionRepository.saveExecution(execution, rule.fields) }
    }

    @Test
    fun `evaluateAndPersist does not deduplicate or re-save the notification`() = runTest(testDispatcher) {
        // Given: an already-stored notification with no matching rules
        val notification = createTestNotification()
        coEvery { ruleRepository.getRulesForApp(notification.packageName) } returns Result.success(emptyList())

        // When: re-evaluating rules directly (the re-run path used by NotificationDetailViewModel)
        val result = useCase.evaluateAndPersist(notification)

        // Then: the result succeeds and dedup/save are never invoked
        result shouldBe Result.success(emptyList())
        coVerify(exactly = 0) { deduplicator.isDuplicate(any()) }
        coVerify(exactly = 0) { notificationRepository.saveNotification(any()) }
    }

    @Test
    fun `getRulesForApp failure propagates as a failed result`() = runTest(testDispatcher) {
        // Given: a saved notification whose rule lookup fails
        val notification = createTestNotification()
        val exception = RuntimeException("rule lookup error")
        coEvery { deduplicator.isDuplicate(notification) } returns false
        coEvery { notificationRepository.saveNotification(notification) } returns Result.success(Unit)
        coEvery { ruleRepository.getRulesForApp(notification.packageName) } returns Result.failure(exception)

        // When: invoking the use case
        val result = useCase.invoke(notification)

        // Then: the failure propagates
        result.isFailure shouldBe true
        result.exceptionOrNull() shouldBe exception
    }
}
