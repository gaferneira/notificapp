package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.core.extraction.RuleEngine
import dev.gaferneira.notificapp.core.notification.action.ActionDispatcher
import dev.gaferneira.notificapp.core.notification.action.CurrentTimeProvider
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.saveDataFields
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class ProcessNotificationUseCaseTest {

    private lateinit var deduplicator: NotificationDeduplicator
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var ruleRepository: RuleRepository
    private lateinit var ruleExecutionRepository: RuleExecutionRepository
    private lateinit var actionDispatcher: ActionDispatcher
    private lateinit var timeProvider: CurrentTimeProvider
    private lateinit var useCase: ProcessNotificationUseCase
    private val testDispatcher = StandardTestDispatcher()
    private val fixedNow = LocalDateTime.of(2026, 7, 6, 12, 0)

    @BeforeEach
    fun setUp() {
        deduplicator = mockk()
        notificationRepository = mockk()
        ruleRepository = mockk()
        ruleExecutionRepository = mockk()
        actionDispatcher = mockk()
        timeProvider = mockk()
        every { timeProvider.now() } returns fixedNow
        useCase = ProcessNotificationUseCase(
            deduplicator = deduplicator,
            notificationRepository = notificationRepository,
            ruleRepository = ruleRepository,
            ruleEngine = RuleEngine(),
            ruleExecutionRepository = ruleExecutionRepository,
            actionDispatcher = actionDispatcher,
            timeProvider = timeProvider,
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

        coVerify(exactly = 1) { ruleExecutionRepository.saveExecution(execution, rule.saveDataFields()) }
    }

    @Test
    fun `dry-run rule matches without invoking the action dispatcher, and the execution records the flag`() = runTest(testDispatcher) {
        // Given: a saved notification and a matching dry-run rule with an enabled action
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val action = createTestAction(id = "action-1")
        val rule = createTestRule(id = "rule-1", isDryRun = true, conditions = listOf(condition), actions = listOf(action))

        coEvery { deduplicator.isDuplicate(notification) } returns false
        coEvery { notificationRepository.saveNotification(notification) } returns Result.success(Unit)
        coEvery { ruleRepository.getRulesForApp(notification.packageName) } returns Result.success(listOf(rule))
        coEvery { ruleExecutionRepository.saveExecution(any(), any()) } returns Result.success(Unit)

        // When: invoking the use case
        val result = useCase.invoke(notification)

        // Then: the match is recorded with no action outcomes and wasDryRun set, and the
        // dispatcher is never called - dry-run rules never reach it
        result.isSuccess shouldBe true
        val execution = result.getOrThrow().single()
        execution.wasDryRun shouldBe true
        execution.actionOutcomes shouldBe emptyMap()
        execution.triggeredActions shouldBe listOf("action-1")
        coVerify(exactly = 0) { actionDispatcher.executeAll(any(), any()) }
    }

    @Test
    fun `evaluateAndPersist with executeActions false never invokes the action dispatcher, even for a matching non-dry-run rule`() = runTest(testDispatcher) {
        // Given: a saved notification and a matching, non-dry-run rule with an enabled action
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val action = createTestAction(id = "action-1")
        val rule = createTestRule(id = "rule-1", isDryRun = false, conditions = listOf(condition), actions = listOf(action))

        coEvery { ruleRepository.getRulesForApp(notification.packageName) } returns Result.success(listOf(rule))
        coEvery { ruleExecutionRepository.saveExecution(any(), any()) } returns Result.success(Unit)

        // When: re-evaluating with executeActions = false (the refresh path)
        val result = useCase.evaluateAndPersist(notification, executeActions = false)

        // Then: the match is still recorded, but with no action outcomes, and the dispatcher is
        // never invoked - refresh must never replay a real action a second time
        result.isSuccess shouldBe true
        val execution = result.getOrThrow().single()
        execution.actionOutcomes shouldBe emptyMap()
        coVerify(exactly = 0) { actionDispatcher.executeAll(any(), any()) }
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
    fun `fields are persisted when the matched rule has an enabled Extract-data action`() = runTest(testDispatcher) {
        // Given: a matching rule with fields and an enabled SAVE_DATA (Extract data) action
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val fields = listOf(createTestField(method = RuleField.ExtractionMethod.RegexPattern("\\d+")))
        val action = createTestAction(id = "action-1", type = ActionType.SAVE_DATA, isEnabled = true, fields = fields)
        val rule = createTestRule(id = "rule-1", conditions = listOf(condition), actions = listOf(action))

        coEvery { deduplicator.isDuplicate(notification) } returns false
        coEvery { notificationRepository.saveNotification(notification) } returns Result.success(Unit)
        coEvery { ruleRepository.getRulesForApp(notification.packageName) } returns Result.success(listOf(rule))
        coEvery { actionDispatcher.executeAll(notification, rule.actions) } returns mapOf("action-1" to ActionOutcome.SUCCESS)
        coEvery { ruleExecutionRepository.saveExecution(any(), any()) } returns Result.success(Unit)

        // When: invoking the use case
        useCase.invoke(notification)

        // Then: the execution is saved with the rule's fields
        coVerify(exactly = 1) { ruleExecutionRepository.saveExecution(any(), fields) }
    }

    @Test
    fun `fields are not persisted when no enabled Extract-data action is present`() = runTest(testDispatcher) {
        // Given: a matching rule with fields but whose only action is a dismiss (no SAVE_DATA)
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val dismiss = createTestAction(id = "dismiss-1", type = ActionType.DISMISS_NOTIFICATION, isEnabled = true)
        val rule = createTestRule(id = "rule-1", conditions = listOf(condition), actions = listOf(dismiss))

        coEvery { deduplicator.isDuplicate(notification) } returns false
        coEvery { notificationRepository.saveNotification(notification) } returns Result.success(Unit)
        coEvery { ruleRepository.getRulesForApp(notification.packageName) } returns Result.success(listOf(rule))
        coEvery { actionDispatcher.executeAll(notification, rule.actions) } returns mapOf("dismiss-1" to ActionOutcome.SUCCESS)
        coEvery { ruleExecutionRepository.saveExecution(any(), any()) } returns Result.success(Unit)

        // When: invoking the use case
        useCase.invoke(notification)

        // Then: the execution is still saved (dismiss ran), but with no fields to persist
        coVerify(exactly = 1) { ruleExecutionRepository.saveExecution(any(), emptyList()) }
    }

    @Test
    fun `fields are not persisted when the Extract-data action is disabled`() = runTest(testDispatcher) {
        // Given: a matching rule with fields and a disabled SAVE_DATA action
        val notification = createTestNotification(title = "ICA Kvantum")
        val condition = createTestCondition(
            condition = MatchingCondition.TITLE,
            operator = MatchingOperator.CONTAINS,
            value = "ICA",
        )
        val fields = listOf(createTestField(method = RuleField.ExtractionMethod.RegexPattern("\\d+")))
        val disabledSaveData = createTestAction(id = "save-1", type = ActionType.SAVE_DATA, isEnabled = false, fields = fields)
        val rule = createTestRule(id = "rule-1", conditions = listOf(condition), actions = listOf(disabledSaveData))

        coEvery { deduplicator.isDuplicate(notification) } returns false
        coEvery { notificationRepository.saveNotification(notification) } returns Result.success(Unit)
        coEvery { ruleRepository.getRulesForApp(notification.packageName) } returns Result.success(listOf(rule))
        coEvery { actionDispatcher.executeAll(notification, rule.actions) } returns emptyMap()
        coEvery { ruleExecutionRepository.saveExecution(any(), any()) } returns Result.success(Unit)

        // When: invoking the use case
        useCase.invoke(notification)

        // Then: no fields are persisted
        coVerify(exactly = 1) { ruleExecutionRepository.saveExecution(any(), emptyList()) }
    }

    @Test
    fun `evaluate is called with timeProvider's now`() = runTest(testDispatcher) {
        // Given: a use case wired with a mocked RuleEngine, so the exact call args are observable
        val mockRuleEngine: RuleEngine = mockk()
        val useCaseWithMockedEngine = ProcessNotificationUseCase(
            deduplicator = deduplicator,
            notificationRepository = notificationRepository,
            ruleRepository = ruleRepository,
            ruleEngine = mockRuleEngine,
            ruleExecutionRepository = ruleExecutionRepository,
            actionDispatcher = actionDispatcher,
            timeProvider = timeProvider,
            ioDispatcher = testDispatcher,
        )
        val notification = createTestNotification()
        val rule = createTestRule(id = "rule-1")
        coEvery { deduplicator.isDuplicate(notification) } returns false
        coEvery { notificationRepository.saveNotification(notification) } returns Result.success(Unit)
        coEvery { ruleRepository.getRulesForApp(notification.packageName) } returns Result.success(listOf(rule))
        every { mockRuleEngine.evaluate(notification, listOf(rule), fixedNow) } returns emptyList()

        // When: invoking the use case
        useCaseWithMockedEngine.invoke(notification)

        // Then: RuleEngine.evaluate was called with timeProvider.now()
        verify(exactly = 1) { mockRuleEngine.evaluate(notification, listOf(rule), fixedNow) }
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
