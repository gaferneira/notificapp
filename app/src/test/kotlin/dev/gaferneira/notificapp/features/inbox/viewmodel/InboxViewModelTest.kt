package dev.gaferneira.notificapp.features.inbox.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.preferences.InboxFilterSettings
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import dev.gaferneira.notificapp.features.inbox.contract.InboxEffect
import dev.gaferneira.notificapp.features.inbox.contract.InboxEvent
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
import dev.gaferneira.notificapp.domain.model.preferences.NotificationStatusFilter as Status

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var notificationRepository: NotificationRepository
    private lateinit var userPreferencesRepository: UserPreferencesRepository
    private lateinit var savedFiltersFlow: MutableStateFlow<InboxFilterSettings>

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        notificationRepository = mockk()
        savedFiltersFlow = MutableStateFlow(InboxFilterSettings())
        userPreferencesRepository = mockk {
            every { observeInboxFilters() } returns savedFiltersFlow
            coEvery { setInboxFilters(any()) } returns Result.success(Unit)
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(listenerEnabled: Boolean = true): InboxViewModel = InboxViewModel(
        listenerStatus = NotificationListenerStatusProvider { listenerEnabled },
        notificationRepository = notificationRepository,
        userPreferencesRepository = userPreferencesRepository,
        ioDispatcher = testDispatcher,
    )

    @Nested
    inner class InitialStateTests {

        @Test
        fun `initial state has no filters and empty query`() {
            val viewModel = createViewModel()

            viewModel.uiState.value.selectedApps shouldBe emptyList()
            viewModel.uiState.value.searchQuery shouldBe ""
        }

        @Test
        fun `hydrates state from saved filters on init`() = runTest(testDispatcher) {
            savedFiltersFlow.value = InboxFilterSettings(selectedApps = listOf("com.bank"), statusFilter = Status.UNPROCESSED)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.selectedApps shouldBe listOf("com.bank")
            viewModel.uiState.value.statusFilter shouldBe Status.UNPROCESSED
        }

        @Test
        fun `checks listener status on init`() = runTest(testDispatcher) {
            val viewModel = createViewModel(listenerEnabled = false)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isNotificationListenerActive shouldBe false
        }
    }

    @Nested
    inner class FilterTests {

        @Test
        fun `applying an app filter persists it to preferences`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val slot = slot<InboxFilterSettings>()

            viewModel.onEvent(InboxEvent.OnAppFilterChange(listOf("com.bank"), Status.PROCESSED))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { userPreferencesRepository.setInboxFilters(capture(slot)) }
            slot.captured.selectedApps shouldBe listOf("com.bank")
            slot.captured.statusFilter shouldBe Status.PROCESSED
        }

        @Test
        fun `updating the search query stores it in state`() {
            val viewModel = createViewModel()

            viewModel.onEvent(InboxEvent.OnSearchQueryChange("purchase"))

            viewModel.uiState.value.searchQuery shouldBe "purchase"
        }
    }

    @Nested
    inner class NavigationTests {

        @Test
        fun `clicking a notification emits NavigateToNotificationDetail`() = runTest(testDispatcher) {
            val viewModel = createViewModel()

            viewModel.effect.test {
                viewModel.onEvent(InboxEvent.OnNotificationClick("n-1"))
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe InboxEffect.NavigateToNotificationDetail("n-1")
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class ListenerStatusTests {

        @Test
        fun `OnResume with granted permission sets listener active`() = runTest(testDispatcher) {
            val viewModel = createViewModel(listenerEnabled = true)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(InboxEvent.OnResume)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isNotificationListenerActive shouldBe true
        }

        @Test
        fun `OnResume with revoked permission sets listener inactive`() = runTest(testDispatcher) {
            var enabled = true
            val viewModel = InboxViewModel(
                listenerStatus = NotificationListenerStatusProvider { enabled },
                notificationRepository = notificationRepository,
                userPreferencesRepository = userPreferencesRepository,
                ioDispatcher = testDispatcher,
            )
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.uiState.value.isNotificationListenerActive shouldBe true

            enabled = false
            viewModel.onEvent(InboxEvent.OnResume)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isNotificationListenerActive shouldBe false
        }
    }
}
