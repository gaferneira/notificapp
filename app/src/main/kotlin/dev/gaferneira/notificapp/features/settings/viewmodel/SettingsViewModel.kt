package dev.gaferneira.notificapp.features.settings.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.di.SettingsDataActions
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.BatteryOptimizationStatusProvider
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.preferences.AppLanguage
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
import dev.gaferneira.notificapp.domain.model.preferences.ThemePreference
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.domain.repository.UserPreferencesRepository
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEffect
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEvent
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 *
 * Manages app settings including monitored apps count and preferences.
 * Continuously observes app changes so the count updates when returning from App Selection.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val listenerStatus: NotificationListenerStatusProvider,
    private val selectedAppRepository: SelectedAppRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val batteryOptimizationStatus: BatteryOptimizationStatusProvider,
    private val dataActions: SettingsDataActions,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    init {
        observeSettings()
        observeRetentionPeriod()
        observeAppLanguage()
        observeTheme()
        observeMonitoringPaused()
        checkBatteryOptimizationStatus()
        loadStorageStats()
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnSelectAppsClicked -> {
                sendEffect(UiEffect.NavigateToAppSelection)
            }
            is UiEvent.OnWebhooksClicked -> {
                sendEffect(UiEffect.NavigateToWebhookList)
            }
            is UiEvent.OnMonitoringPausedChanged -> setMonitoringPaused(event.paused)
            is UiEvent.OnThemeChanged -> setTheme(event.theme)
            is UiEvent.OnOpenBatterySettingsClicked -> sendEffect(UiEffect.OpenBatteryOptimizationSettings)
            is UiEvent.OnClearAllData -> clearAllData()
            is UiEvent.RetentionPeriodChanged -> setRetentionPeriod(event.period)
            is UiEvent.AppLanguageChanged -> setAppLanguage(event.language)
            is UiEvent.OnRefresh -> {
                // Data is already observed, refresh just clears errors
                setState { copy(error = null) }
            }
            is UiEvent.OnDismissError -> {
                setState { copy(error = null) }
            }
            is UiEvent.OnResume -> {
                checkListenerStatus()
                checkBatteryOptimizationStatus()
            }
        }
    }

    /**
     * Observe the retention period preference continuously, mirroring [observeSettings]'s
     * pattern for [selectedAppRepository].
     */
    private fun observeRetentionPeriod() {
        viewModelScope.launch {
            userPreferencesRepository.observeRetentionPeriod()
                .flowOn(ioDispatcher)
                .catch { e -> Timber.e(e, "Error observing retention period") }
                .collect { period ->
                    setState { copy(retentionPeriod = period) }
                }
        }
    }

    /**
     * Persist the new retention period on the IO dispatcher; state updates via
     * [observeRetentionPeriod]'s ongoing collection once the write succeeds.
     */
    private fun setRetentionPeriod(period: RetentionPeriod) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setRetentionPeriod(period)
                .onSuccess { dataActions.enforceRetention() }
                .onFailure { e -> Timber.e(e, "Failed to set retention period") }
            // Retention shrank the data set; refresh the storage snapshot.
            loadStorageStats()
        }
    }

    private fun observeTheme() {
        viewModelScope.launch {
            userPreferencesRepository.observeTheme()
                .flowOn(ioDispatcher)
                .catch { e -> Timber.e(e, "Error observing theme") }
                .collect { theme -> setState { copy(themePreference = theme) } }
        }
    }

    private fun setTheme(theme: ThemePreference) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setTheme(theme)
                .onFailure { e -> Timber.e(e, "Failed to set theme") }
        }
    }

    private fun observeMonitoringPaused() {
        viewModelScope.launch {
            userPreferencesRepository.observeMonitoringPaused()
                .flowOn(ioDispatcher)
                .catch { e -> Timber.e(e, "Error observing monitoring paused") }
                .collect { paused -> setState { copy(monitoringPaused = paused) } }
        }
    }

    private fun setMonitoringPaused(paused: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setMonitoringPaused(paused)
                .onFailure { e -> Timber.e(e, "Failed to set monitoring paused") }
        }
    }

    private fun checkBatteryOptimizationStatus() {
        viewModelScope.launch(ioDispatcher) {
            setState { copy(isIgnoringBatteryOptimizations = batteryOptimizationStatus.isIgnoringBatteryOptimizations()) }
        }
    }

    /** Clears collected data (not rules or monitored apps), then refreshes the storage snapshot. */
    private fun clearAllData() {
        viewModelScope.launch(ioDispatcher) {
            dataActions.clearCollectedData()
                .onSuccess {
                    sendEffect(UiEffect.DataCleared)
                    loadStorageStats()
                }
                .onFailure { e ->
                    Timber.e(e, "Failed to clear collected data")
                    sendEffect(UiEffect.ClearDataFailed)
                }
        }
    }

    /**
     * Observe the app language preference continuously, mirroring [observeRetentionPeriod]'s
     * pattern for [userPreferencesRepository].
     */
    private fun observeAppLanguage() {
        viewModelScope.launch {
            userPreferencesRepository.observeLanguage()
                .flowOn(ioDispatcher)
                .catch { e -> Timber.e(e, "Error observing app language") }
                .collect { language ->
                    setState { copy(appLanguage = language) }
                }
        }
    }

    /**
     * Persist the new app language on the IO dispatcher; state updates via
     * [observeAppLanguage]'s ongoing collection once the write succeeds.
     */
    private fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch(ioDispatcher) {
            userPreferencesRepository.setLanguage(language)
                .onFailure { e -> Timber.e(e, "Failed to set app language") }
        }
    }

    /**
     * Load the storage usage snapshot once on init - it's a point-in-time read, not observed
     * live (per plan, refresh isn't critical for v1).
     */
    private fun loadStorageStats() {
        viewModelScope.launch(ioDispatcher) {
            dataActions.storageStatsRepository.getStorageStats()
                .onSuccess { stats -> setState { copy(storageStats = stats) } }
                .onFailure { e -> Timber.e(e, "Failed to load storage stats") }
        }
    }

    /**
     * Re-checks the notification listener status on the IO dispatcher,
     * matching the pattern used in [observeSettings].
     */
    private fun checkListenerStatus() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val isListenerActive = listenerStatus.isEnabled()
                setState { copy(isNotificationListenerActive = isListenerActive) }
            } catch (e: SecurityException) {
                Timber.e(e, "Failed to check notification listener status")
            }
        }
    }

    /**
     * Observe settings continuously so UI updates when data changes.
     */
    private fun observeSettings() {
        viewModelScope.launch {
            try {
                // Check notification listener status (one-time check)
                val isListenerActive = listenerStatus.isEnabled()
                setState {
                    copy(
                        isNotificationListenerActive = isListenerActive,
                        isLoading = false,
                    )
                }

                // Observe enabled apps continuously (updates when returning from App Selection)
                selectedAppRepository.observeEnabledApps()
                    .flowOn(ioDispatcher)
                    .catch { e ->
                        Timber.e(e, "Error loading monitored apps")
                        emit(emptyList())
                    }
                    .collect { apps ->
                        setState {
                            copy(
                                monitoredApps = apps,
                                isLoading = false,
                            )
                        }
                        Timber.d("Updated monitored apps count: ${apps.size}")
                    }
            } catch (e: Exception) {
                Timber.e(e, "Failed to observe settings")
                setState {
                    copy(
                        isLoading = false,
                        error = "Failed to load settings: ${e.message}",
                    )
                }
            }
        }
    }
}
