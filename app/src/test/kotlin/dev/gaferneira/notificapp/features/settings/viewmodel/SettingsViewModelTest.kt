package dev.gaferneira.notificapp.features.settings.viewmodel

import app.cash.turbine.test
import dev.gaferneira.notificapp.core.di.SettingsDataActions
import dev.gaferneira.notificapp.core.notification.ClearCollectedDataUseCase
import dev.gaferneira.notificapp.core.notification.EnforceRetentionUseCase
import dev.gaferneira.notificapp.domain.BatteryOptimizationStatusProvider
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.model.StorageStats
import dev.gaferneira.notificapp.domain.model.preferences.AppLanguage
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
import dev.gaferneira.notificapp.domain.model.preferences.ThemePreference
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.domain.repository.StorageStatsRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEffect
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEvent
import dev.gaferneira.notificapp.testutil.fakes.FakeUserPreferencesRepository
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
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
    private lateinit var enforceRetentionUseCase: EnforceRetentionUseCase
    private lateinit var clearCollectedDataUseCase: ClearCollectedDataUseCase
    private var batteryIgnoring = true

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        selectedAppRepository = mockk()
        userPreferencesRepository = FakeUserPreferencesRepository()
        storageStatsRepository = mockk()
        enforceRetentionUseCase = mockk()
        coEvery { enforceRetentionUseCase() } returns Unit
        clearCollectedDataUseCase = mockk()
        coEvery { clearCollectedDataUseCase() } returns Result.success(Unit)
        batteryIgnoring = true
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
        batteryOptimizationStatus = BatteryOptimizationStatusProvider { batteryIgnoring },
        dataActions = SettingsDataActions(enforceRetentionUseCase, clearCollectedDataUseCase, storageStatsRepository),
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
    inner class DismissErrorTests {

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
                batteryOptimizationStatus = BatteryOptimizationStatusProvider { batteryIgnoring },
                dataActions = SettingsDataActions(enforceRetentionUseCase, clearCollectedDataUseCase, storageStatsRepository),
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
    inner class RetentionEnforcementTests {

        @Test
        fun `RetentionPeriodChanged triggers the retention sweep after persisting`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.RetentionPeriodChanged(RetentionPeriod.DAYS_30))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { enforceRetentionUseCase() }
        }

        @Test
        fun `retention sweep is not run when persisting the period fails`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val failingPrefs = mockk<UserPreferencesRepository>(relaxed = true)
            every { failingPrefs.observeRetentionPeriod() } returns MutableSharedFlow()
            every { failingPrefs.observeLanguage() } returns MutableSharedFlow()
            every { failingPrefs.observeTheme() } returns MutableSharedFlow()
            every { failingPrefs.observeMonitoringPaused() } returns MutableSharedFlow()
            coEvery { failingPrefs.setRetentionPeriod(any()) } returns Result.failure(IllegalStateException("io"))
            val viewModel = SettingsViewModel(
                listenerStatus = NotificationListenerStatusProvider { true },
                selectedAppRepository = selectedAppRepository,
                userPreferencesRepository = failingPrefs,
                batteryOptimizationStatus = BatteryOptimizationStatusProvider { true },
                dataActions = SettingsDataActions(enforceRetentionUseCase, clearCollectedDataUseCase, storageStatsRepository),
                ioDispatcher = testDispatcher,
            )

            viewModel.onEvent(UiEvent.RetentionPeriodChanged(RetentionPeriod.DAYS_30))
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { enforceRetentionUseCase() }
        }
    }

    @Nested
    inner class MonitoringPausedTests {

        @Test
        fun `initial paused state reflects the stored preference`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            userPreferencesRepository.setMonitoringPaused(true)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.monitoringPaused shouldBe true
        }

        @Test
        fun `OnMonitoringPausedChanged persists the flag and updates state`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnMonitoringPausedChanged(true))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.monitoringPaused shouldBe true
            userPreferencesRepository.current().monitoringPaused shouldBe true
        }
    }

    @Nested
    inner class ThemeTests {

        @Test
        fun `initial theme reflects the stored preference`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            userPreferencesRepository.setTheme(ThemePreference.DARK)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.themePreference shouldBe ThemePreference.DARK
        }

        @Test
        fun `OnThemeChanged persists the theme and updates state`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.OnThemeChanged(ThemePreference.LIGHT))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.themePreference shouldBe ThemePreference.LIGHT
            userPreferencesRepository.current().themePreference shouldBe ThemePreference.LIGHT
        }
    }

    @Nested
    inner class BatteryOptimizationTests {

        @Test
        fun `battery status is loaded on init`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            batteryIgnoring = false

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isIgnoringBatteryOptimizations shouldBe false
        }

        @Test
        fun `OnResume refreshes battery status`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            batteryIgnoring = false
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            batteryIgnoring = true
            viewModel.onEvent(UiEvent.OnResume)
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.isIgnoringBatteryOptimizations shouldBe true
        }

        @Test
        fun `OnOpenBatterySettingsClicked emits OpenBatteryOptimizationSettings`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val viewModel = createViewModel()

            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnOpenBatterySettingsClicked)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe UiEffect.OpenBatteryOptimizationSettings
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Nested
    inner class ClearAllDataTests {

        @Test
        fun `OnClearAllData clears data, emits DataCleared and refreshes storage stats`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnClearAllData)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe UiEffect.DataCleared
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { clearCollectedDataUseCase() }
            // once on init + once after clearing
            coVerify(exactly = 2) { storageStatsRepository.getStorageStats() }
        }

        @Test
        fun `OnClearAllData failure emits ClearDataFailed and skips the stats refresh`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            coEvery { clearCollectedDataUseCase() } returns Result.failure(IllegalStateException("db"))
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.effect.test {
                viewModel.onEvent(UiEvent.OnClearAllData)
                testDispatcher.scheduler.advanceUntilIdle()

                awaitItem() shouldBe UiEffect.ClearDataFailed
                cancelAndIgnoreRemainingEvents()
            }
            coVerify(exactly = 1) { storageStatsRepository.getStorageStats() }
        }
    }

    @Nested
    inner class AppLanguageTests {

        @Test
        fun `initial app language reflects the stored preference`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            userPreferencesRepository.setLanguage(AppLanguage.ES)

            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.appLanguage shouldBe AppLanguage.ES
        }

        @Test
        fun `AppLanguageChanged persists the new language and updates state`() = runTest(testDispatcher) {
            every { selectedAppRepository.observeEnabledApps() } returns MutableSharedFlow()
            val viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.onEvent(UiEvent.AppLanguageChanged(AppLanguage.EN))
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.uiState.value.appLanguage shouldBe AppLanguage.EN
            userPreferencesRepository.current().appLanguage shouldBe AppLanguage.EN
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
