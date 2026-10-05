package dev.gaferneira.notificapp.features.appselection.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.UiText
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
        isInitialSetup: Boolean = preSelected.isEmpty(),
    ): AppSelectionViewModel {
        appsProvider = mockk { coEvery { getMonitorableApps() } returns apps }
        selectedAppRepository = FakeSelectedAppRepository(initial = preSelected)
        return AppSelectionViewModel(appsProvider, selectedAppRepository, navigationHandler, testDispatcher).also {
            it.onEvent(UiEvent.OnScreenOpened(isInitialSetup))
        }
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
        fun `route flag is respected regardless of persisted selection`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(
                emptyList(),
                preSelected = listOf(SelectedApp(packageName = "com.a", appName = "Alpha", isEnabled = true)),
                isInitialSetup = true,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isInitialSetup shouldBe true
        }

        @Test
        fun `initial setup starts with no app selected and persists nothing`() = runTest(testDispatcher) {
            val apps = listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank"))
            val viewModel = buildViewModel(apps)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.selectedPackageNames shouldBe emptySet()
            viewModel.uiState.value.hasSelection shouldBe false
            selectedAppRepository.currentApps() shouldBe emptyList()
        }

        @Test
        fun `settings route is not initial setup and never auto-adds apps`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(
                listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank")),
                preSelected = listOf(SelectedApp(packageName = "com.a", appName = "Alpha", isEnabled = true)),
                isInitialSetup = false,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isInitialSetup shouldBe false
            selectedAppRepository.currentApps().map { it.packageName } shouldBe listOf("com.a")
        }

        @Test
        fun `settings route with empty selection stays empty`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(listOf(AppInfo("com.a", "Alpha")), isInitialSetup = false)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.selectedPackageNames shouldBe emptySet()
            selectedAppRepository.currentApps() shouldBe emptyList()
        }

        @Test
        fun `a second OnScreenOpened does not reload or change the flag`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(listOf(AppInfo("com.a", "Alpha")), isInitialSetup = true)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnScreenOpened(isInitialSetup = false))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isInitialSetup shouldBe true
            coVerify(exactly = 1) { appsProvider.getMonitorableApps() }
        }

        @Test
        fun `OnRefresh reloads the app list`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(listOf(AppInfo("com.a", "Alpha")))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnRefresh)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.availableApps shouldBe listOf(AppInfo("com.a", "Alpha"))
        }

        @Test
        fun `OnRefresh preserves deselections and never modifies the saved selection`() = runTest(testDispatcher) {
            val apps = listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank"))
            val viewModel = buildViewModel(apps)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onEvent(UiEvent.OnSelectAllToggled)
            viewModel.onEvent(UiEvent.OnAppToggled("com.b", isSelected = false))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnRefresh)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.selectedPackageNames shouldBe setOf("com.a")
            selectedAppRepository.currentApps().map { it.packageName } shouldBe listOf("com.a")
        }

        @Test
        fun `OnRefresh does not show the loading spinner and keeps list order`() = runTest(testDispatcher) {
            val apps = listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank"))
            val viewModel = buildViewModel(apps)
            testDispatcher.scheduler.advanceUntilIdle()
            val orderBefore = viewModel.uiState.value.availableApps.map { it.packageName }

            viewModel.uiState.test {
                awaitItem().isLoading shouldBe false
                viewModel.onEvent(UiEvent.OnRefresh)
                testDispatcher.scheduler.advanceUntilIdle()
                cancelAndIgnoreRemainingEvents()
            }

            viewModel.uiState.value.isLoading shouldBe false
            viewModel.uiState.value.availableApps.map { it.packageName } shouldBe orderBefore
        }

        @Test
        fun `OnRefresh before the screen is opened does nothing`() = runTest(testDispatcher) {
            appsProvider = mockk { coEvery { getMonitorableApps() } returns emptyList() }
            selectedAppRepository = FakeSelectedAppRepository()
            val viewModel = AppSelectionViewModel(appsProvider, selectedAppRepository, navigationHandler, testDispatcher)

            viewModel.onEvent(UiEvent.OnRefresh)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { appsProvider.getMonitorableApps() }
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
    inner class SelectAllTests {

        @Test
        fun `select all toggle deselects everything when all filtered apps are selected`() = runTest(testDispatcher) {
            val apps = listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank"))
            val viewModel = buildViewModel(apps)
            testDispatcher.scheduler.advanceUntilIdle()
            viewModel.onEvent(UiEvent.OnSelectAllToggled)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnSelectAllToggled)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.selectedPackageNames shouldBe emptySet()
            selectedAppRepository.currentApps() shouldBe emptyList()
        }

        @Test
        fun `select all toggle selects every filtered app when none or some are selected`() = runTest(testDispatcher) {
            val apps = listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank"))
            val viewModel = buildViewModel(
                apps,
                preSelected = listOf(SelectedApp(packageName = "com.a", appName = "Alpha", isEnabled = true)),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnSelectAllToggled)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.selectedPackageNames shouldBe setOf("com.a", "com.b")
            selectedAppRepository.currentApps().map { it.packageName }.toSet() shouldBe setOf("com.a", "com.b")
        }

        @Test
        fun `select all toggle only applies to search-filtered apps`() = runTest(testDispatcher) {
            val apps = listOf(AppInfo("com.a", "Alpha"), AppInfo("com.b", "Bank"))
            val viewModel = buildViewModel(
                apps,
                preSelected = listOf(
                    SelectedApp(packageName = "com.a", appName = "Alpha", isEnabled = true),
                    SelectedApp(packageName = "com.b", appName = "Bank", isEnabled = true),
                ),
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnSearchQueryChanged("ban"))
            viewModel.onEvent(UiEvent.OnSelectAllToggled)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.selectedPackageNames shouldBe setOf("com.a")
            selectedAppRepository.currentApps().map { it.packageName }.toSet() shouldBe setOf("com.a")
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
        fun `continue is disabled and a forced click does not navigate when nothing is selected`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(listOf(AppInfo("com.a", "Alpha")))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.hasSelection shouldBe false

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

            coVerify(exactly = 1) { navigationHandler.clearAndNavigate(Routes.home()) }
        }

        @Test
        fun `back from settings navigates back and never to home`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(listOf(AppInfo("com.a", "Alpha")), isInitialSetup = false)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnBackClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { navigationHandler.goBack() }
            coVerify(exactly = 0) { navigationHandler.clearAndNavigate(any()) }
        }

        @Test
        fun `continue when accessed from settings navigates back instead`() = runTest(testDispatcher) {
            val viewModel = buildViewModel(
                listOf(AppInfo("com.a", "Alpha")),
                preSelected = listOf(SelectedApp(packageName = "com.a", appName = "Alpha", isEnabled = true)),
                isInitialSetup = false,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnContinueClicked)
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { navigationHandler.goBack() }
            coVerify(exactly = 0) { navigationHandler.clearAndNavigate(any()) }
        }
    }

    @Nested
    inner class ErrorTests {

        @Test
        fun `a provider failure surfaces an error and clears loading`() = runTest(testDispatcher) {
            appsProvider = mockk { coEvery { getMonitorableApps() } throws IllegalStateException("boom") }
            selectedAppRepository = FakeSelectedAppRepository()
            val viewModel = AppSelectionViewModel(appsProvider, selectedAppRepository, navigationHandler, testDispatcher)
            viewModel.onEvent(UiEvent.OnScreenOpened(isInitialSetup = true))
            testDispatcher.scheduler.advanceUntilIdle()

            val state = viewModel.uiState.value
            state.isLoading shouldBe false
            state.error shouldBe UiText.StringResource(R.string.app_selection_error_load, arrayOf("boom"))
        }

        @Test
        fun `dismissing the error clears it`() = runTest(testDispatcher) {
            appsProvider = mockk { coEvery { getMonitorableApps() } throws IllegalStateException("boom") }
            selectedAppRepository = FakeSelectedAppRepository()
            val viewModel = AppSelectionViewModel(appsProvider, selectedAppRepository, navigationHandler, testDispatcher)
            viewModel.onEvent(UiEvent.OnScreenOpened(isInitialSetup = true))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnDismissError)

            viewModel.uiState.value.error shouldBe null
        }
    }
}
