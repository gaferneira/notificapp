package dev.gaferneira.notificapp.features.notificationdetail.viewmodel

import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.UiText
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.domain.action.RuleReEvaluator
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEvent
import dev.gaferneira.notificapp.testutil.createTestNotification
import dev.gaferneira.notificapp.testutil.fakes.FakeNotificationRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeRuleExecutionRepository
import dev.gaferneira.notificapp.testutil.fakes.FakeRuleRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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
    private lateinit var ruleReEvaluator: RuleReEvaluator
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var viewModel: NotificationDetailViewModel

    private val notification = createTestNotification(id = "notif-1")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        notificationRepository = FakeNotificationRepository(initial = listOf(notification))
        ruleExecutionRepository = FakeRuleExecutionRepository()
        ruleRepository = FakeRuleRepository()
        ruleReEvaluator = mockk()
        navigationHandler = mockk()

        viewModel = NotificationDetailViewModel(
            notificationRepository = notificationRepository,
            ruleExecutionRepository = ruleExecutionRepository,
            ruleRepository = ruleRepository,
            ruleReEvaluator = ruleReEvaluator,
            navigationHandler = navigationHandler,
            ioDispatcher = testDispatcher,
        )
        viewModel.setNotificationId("notif-1")
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Nested
    inner class LoadTests {

        @Test
        fun `loading a notification successfully populates state and clears loading`() {
            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.notification shouldBe notification
            state.executions shouldBe emptyList()
        }

        @Test
        fun `loading fails when the notification is not found`() = runTest(testDispatcher) {
            viewModel.setNotificationId("missing")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.error.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.notification_detail_error_not_found
        }
    }

    @Nested
    inner class RefreshTests {

        @Test
        fun `refresh clears the loading spinner even when the executions flow does not re-emit`() = runTest(testDispatcher) {
            // Given: zero executions before refresh, and the re-run also matches zero rules, so
            // neither the delete nor the (skipped) insert touch any row - Room's invalidation
            // trigger never fires and the observed Flow never re-emits (regression test for the
            // bug where isLoading was only ever cleared by that re-emission)
            coEvery { ruleReEvaluator.reEvaluate(notification) } returns Result.success(emptyList())

            viewModel.onEvent(UiEvent.OnRefreshClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isLoading shouldBe false
        }

        @Test
        fun `refresh never dispatches actions, even for a matching non-dry-run rule`() = runTest(testDispatcher) {
            // Given: refresh re-evaluates a rule that would normally execute a real action
            coEvery { ruleReEvaluator.reEvaluate(notification) } returns Result.success(emptyList())

            // When: refreshing
            viewModel.onEvent(UiEvent.OnRefreshClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            // Then: reEvaluate (which never dispatches actions) was called - refresh must never
            // replay alarms/snoozes/dismisses for a notification already acted on once
            coVerify(exactly = 1) { ruleReEvaluator.reEvaluate(notification) }
        }

        @Test
        fun `refresh failure while deleting existing executions surfaces an error`() = runTest(testDispatcher) {
            ruleExecutionRepository.deleteError = IllegalStateException("db error")

            viewModel.onEvent(UiEvent.OnRefreshClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.error.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.notification_detail_error_refresh
        }

        @Test
        fun `refresh failure while re-evaluating rules surfaces an error`() = runTest(testDispatcher) {
            coEvery {
                ruleReEvaluator.reEvaluate(notification)
            } returns Result.failure(IllegalStateException("eval error"))

            viewModel.onEvent(UiEvent.OnRefreshClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.error.shouldBeInstanceOf<UiText.StringResource>().id shouldBe R.string.notification_detail_error_refresh
        }
    }
}
