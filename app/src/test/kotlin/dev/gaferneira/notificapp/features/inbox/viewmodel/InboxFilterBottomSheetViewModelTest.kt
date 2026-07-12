package dev.gaferneira.notificapp.features.inbox.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract.UiEffect
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract.UiEvent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter as Status

@OptIn(ExperimentalCoroutinesApi::class)
class InboxFilterBottomSheetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val notificationRepository: NotificationRepository = mockk()
    private lateinit var viewModel: InboxFilterBottomSheetViewModel

    private fun createViewModel(apps: List<AppInfo> = emptyList()): InboxFilterBottomSheetViewModel {
        every { notificationRepository.observeAppsWithNotifications() } returns flowOf(apps)
        return InboxFilterBottomSheetViewModel(notificationRepository)
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads available apps from the repository on construction`() = runTest(testDispatcher) {
        viewModel = createViewModel(apps = listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank")))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.uiState.value.availableApps.map { it.name } shouldBe listOf("Alpha", "Bank")
    }

    @Test
    fun `init hydrates selected apps and status filter, and sets hasActiveFilters`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UiEvent.Init(currentSelectedApps = listOf("com.a"), currentStatusFilter = Status.UNPROCESSED))

        val state = viewModel.uiState.value
        state.selectedApps shouldBe setOf("com.a")
        state.statusFilter shouldBe Status.UNPROCESSED
        state.hasActiveFilters shouldBe true
    }

    @Test
    fun `init with no selection and ALL status leaves hasActiveFilters false`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UiEvent.Init(currentSelectedApps = emptyList(), currentStatusFilter = Status.ALL))

        viewModel.uiState.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `toggling an app flips hasActiveFilters on and back off`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.uiState.value.hasActiveFilters shouldBe true
        viewModel.uiState.value.selectedApps shouldBe setOf("com.a")

        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.uiState.value.hasActiveFilters shouldBe false
        viewModel.uiState.value.selectedApps shouldBe emptySet()
    }

    @Test
    fun `changing status away from ALL sets hasActiveFilters`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UiEvent.OnStatusChange(Status.UNPROCESSED))
        viewModel.uiState.value.hasActiveFilters shouldBe true

        viewModel.onEvent(UiEvent.OnStatusChange(Status.ALL))
        viewModel.uiState.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `clear all resets selected apps, status filter, and hasActiveFilters`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.onEvent(UiEvent.OnStatusChange(Status.UNPROCESSED))

        viewModel.onEvent(UiEvent.OnClearAll)

        val state = viewModel.uiState.value
        state.selectedApps shouldBe emptySet()
        state.statusFilter shouldBe Status.ALL
        state.hasActiveFilters shouldBe false
    }

    @Test
    fun `apply emits ApplyFilter carrying the current selection`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEvent(UiEvent.OnAppToggle("com.a"))
        viewModel.onEvent(UiEvent.OnStatusChange(Status.UNPROCESSED))

        viewModel.effect.test {
            viewModel.onEvent(UiEvent.OnApply)
            testDispatcher.scheduler.advanceUntilIdle()

            val effect = awaitItem().shouldBeInstanceOf<UiEffect.ApplyFilter>()
            effect.selectedApps shouldBe listOf("com.a")
            effect.statusFilter shouldBe Status.UNPROCESSED
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dismiss emits Dismiss`() = runTest(testDispatcher) {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.effect.test {
            viewModel.onEvent(UiEvent.OnDismiss)
            testDispatcher.scheduler.advanceUntilIdle()

            awaitItem() shouldBe UiEffect.Dismiss
            cancelAndIgnoreRemainingEvents()
        }
    }
}
