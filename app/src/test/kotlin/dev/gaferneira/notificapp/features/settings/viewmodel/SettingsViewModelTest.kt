package dev.gaferneira.notificapp.features.settings.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.model.StorageStats
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.domain.repository.StorageStatsRepository
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEffect
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEvent
import dev.gaferneira.notificapp.testutil.fakes.FakeUserPreferencesRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var selectedAppRepository: SelectedAppRepository
    private lateinit var userPreferencesRepository: FakeUserPreferencesRepository
    private lateinit var storageStatsRepository: StorageStatsRepository

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        selectedAppRepository = mockk()
        userPreferencesRepository = FakeUserPreferencesRepository()
        storageStatsRepository = mockk()
        coEvery { storageStatsRepository.getStorageStats() } returns Result.success(
            StorageStats(
                databaseSizeBytes = 0L,
                notificationCount = 0,
                ruleCount = 0,
                ruleExecutionCount = 0,
                extractedFieldValueCount = 0,
                selectedAppCount = 0,
            ),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(listenerEnabled: Boolean = true): SettingsViewModel = SettingsViewModel(
        listenerStatus = NotificationListenerStatusProvider { listenerEnabled },
        selectedAppRepository = selectedAppRepository,
        userPreferencesRepository = userPreferencesRepository,
        storageStatsRepository = storageStatsRepository,
        ioDispatcher = testDispatcher,
    )

    @Nested
    inner class ObserveSettingsTests {

        @Test
        fun `enabled apps stream populates monitoredApps and clears loading`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns flow {
                emit(listOf(SelectedApp(packageName = "com.bank", appName = "Bank")))
            }

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.monitoredApps.map { it.packageName } shouldBe listOf("com.bank")
            viewModel.uiState.value.isLoading shouldBe false
        }

        @Test
        fun `stream error falls back to empty monitored apps without crashing`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns flow {
                throw IllegalStateException("db down")
            }

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.monitoredApps shouldBe emptyList()
            viewModel.uiState.value.isLoading shouldBe false
        }

        @Test
        fun `sets listener status on init`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()

            val viewModel = createViewModel(listenerEnabled = false)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isNotificationListenerActive shouldBe false
        }
    }

    @Nested
    inner class ToggleTests {

        @Test
        fun `collection toggle updates state`() {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val viewModel = createViewModel()

            viewModel.onEvent(UiEvent.OnCollectionToggled(false))

            viewModel.uiState.value.isCollectionEnabled shouldBe false
        }

        @Test
        fun `show app icons toggle updates state`() {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val viewModel = createViewModel()

            viewModel.onEvent(UiEvent.OnShowAppIconsToggled(false))

            viewModel.uiState.value.showAppIcons shouldBe false
        }

        @Test
        fun `dismiss error clears the error field`() {
            every { selectedAppRepository.observeEnabledApps() } returns flow {
                throw IllegalStateException("db down")
            }
            val viewModel = createViewModel()

            viewModel.onEvent(UiEvent.OnDismissError)

            viewModel.uiState.value.error shouldBe null
        }
    }

    @Nested
    inner class NavigationTests {

        @Test
        fun `select apps clicked emits NavigateToAppSelection`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val viewModel = createViewModel()

            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnSelectAppsClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe UiEffect.NavigateToAppSelection
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class ListenerStatusTests {

        @Test
        fun `OnResume re-checks listener status`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            var enabled = true
            val viewModel = SettingsViewModel(
                listenerStatus = NotificationListenerStatusProvider { enabled },
                selectedAppRepository = selectedAppRepository,
                userPreferencesRepository = userPreferencesRepository,
                storageStatsRepository = storageStatsRepository,
                ioDispatcher = testDispatcher,
            )
            testDispatcher.scheduler.advanceUntilIdle()

            enabled = false
            viewModel.onEvent(UiEvent.OnResume)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isNotificationListenerActive shouldBe false
        }
    }

    @Nested
    inner class RetentionPeriodTests {

        @Test
        fun `initial retention period reflects the stored preference`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            userPreferencesRepository.setRetentionPeriod(RetentionPeriod.DAYS_90)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.retentionPeriod shouldBe RetentionPeriod.DAYS_90
        }

        @Test
        fun `RetentionPeriodChanged persists the new period and updates state`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.RetentionPeriodChanged(RetentionPeriod.DAYS_30))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.retentionPeriod shouldBe RetentionPeriod.DAYS_30
            userPreferencesRepository.current().retentionPeriod shouldBe RetentionPeriod.DAYS_30
        }
    }

    @Nested
    inner class StorageStatsTests {

        @Test
        fun `storage stats load into state on init`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            coEvery { storageStatsRepository.getStorageStats() } returns Result.success(
                StorageStats(
                    databaseSizeBytes = 2048L,
                    notificationCount = 5,
                    ruleCount = 2,
                    ruleExecutionCount = 3,
                    extractedFieldValueCount = 4,
                    selectedAppCount = 1,
                ),
            )

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.storageStats shouldBe StorageStats(
                databaseSizeBytes = 2048L,
                notificationCount = 5,
                ruleCount = 2,
                ruleExecutionCount = 3,
                extractedFieldValueCount = 4,
                selectedAppCount = 1,
            )
        }

        @Test
        fun `storage stats failure leaves state at null without crashing`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            coEvery { storageStatsRepository.getStorageStats() } returns Result.failure(IllegalStateException("db error"))

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.storageStats shouldBe null
        }
    }
}
