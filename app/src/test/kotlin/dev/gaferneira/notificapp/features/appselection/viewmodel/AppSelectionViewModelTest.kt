package dev.gaferneira.notificapp.features.appselection.viewmodel

import dev.gaferneira.notificapp.core.ui.navigation.NavigationHandler
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.features.appselection.contract.AppSelectionContract.UiEvent
import dev.gaferneira.notificapp.features.appselection.data.InstalledAppsProvider
import dev.gaferneira.notificapp.testutil.fakes.FakeSelectedAppRepository
import io.kotest.matchers.shouldBe
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
class AppSelectionViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var appsProvider: InstalledAppsProvider
    private lateinit var selectedAppRepository: FakeSelectedAppRepository
    private lateinit var navigationHandler: NavigationHandler

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        navigationHandler = mockk(relaxed = true)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        apps: List<AppInfo>,
        preSelected: List<SelectedApp> = emptyList(),
    ): AppSelectionViewModel {
        appsProvider = mockk { coEvery { getMonitorableApps() } returns apps }
        selectedAppRepository = FakeSelectedAppRepository(initial = preSelected)
        return AppSelectionViewModel(appsProvider, selectedAppRepository, navigationHandler, testDispatcher)
    }

    @Nested
    inner class LoadTests {

        @Test
        fun `loading populates available apps and clears loading`() = runTest(testDispatcher) {
            val apps = listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank"))
            val viewModel = buildViewModel(apps)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.availableApps shouldBe listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank"))
        }

        @Test
        fun `already-selected apps are pre-checked and sorted first`() = runTest(testDispatcher) {
            val apps = listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank"))
            val viewModel = buildViewModel(
                apps,
                preSelected = listOf(SelectedApp(packageName = "com.b", appName = "Bank", isEnabled = true)),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.selectedPackageNames shouldBe setOf("com.b")
            state.availableApps.map { it.packageName } shouldBe listOf("com.b", "com.a")
        }

        @Test
        fun `no pre-selected apps means initial setup`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(emptyList())
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isInitialSetup shouldBe true
        }

        @Test
        fun `pre-selected apps means not initial setup`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(
                emptyList(),
                preSelected = listOf(SelectedApp(packageName = "com.a", appName = "Alpha", isEnabled = true)),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isInitialSetup shouldBe false
        }

        @Test
        fun `OnRefresh reloads the app list`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(listOf(AppInfo("com.a", "Alpha")))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnRefresh)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.availableApps shouldBe listOf(AppInfo("com.a", "Alpha"))
        }
    }

    @Nested
    inner class ToggleTests {

        @Test
        fun `toggling an app on updates state and persists it as enabled`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(listOf(AppInfo("com.a", "Alpha")))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnAppToggled("com.a", isSelected = true))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.selectedPackageNames shouldBe setOf("com.a")
            selectedAppRepository.currentApps().single().let {
                it.packageName shouldBe "com.a"
                it.isEnabled shouldBe true
            }
        }

        @Test
        fun `toggling an app off removes it from state and the repository`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(
                listOf(AppInfo("com.a", "Alpha")),
                preSelected = listOf(SelectedApp(packageName = "com.a", appName = "Alpha", isEnabled = true)),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnAppToggled("com.a", isSelected = false))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.selectedPackageNames shouldBe emptySet()
            selectedAppRepository.currentApps() shouldBe emptyList()
        }
    }

    @Nested
    inner class SearchTests {

        @Test
        fun `search query filters available apps by name`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank")))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnSearchQueryChanged("ban"))

            viewModel.uiState.value.filteredApps.map { it.packageName } shouldBe listOf("com.b")
        }
    }

    @Nested
    inner class ContinueTests {

        @Test
        fun `continue with no selection shows an error and does not navigate`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(emptyList())
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnContinueClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { navigationHandler.clearAndNavigate(any()) }
            coVerify(exactly = 0) { navigationHandler.goBack() }
        }

        @Test
        fun `continue during initial setup navigates to the main app`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(listOf(AppInfo("com.a", "Alpha")))
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onEvent(UiEvent.OnAppToggled("com.a", isSelected = true))

            viewModel.onEvent(UiEvent.OnContinueClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { navigationHandler.clearAndNavigate(Routes.inbox()) }
        }

        @Test
        fun `continue when accessed from settings navigates back instead`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(
                listOf(AppInfo("com.a", "Alpha")),
                preSelected = listOf(SelectedApp(packageName = "com.a", appName = "Alpha", isEnabled = true)),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnContinueClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { navigationHandler.goBack() }
        }
    }

    @Nested
    inner class ErrorTests {

        @Test
        fun `a provider failure surfaces an error and clears loading`() = runTest(testDispatcher) {
            appsProvider = mockk { coEvery { getMonitorableApps() } throws IllegalStateException("boom") }
            selectedAppRepository = FakeSelectedAppRepository()
            val viewModel = AppSelectionViewModel(appsProvider, selectedAppRepository, navigationHandler, testDispatcher)
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.error shouldBe "Failed to load apps: boom"
        }

        @Test
        fun `dismissing the error clears it`() = runTest(testDispatcher) {
            appsProvider = mockk { coEvery { getMonitorableApps() } throws IllegalStateException("boom") }
            selectedAppRepository = FakeSelectedAppRepository()
            val viewModel = AppSelectionViewModel(appsProvider, selectedAppRepository, navigationHandler, testDispatcher)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnDismissError)

            viewModel.uiState.value.error shouldBe null
        }
    }
}
