package dev.gaferneira.notificapp.features.settings.viewmodel

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
import dev.gaferneira.notificapp.domain.NotificationListenerStatusProvider
import dev.gaferneira.notificapp.domain.repository.SelectedAppRepository
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
    @Dispatcher(DispatcherType.IO) private val ioDispatcher: CoroutineDispatcher,
) : MviViewModel<UiState, UiEvent, UiEffect>(UiState()) {

    init {
        observeSettings()
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
