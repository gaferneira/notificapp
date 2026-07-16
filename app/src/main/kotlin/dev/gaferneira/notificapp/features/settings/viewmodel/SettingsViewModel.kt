package dev.gaferneira.notificapp.features.settings.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
import dev.gaferneira.notificapp.domain.repository.StorageStatsRepository
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
    private val storageStatsRepository: StorageStatsRepository,
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    init {
        observeSettings()
        observeRetentionPeriod()
        loadStorageStats()
    }

    override fun onEvent(event: UiEvent) {
        when (event) {
            is UiEvent.OnSelectAppsClicked -> {
                sendEffect(UiEffect.NavigateToAppSelection)
            }
            is UiEvent.OnCollectionToggled -> {
                setState { copy(isCollectionEnabled = event.isEnabled) }
            }
            is UiEvent.OnShowAppIconsToggled -> {
                setState { copy(showAppIcons = event.isEnabled) }
            }
            is UiEvent.RetentionPeriodChanged -> setRetentionPeriod(event.period)
            is UiEvent.OnRefresh -> {
                // Data is already observed, refresh just clears errors
                setState { copy(error = null) }
            }
            is UiEvent.OnDismissError -> {
                setState { copy(error = null) }
            }
            is UiEvent.OnResume -> checkListenerStatus()
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
                .onFailure { e -> Timber.e(e, "Failed to set retention period") }
        }
    }

    /**
     * Load the storage usage snapshot once on init - it's a point-in-time read, not observed
     * live (per plan, refresh isn't critical for v1).
     */
    private fun loadStorageStats() {
        viewModelScope.launch(ioDispatcher) {
            storageStatsRepository.getStorageStats()
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
