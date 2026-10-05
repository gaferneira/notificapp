package dev.gaferneira.notificapp.features.notificationdetail.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.extraction.ExtractionPreviewer
import dev.gaferneira.notificapp.core.extraction.RuleEngine
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.model.ActionOutcome
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.FieldChange
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.LoadStatus
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.Message
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.PreviewFailure
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEffect
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEvent
import dev.gaferneira.notificapp.testutil.createTestAction
import dev.gaferneira.notificapp.testutil.createTestCondition
import dev.gaferneira.notificapp.testutil.createTestField
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.createTestRule
import dev.gaferneira.notificapp.testutil.fakes.FakeNotificationRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeRuleExecutionRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeRuleRepository
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationDetailViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var notificationRepository: FakeNotificationRepository
    private lateinit var ruleExecutionRepository: FakeRuleExecutionRepository
    private lateinit var ruleRepository: FakeRuleRepository
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var viewModel: NotificationDetailViewModel

    private val amountField = createTestField(
        id = "f-amount",
        name = "Amount",
        fieldType = RuleField.FieldType.NUMBER,
        method = RuleField.ExtractionMethod.RegexPattern("(\\d+)"),
    )

    private val notification = createTestNotification(id = "notif-1", title = "ICA Kvantum", rawContent = "ICA Kvantum 123", timestamp = 1_700_000_000_000L)

    @Suppress("LongParameterList")
    private fun rule(
        id: String = "rule-1",
        name: String = "ICA rule",
        isActive: Boolean = true,
        deleteRaw: Boolean = false,
        fields: List<RuleField> = listOf(amountField),
        matchValue: String = "ICA",
    ): Rule = createTestRule(
        id = id,
        name = name,
        isActive = isActive,
        deleteRawContentAfterExtraction = deleteRaw,
        conditions = listOf(createTestCondition(condition = MatchingCondition.TITLE, operator = MatchingOperator.CONTAINS, value = matchValue)),
        actions = listOf(
            createTestAction(id = "save-$id", type = ActionType.SAVE_DATA, fields = fields),
            createTestAction(id = "dismiss-$id", type = ActionType.DISMISS_NOTIFICATION),
        ),
    )

    private fun execution(
        ruleId: String = "rule-1",
        data: Map<String, String> = mapOf("f-amount" to "123"),
        actions: List<String> = listOf("save-$ruleId", "dismiss-$ruleId"),
    ) = RuleExecution(
        id = "exec-$ruleId",
        notificationId = "notif-1",
        ruleId = ruleId,
        extractedData = data,
        triggeredActions = actions,
        actionOutcomes = actions.associateWith { ActionOutcome.SUCCESS },
        createdAt = 5_000L,
    )

    private fun createViewModel(
        notifications: List<Notification> = listOf(notification),
        rules: List<Rule> = emptyList(),
        executions: List<RuleExecution> = emptyList(),
    ) {
        notificationRepository = FakeNotificationRepository(initial = notifications)
        ruleExecutionRepository = FakeRuleExecutionRepository(initial = mapOf("notif-1" to executions))
        ruleRepository = FakeRuleRepository(initial = rules)
        navigationHandler = mockk(relaxed = true)
        viewModel = NotificationDetailViewModel(
            notificationRepository = notificationRepository,
            ruleExecutionRepository = ruleExecutionRepository,
            ruleRepository = ruleRepository,
            extractionPreviewer = ExtractionPreviewer(RuleEngine()),
            navigationHandler = navigationHandler,
            ioDispatcher = testDispatcher,
        )
    }

    private fun load(id: String = "notif-1") {
        viewModel.setNotificationId(id)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    private fun send(event: UiEvent) {
        viewModel.onEvent(event)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class LoadTests {

        @Test
        fun `loading a notification populates state and exposes timestamp and package`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution()))
            load()

            val state = viewModel.uiState.value
            state.loadStatus shouldBe LoadStatus.LOADED
            state.isLoading shouldBe false
            state.notification shouldBe notification
            state.postedAt shouldBe 1_700_000_000_000L
            state.sourcePackageName shouldBe "com.test.app"
            state.executions.size shouldBe 1
        }

        @Test
        fun `an unknown notification id is NotFound, not an error`() {
            createViewModel()
            load("missing")

            viewModel.uiState.value.loadStatus shouldBe LoadStatus.NOT_FOUND
        }

        @Test
        fun `a notification deleted while the screen is open becomes Deleted`() = runTest(testDispatcher) {
            createViewModel()
            load()
            viewModel.uiState.value.loadStatus shouldBe LoadStatus.LOADED

            // When: retention cleanup removes it
            notificationRepository.deleteNotification("notif-1")
            advanceUntilIdle()

            viewModel.uiState.value.loadStatus shouldBe LoadStatus.DELETED
            viewModel.uiState.value.notification shouldBe null
        }

        @Test
        fun `a storage failure is an Error and Retry reloads it`() {
            createViewModel()
            notificationRepository.observeError = IllegalStateException("db")
            load()
            viewModel.uiState.value.loadStatus shouldBe LoadStatus.ERROR

            notificationRepository.observeError = null
            send(UiEvent.OnRetryClicked)

            viewModel.uiState.value.loadStatus shouldBe LoadStatus.LOADED
        }

        @Test
        fun `a rule lookup failure is an Error`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution()))
            ruleRepository.getRulesError = IllegalStateException("db")
            load()

            viewModel.uiState.value.loadStatus shouldBe LoadStatus.ERROR
        }

        @Test
        fun `Retry is ignored unless the screen is in Error`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution()))
            load()
            val callsBefore = ruleRepository.getRulesCallCount

            send(UiEvent.OnRetryClicked)

            ruleRepository.getRulesCallCount shouldBe callsBefore
            viewModel.uiState.value.loadStatus shouldBe LoadStatus.LOADED
        }

        @Test
        fun `switching notification id cancels the previous observation and resets state`() {
            val other = createTestNotification(id = "notif-2", title = "Other", rawContent = "Other")
            createViewModel(notifications = listOf(notification, other), rules = listOf(rule()), executions = listOf(execution()))
            load()
            send(UiEvent.OnTestRulesClicked)
            (viewModel.uiState.value.preview != null) shouldBe true

            load("notif-2")

            val state = viewModel.uiState.value
            state.notification?.id shouldBe "notif-2"
            state.executions shouldBe emptyList()
            state.preview shouldBe null
            state.loadStatus shouldBe LoadStatus.LOADED

            // And: changes to the old notification no longer reach the state
            notificationRepository.setNotifications(listOf(notification.copy(title = "changed"), other))
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.notification?.id shouldBe "notif-2"
        }
    }

    @Nested
    inner class MappingTests {

        @Test
        fun `fields and actions resolve to the rule's real names and types`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution()))
            load()

            val details = viewModel.uiState.value.executions.single()
            details.ruleName shouldBe "ICA rule"
            details.isRuleDeleted shouldBe false
            val field = details.extractedFields.single()
            field.fieldName shouldBe "Amount"
            field.fieldType shouldBe RuleField.FieldType.NUMBER
            field.value shouldBe "123"
            details.triggeredActions.map { it.type } shouldBe listOf(ActionType.SAVE_DATA, ActionType.DISMISS_NOTIFICATION)
            details.triggeredActions.map { it.outcome } shouldBe listOf(ActionOutcome.SUCCESS, ActionOutcome.SUCCESS)
        }

        @Test
        fun `a field deleted from the rule falls back to an unnamed STRING`() {
            createViewModel(
                rules = listOf(rule(fields = emptyList())),
                executions = listOf(execution(data = mapOf("f-gone" to "x"))),
            )
            load()

            val field = viewModel.uiState.value.executions.single().extractedFields.single()
            field.fieldName shouldBe null
            field.isFieldDeleted shouldBe true
            field.fieldType shouldBe RuleField.FieldType.STRING
            field.fieldId shouldBe "f-gone"
        }

        @Test
        fun `an action removed from the rule has a null type but keeps its outcome`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution(actions = listOf("removed-action"))))
            load()

            val action = viewModel.uiState.value.executions.single().triggeredActions.single()
            action.type shouldBe null
            action.actionId shouldBe "removed-action"
            action.outcome shouldBe ActionOutcome.SUCCESS
        }

        @Test
        fun `a deleted rule has a null name`() {
            createViewModel(rules = emptyList(), executions = listOf(execution()))
            load()

            viewModel.uiState.value.executions.single().isRuleDeleted shouldBe true
        }

        @Test
        fun `rules are loaded with one batched call per emission, not one per execution`() {
            createViewModel(
                rules = listOf(rule("rule-1"), rule("rule-2", name = "Second")),
                executions = listOf(execution("rule-1"), execution("rule-2"), execution("rule-1").copy(id = "exec-extra")),
            )
            load()
            ruleRepository.getRulesCallCount shouldBe 1

            // When: a new emission arrives
            ruleExecutionRepository.setExecutions("notif-1", listOf(execution("rule-1")))
            testDispatcher.scheduler.advanceUntilIdle()

            ruleRepository.getRulesCallCount shouldBe 2
        }

        @Test
        fun `a redacted notification exposes the rule that scrubbed it`() {
            val redacted = notification.copy(rawContent = "", content = null, title = null)
            createViewModel(
                notifications = listOf(redacted),
                rules = listOf(rule(deleteRaw = true), rule("rule-2", name = "Other")),
                executions = listOf(execution("rule-2", data = emptyMap()), execution("rule-1")),
            )
            load()

            val state = viewModel.uiState.value
            state.isContentRedacted shouldBe true
            state.redactedByRule?.id shouldBe "rule-1"
            state.redactedByRule?.name shouldBe "ICA rule"
            state.executions.filter { it.wasRedactionSource }.map { it.ruleId } shouldBe listOf("rule-1")
        }

        @Test
        fun `a normal notification is not redacted`() {
            createViewModel(rules = listOf(rule(deleteRaw = true)), executions = listOf(execution()))
            load()

            viewModel.uiState.value.isContentRedacted shouldBe false
            viewModel.uiState.value.redactedByRule shouldBe null
        }
    }

    @Nested
    inner class PreviewTests {

        @Test
        fun `testing rules is read-only and fills the preview with the diff`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution(data = mapOf("f-amount" to "99"))))
            load()
            val executionsBefore = ruleExecutionRepository.currentExecutions("notif-1")

            send(UiEvent.OnTestRulesClicked)

            val state = viewModel.uiState.value
            state.isPreviewLoading shouldBe false
            val match = state.preview!!.matches.single()
            match.ruleName shouldBe "ICA rule"
            match.actionTypes shouldBe listOf(ActionType.SAVE_DATA, ActionType.DISMISS_NOTIFICATION)
            match.fieldDiffs.single().change shouldBe FieldChange.CHANGED
            state.canApplyPreview shouldBe true
            // Nothing persisted
            ruleExecutionRepository.appliedUpdates shouldBe emptyList()
            ruleExecutionRepository.currentExecutions("notif-1") shouldBe executionsBefore
        }

        @Test
        fun `inactive rules are not part of the preview`() {
            createViewModel(rules = listOf(rule(isActive = false)), executions = listOf(execution()))
            load()

            send(UiEvent.OnTestRulesClicked)

            viewModel.uiState.value.preview!!.matches shouldBe emptyList()
        }

        @Test
        fun `preview loading is inline and never replaces the screen`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution()))
            load()

            viewModel.onEvent(UiEvent.OnTestRulesClicked)

            // Before the work runs: only the inline flag is set, the screen stays LOADED
            val state = viewModel.uiState.value
            state.isPreviewLoading shouldBe true
            state.loadStatus shouldBe LoadStatus.LOADED
            state.notification shouldBe notification
            testDispatcher.scheduler.advanceUntilIdle()
        }

        @Test
        fun `a redacted notification cannot be tested`() {
            createViewModel(
                notifications = listOf(notification.copy(rawContent = "", content = null, title = null)),
                rules = listOf(rule()),
                executions = listOf(execution()),
            )
            load()

            send(UiEvent.OnTestRulesClicked)

            val state = viewModel.uiState.value
            state.canTestRules shouldBe false
            state.preview shouldBe null
            state.previewFailure shouldBe PreviewFailure.CONTENT_REDACTED
        }

        @Test
        fun `a rule loading failure surfaces as a preview failure`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution()))
            load()
            ruleRepository.rulesForAppError = IllegalStateException("db")

            send(UiEvent.OnTestRulesClicked)

            val state = viewModel.uiState.value
            state.previewFailure shouldBe PreviewFailure.EVALUATION_FAILED
            state.isPreviewLoading shouldBe false
            state.loadStatus shouldBe LoadStatus.LOADED
        }

        @Test
        fun `dismissing the preview clears it`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution()))
            load()
            send(UiEvent.OnTestRulesClicked)

            send(UiEvent.OnDismissPreview)

            viewModel.uiState.value.preview shouldBe null
        }
    }

    @Nested
    inner class UpdateTests {

        @Test
        fun `applying replaces extracted data only, keeping execution identity, outcomes and createdAt`() = runTest(testDispatcher) {
            createViewModel(rules = listOf(rule()), executions = listOf(execution(data = mapOf("f-amount" to "99"))))
            load()
            send(UiEvent.OnTestRulesClicked)
            val before = ruleExecutionRepository.currentExecutions("notif-1").single()

            viewModel.effect.test {
                send(UiEvent.OnUpdateExtractedDataClicked)

                awaitItem() shouldBe UiEffect.ShowMessage(Message.EXTRACTED_DATA_UPDATED)
            }

            val after = ruleExecutionRepository.currentExecutions("notif-1").single()
            after.extractedData shouldBe mapOf("f-amount" to "123")
            after.copy(extractedData = before.extractedData) shouldBe before
            ruleExecutionRepository.appliedUpdates.single().single().executionId shouldBe before.id
            viewModel.uiState.value.preview shouldBe null
            viewModel.uiState.value.isApplyingUpdate shouldBe false
        }

        @Test
        fun `update is disabled and ignored when nothing changed`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution()))
            load()
            send(UiEvent.OnTestRulesClicked)

            viewModel.uiState.value.canApplyPreview shouldBe false
            send(UiEvent.OnUpdateExtractedDataClicked)

            ruleExecutionRepository.appliedUpdates shouldBe emptyList()
        }

        @Test
        fun `a rule that never ran is not persisted, so the update stays disabled`() {
            createViewModel(rules = listOf(rule()), executions = emptyList())
            load()
            send(UiEvent.OnTestRulesClicked)

            viewModel.uiState.value.preview!!.matches.single().executionId shouldBe null
            viewModel.uiState.value.canApplyPreview shouldBe false
            send(UiEvent.OnUpdateExtractedDataClicked)
            ruleExecutionRepository.appliedUpdates shouldBe emptyList()
            ruleExecutionRepository.currentExecutions("notif-1") shouldBe emptyList()
        }

        @Test
        fun `update is disabled once the content has been redacted`() {
            createViewModel(rules = listOf(rule()), executions = listOf(execution(data = mapOf("f-amount" to "99"))))
            load()
            send(UiEvent.OnTestRulesClicked)
            viewModel.uiState.value.canApplyPreview shouldBe true

            // When: a rule scrubs the notification while the preview is showing
            notificationRepository.setNotifications(listOf(notification.copy(rawContent = "", content = null, title = null)))
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.canApplyPreview shouldBe false
            state.preview shouldBe null
            send(UiEvent.OnUpdateExtractedDataClicked)
            ruleExecutionRepository.appliedUpdates shouldBe emptyList()
        }

        @Test
        fun `a failed update reports the error and keeps the preview`() = runTest(testDispatcher) {
            createViewModel(rules = listOf(rule()), executions = listOf(execution(data = mapOf("f-amount" to "99"))))
            load()
            send(UiEvent.OnTestRulesClicked)
            ruleExecutionRepository.updateError = IllegalStateException("db")

            viewModel.effect.test {
                send(UiEvent.OnUpdateExtractedDataClicked)

                awaitItem() shouldBe UiEffect.ShowMessage(Message.UPDATE_FAILED)
            }

            (viewModel.uiState.value.preview != null) shouldBe true
            viewModel.uiState.value.isApplyingUpdate shouldBe false
            ruleExecutionRepository.currentExecutions("notif-1").single().extractedData shouldBe mapOf("f-amount" to "99")
        }
    }

    @Nested
    inner class ActionTests {

        @Test
        fun `deleting the notification navigates back without flashing Deleted`() = runTest(testDispatcher) {
            createViewModel()
            load()

            viewModel.effect.test {
                send(UiEvent.OnDeleteNotificationClicked)

                awaitItem() shouldBe UiEffect.NavigateBack
            }

            notificationRepository.currentNotifications() shouldBe emptyList()
            viewModel.uiState.value.loadStatus shouldBe LoadStatus.LOADED
        }

        @Test
        fun `a failed delete reports the error and keeps observing`() = runTest(testDispatcher) {
            createViewModel()
            load()
            notificationRepository.deleteError = IllegalStateException("db")

            viewModel.effect.test {
                send(UiEvent.OnDeleteNotificationClicked)

                awaitItem() shouldBe UiEffect.ShowMessage(Message.DELETE_FAILED)
            }
        }

        @Test
        fun `opening the source app emits OpenApp with its package`() = runTest(testDispatcher) {
            createViewModel()
            load()

            viewModel.effect.test {
                send(UiEvent.OnOpenSourceAppClicked)

                awaitItem() shouldBe UiEffect.OpenApp("com.test.app")
            }
        }

        @Test
        fun `opening a rule navigates to its details`() {
            createViewModel()
            load()

            send(UiEvent.OnOpenRuleClicked("rule-1"))

            coVerify { navigationHandler.navigate(Routes.ruleDetails("rule-1")) }
        }

        @Test
        fun `create rule navigates to the editor pre-filled from this notification`() {
            createViewModel()
            load()

            send(UiEvent.OnCreateRuleClicked)

            coVerify { navigationHandler.navigate(Routes.ruleEditor(notificationId = "notif-1")) }
        }

        @Test
        fun `back navigates back`() {
            createViewModel()
            load()

            send(UiEvent.OnBackClicked)

            coVerify { navigationHandler.goBack() }
        }
    }
}
