package dev.gaferneira.notificapp.features.notificationdetail.viewmodel

import dev.gaferneira.notificapp.core.notification.ProcessNotificationUseCase
import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import dev.gaferneira.notificapp.domain.repository.RuleRepository
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.UiEvent
import dev.gaferneira.notificapp.testutil.createTestNotification
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private lateinit var notificationRepository: NotificationRepository
    private lateinit var ruleExecutionRepository: RuleExecutionRepository
    private lateinit var ruleRepository: RuleRepository
    private lateinit var processNotificationUseCase: ProcessNotificationUseCase
    private lateinit var navigationHandler: NavigationHandler
    private lateinit var executionsFlow: MutableStateFlow<List<RuleExecution>>
    private lateinit var viewModel: NotificationDetailViewModel

    private val notification = createTestNotification(id = "notif-1")

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        notificationRepository = mockk()
        ruleExecutionRepository = mockk()
        ruleRepository = mockk()
        processNotificationUseCase = mockk()
        navigationHandler = mockk()

        executionsFlow = MutableStateFlow(emptyList())
        coEvery { notificationRepository.getNotification("notif-1") } returns Result.success(notification)
        every { ruleExecutionRepository.observeExecutionsForNotification("notif-1") } returns executionsFlow

        viewModel = NotificationDetailViewModel(
            notificationRepository = notificationRepository,
            ruleExecutionRepository = ruleExecutionRepository,
            ruleRepository = ruleRepository,
            processNotificationUseCase = processNotificationUseCase,
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
            coEvery { notificationRepository.getNotification("missing") } returns Result.success(null)

            viewModel.setNotificationId("missing")
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.error shouldBe "Notification not found"
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
            coEvery { ruleExecutionRepository.deleteExecutionsForNotification("notif-1") } returns Result.success(Unit)
            coEvery { processNotificationUseCase.evaluateAndPersist(notification) } returns Result.success(emptyList())

            viewModel.onEvent(UiEvent.OnRefreshClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isLoading shouldBe false
        }

        @Test
        fun `refresh failure while deleting existing executions surfaces an error`() = runTest(testDispatcher) {
            coEvery {
                ruleExecutionRepository.deleteExecutionsForNotification("notif-1")
            } returns Result.failure(IllegalStateException("db error"))

            viewModel.onEvent(UiEvent.OnRefreshClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.error shouldBe "Failed to refresh: db error"
        }

        @Test
        fun `refresh failure while re-evaluating rules surfaces an error`() = runTest(testDispatcher) {
            coEvery { ruleExecutionRepository.deleteExecutionsForNotification("notif-1") } returns Result.success(Unit)
            coEvery {
                processNotificationUseCase.evaluateAndPersist(notification)
            } returns Result.failure(IllegalStateException("eval error"))

            viewModel.onEvent(UiEvent.OnRefreshClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.error shouldBe "Failed to refresh: eval error"
        }
    }
}
