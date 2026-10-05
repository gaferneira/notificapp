package dev.gaferneira.notificapp.features.inbox.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilter
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract.UiEffect
import dev.gaferneira.notificapp.features.inbox.contract.InboxFilterContract.UiEvent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    private val apps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Records every (query, packages, isProcessed) the ViewModel asked a live count for. */
    private val countRequests = mutableListOf<Triple<String, List<String>, Boolean?>>()

    /** Fake "database": the live count equals the value mapped from the requested filters. */
    private var countFor: (String, List<String>, Boolean?) -> Int = { _, packages, isProcessed ->
        100 - packages.size * 10 - (if (isProcessed == null) 0 else 1)
    }

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        every { notificationRepository.observeAppsWithNotifications() } returns apps
        every { notificationRepository.observeNotificationCountsByApp() } returns counts
        every { notificationRepository.observeFilteredCount(any(), any(), any()) } answers {
            val query = firstArg<String>()
            val packages = secondArg<List<String>>()
            val isProcessed = thirdArg<Boolean?>()
            countRequests += Triple(query, packages, isProcessed)
            MutableStateFlow(Unit).map { countFor(query, packages, isProcessed) }
        }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.createViewModel(): InboxFilterBottomSheetViewModel {
        val viewModel = InboxFilterBottomSheetViewModel(notificationRepository)
        testDispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun InboxFilterBottomSheetViewModel.send(event: UiEvent) {
        onEvent(event)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun `given apps with notifications, when created, then options are sorted by name and carry counts`() = runTest(testDispatcher) {
        apps.value = listOf(AppInfo("com.b", "bank"), AppInfo("com.a", "Alpha"))
        counts.value = mapOf("com.a" to 5, "com.b" to 2)

        val state = createViewModel().uiState.value

        state.withNotificationsApps.map { it.name to it.notificationCount } shouldBe listOf("Alpha" to 5, "bank" to 2)
        state.hasAppOptions shouldBe true
    }

    @Test
    fun `given no apps, when created, then there are no app options`() = runTest(testDispatcher) {
        val state = createViewModel().uiState.value

        state.hasAppOptions shouldBe false
        state.withNotificationsApps shouldBe emptyList()
    }

    @Test
    fun `given an applied filter, when initialized, then the draft is hydrated and filters are active`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.send(UiEvent.Init(InboxFilter(setOf("com.a"), Status.UNPROCESSED)))

        val state = viewModel.uiState.value
        state.draft shouldBe InboxFilter(setOf("com.a"), Status.UNPROCESSED)
        state.hasActiveFilters shouldBe true
    }

    @Test
    fun `given an empty applied filter, when initialized, then no filter is active`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.send(UiEvent.Init(InboxFilter()))

        viewModel.uiState.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `given a draft, when an app is toggled twice, then it is selected then deselected`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.send(UiEvent.OnAppToggle("com.a"))
        viewModel.uiState.value.draft.selectedApps shouldBe setOf("com.a")
        viewModel.uiState.value.hasActiveFilters shouldBe true

        viewModel.send(UiEvent.OnAppToggle("com.a"))
        viewModel.uiState.value.draft.selectedApps shouldBe emptySet()
        viewModel.uiState.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `given a draft, when the status changes away from ALL and back, then hasActiveFilters follows`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.send(UiEvent.OnStatusChange(Status.UNPROCESSED))
        viewModel.uiState.value.hasActiveFilters shouldBe true

        viewModel.send(UiEvent.OnStatusChange(Status.ALL))
        viewModel.uiState.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `given active filters, when clear all, then the draft is empty`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.send(UiEvent.OnAppToggle("com.a"))
        viewModel.send(UiEvent.OnStatusChange(Status.UNPROCESSED))

        viewModel.send(UiEvent.OnClearAll)

        viewModel.uiState.value.draft shouldBe InboxFilter()
        viewModel.uiState.value.hasActiveFilters shouldBe false
    }

    @Test
    fun `given edits to the draft, when apply, then ApplyFilter carries the draft`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.send(UiEvent.OnAppToggle("com.a"))
        viewModel.send(UiEvent.OnStatusChange(Status.PROCESSED))

        viewModel.effect.test {
            viewModel.send(UiEvent.OnApply)

            val effect = awaitItem().shouldBeInstanceOf<UiEffect.ApplyFilter>()
            effect.filter shouldBe InboxFilter(setOf("com.a"), Status.PROCESSED)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given a draft with edits, when dismissed, then Dismiss is emitted`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.effect.test {
            viewModel.send(UiEvent.OnDismiss)

            awaitItem() shouldBe UiEffect.Dismiss
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `given unapplied edits, when Init is re-sent, then the draft is not reset`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.send(UiEvent.Init(InboxFilter()))
        viewModel.send(UiEvent.OnStatusChange(Status.PROCESSED))

        viewModel.send(UiEvent.Init(InboxFilter()))

        viewModel.uiState.value.draft.status shouldBe Status.PROCESSED
    }

    @Test
    fun `given a sheet that was applied, when opened again, then the draft is hydrated again`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.send(UiEvent.Init(InboxFilter()))
        viewModel.send(UiEvent.OnStatusChange(Status.PROCESSED))
        viewModel.send(UiEvent.OnApply)

        viewModel.send(UiEvent.Init(InboxFilter(status = Status.UNPROCESSED)))

        viewModel.uiState.value.draft.status shouldBe Status.UNPROCESSED
    }

    @Test
    fun `given a sheet that was dismissed, when opened again, then unapplied edits are discarded`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.send(UiEvent.Init(InboxFilter()))
        viewModel.send(UiEvent.OnAppToggle("com.a"))
        viewModel.send(UiEvent.OnDismiss)

        viewModel.send(UiEvent.Init(InboxFilter()))

        viewModel.uiState.value.draft shouldBe InboxFilter()
    }

    @Test
    fun `given a selected app that no longer has notifications, when initialized, then it stays selected with zero notifications`() = runTest(testDispatcher) {
        apps.value = listOf(AppInfo("com.a", "Alpha"))
        counts.value = mapOf("com.a" to 3)
        val viewModel = createViewModel()

        viewModel.send(UiEvent.Init(InboxFilter(selectedApps = setOf("com.gone"))))

        val state = viewModel.uiState.value
        state.draft.selectedApps shouldBe setOf("com.gone")
        state.selectedApps.map { it.packageName to it.notificationCount } shouldBe listOf("com.gone" to 0)
        state.withoutNotificationsApps.map { it.packageName } shouldBe listOf("com.gone")
        state.withNotificationsApps.map { it.packageName } shouldBe listOf("com.a")
    }

    @Test
    fun `given the app picker search text, when it changes, then visible options are filtered but selected apps are kept`() = runTest(testDispatcher) {
        apps.value = listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank"))
        counts.value = mapOf("com.a" to 1, "com.b" to 1)
        val viewModel = createViewModel()
        viewModel.send(UiEvent.OnAppToggle("com.a"))

        viewModel.send(UiEvent.OnAppSearchChange("ban"))

        val state = viewModel.uiState.value
        state.withNotificationsApps.map { it.name } shouldBe listOf("Bank")
        state.selectedApps.map { it.name } shouldBe listOf("Alpha")
        state.hasAppOptions shouldBe true
    }

    @Test
    fun `given the draft, when it changes, then the live count is requested with the draft filters`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.send(UiEvent.Init(InboxFilter(), searchQuery = "bank"))

        viewModel.send(UiEvent.OnAppToggle("com.b"))
        viewModel.send(UiEvent.OnAppToggle("com.a"))
        viewModel.send(UiEvent.OnStatusChange(Status.UNPROCESSED))

        countRequests.last() shouldBe Triple("bank", listOf("com.a", "com.b"), false)
        viewModel.uiState.value.matchCount shouldBe 100 - 2 * 10 - 1
    }

    @Test
    fun `given a processed status, when the draft changes, then the count asks for processed only`() = runTest(testDispatcher) {
        val viewModel = createViewModel()

        viewModel.send(UiEvent.OnStatusChange(Status.PROCESSED))

        countRequests.last().third shouldBe true
    }

    @Test
    fun `given a typed app picker query, when it changes, then the live count is not re-requested`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        val before = countRequests.size

        viewModel.send(UiEvent.OnAppSearchChange("x"))

        countRequests.size shouldBe before
    }
}
