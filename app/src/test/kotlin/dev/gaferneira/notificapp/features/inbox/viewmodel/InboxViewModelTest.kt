package dev.gaferneira.notificapp.features.inbox.viewmodel

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.preferences.InboxFilterSettings
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import dev.gaferneira.notificapp.features.inbox.contract.InboxEffect
import dev.gaferneira.notificapp.features.inbox.contract.InboxEvent
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilter
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterChip
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
    private val appsFlow = MutableStateFlow<List<AppInfo>>(emptyList())

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        notificationRepository = mockk {
            every { observeAppsWithNotifications() } returns appsFlow
        }
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

    private fun createViewModel(
        listenerEnabled: Boolean = true,
        initialStatus: Status? = null,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): InboxViewModel = InboxViewModel(
        initialStatus = initialStatus,
        savedStateHandle = savedStateHandle,
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

            viewModel.uiState.value.filter shouldBe InboxFilter()
            viewModel.uiState.value.searchQuery shouldBe ""
        }

        @Test
        fun `hydrates state from saved filters on init`() = runTest(testDispatcher) {
            savedFiltersFlow.value = InboxFilterSettings(selectedApps = listOf("com.bank"), statusFilter = Status.UNPROCESSED)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.filter shouldBe InboxFilter(setOf("com.bank"), Status.UNPROCESSED)
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
        fun `applying a filter persists it to preferences`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            val slot = slot<InboxFilterSettings>()

            viewModel.onEvent(InboxEvent.OnFilterChange(InboxFilter(setOf("com.bank"), Status.PROCESSED)))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { userPreferencesRepository.setInboxFilters(capture(slot)) }
            slot.captured.selectedApps shouldBe listOf("com.bank")
            slot.captured.statusFilter shouldBe Status.PROCESSED
        }

        @Test
        fun `removing an app chip persists the filter without that app`() = runTest(testDispatcher) {
            savedFiltersFlow.value = InboxFilterSettings(listOf("com.a", "com.b"), Status.PROCESSED)
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val slot = slot<InboxFilterSettings>()

            viewModel.onEvent(InboxEvent.OnRemoveFilter(InboxFilterChip.App("com.a")))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { userPreferencesRepository.setInboxFilters(capture(slot)) }
            slot.captured shouldBe InboxFilterSettings(listOf("com.b"), Status.PROCESSED)
        }

        @Test
        fun `removing the status chip persists the filter with status ALL`() = runTest(testDispatcher) {
            savedFiltersFlow.value = InboxFilterSettings(listOf("com.a"), Status.UNPROCESSED)
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            val slot = slot<InboxFilterSettings>()

            viewModel.onEvent(InboxEvent.OnRemoveFilter(InboxFilterChip.Status(Status.UNPROCESSED)))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify { userPreferencesRepository.setInboxFilters(capture(slot)) }
            slot.captured shouldBe InboxFilterSettings(listOf("com.a"), Status.ALL)
        }

        @Test
        fun `a persisted filter change flows back into the state`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            savedFiltersFlow.value = InboxFilterSettings(listOf("com.a"), Status.PROCESSED)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.filter.activeFilterCount() shouldBe 2
        }

        @Test
        fun `app names for the chips come from the apps with notifications`() = runTest(testDispatcher) {
            appsFlow.value = listOf(AppInfo("com.bank", "Bank"))
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.appNames shouldBe mapOf("com.bank" to "Bank")
        }

        @Test
        fun `OnClearFilters resets the search and persists a default filter`() = runTest(testDispatcher) {
            savedFiltersFlow.value = InboxFilterSettings(listOf("com.a"), Status.UNPROCESSED)
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onEvent(InboxEvent.OnSearchQueryChange("purchase"))
            val slot = slot<InboxFilterSettings>()

            viewModel.onEvent(InboxEvent.OnClearFilters)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.searchQuery shouldBe ""
            coVerify { userPreferencesRepository.setInboxFilters(capture(slot)) }
            slot.captured shouldBe InboxFilterSettings()
        }

        @Test
        fun `OnClearFilters with only a search query resets the search and still persists defaults`() = runTest(testDispatcher) {
            val viewModel = createViewModel()
            viewModel.onEvent(InboxEvent.OnSearchQueryChange("zzz"))

            viewModel.onEvent(InboxEvent.OnClearFilters)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.searchQuery shouldBe ""
            coVerify { userPreferencesRepository.setInboxFilters(InboxFilterSettings()) }
        }

        @Test
        fun `updating the search query stores it in state`() {
            val viewModel = createViewModel()

            viewModel.onEvent(InboxEvent.OnSearchQueryChange("purchase"))

            viewModel.uiState.value.searchQuery shouldBe "purchase"
        }
    }

    @Nested
    inner class InitialStatusTests {

        @Test
        fun `initial status is the filter from the very first state`() {
            savedFiltersFlow.value = InboxFilterSettings(listOf("com.bank"), Status.UNPROCESSED)

            val viewModel = createViewModel(initialStatus = Status.PROCESSED)

            viewModel.uiState.value.filter shouldBe InboxFilter(status = Status.PROCESSED)
        }

        @Test
        fun `initial status overrides the saved filter and is not persisted`() = runTest(testDispatcher) {
            savedFiltersFlow.value = InboxFilterSettings(listOf("com.bank"), Status.UNPROCESSED)
            val viewModel = createViewModel(initialStatus = Status.PROCESSED)

            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.filter shouldBe InboxFilter(status = Status.PROCESSED)
            coVerify(exactly = 0) { userPreferencesRepository.setInboxFilters(any()) }
        }

        @Test
        fun `initial status survives a later saved-filter emission`() = runTest(testDispatcher) {
            val viewModel = createViewModel(initialStatus = Status.PROCESSED)
            testDispatcher.scheduler.advanceUntilIdle()

            savedFiltersFlow.value = InboxFilterSettings(listOf("com.a"), Status.UNPROCESSED)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.filter shouldBe InboxFilter(status = Status.PROCESSED)
        }

        @Test
        fun `a restored ViewModel keeps the user's edit instead of re-applying the route status`() = runTest(testDispatcher) {
            val savedStateHandle = SavedStateHandle()
            val viewModel = createViewModel(initialStatus = Status.PROCESSED, savedStateHandle = savedStateHandle)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onEvent(InboxEvent.OnFilterChange(InboxFilter(status = Status.UNPROCESSED)))
            savedFiltersFlow.value = InboxFilterSettings(statusFilter = Status.UNPROCESSED)
            testDispatcher.scheduler.advanceUntilIdle()

            // Same route arg, same saved state: what process-death restoration hands the new instance.
            val restored = createViewModel(initialStatus = Status.PROCESSED, savedStateHandle = savedStateHandle)
            testDispatcher.scheduler.advanceUntilIdle()

            restored.uiState.value.filter shouldBe InboxFilter(status = Status.UNPROCESSED)
        }

        @Test
        fun `a filter edit after the initial status is persisted and shown`() = runTest(testDispatcher) {
            val viewModel = createViewModel(initialStatus = Status.PROCESSED)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(InboxEvent.OnFilterChange(InboxFilter(setOf("com.a"), Status.PROCESSED)))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.filter shouldBe InboxFilter(setOf("com.a"), Status.PROCESSED)
            coVerify { userPreferencesRepository.setInboxFilters(InboxFilterSettings(listOf("com.a"), Status.PROCESSED)) }
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
                initialStatus = null,
                savedStateHandle = SavedStateHandle(),
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
