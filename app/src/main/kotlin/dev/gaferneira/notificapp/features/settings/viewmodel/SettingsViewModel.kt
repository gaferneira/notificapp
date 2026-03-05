package dev.gaferneira.notificapp.features.settings.viewmodel

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.core.di.Dispatcher
import dev.gaferneira.notificapp.core.di.DispatcherType
import dev.gaferneira.notificapp.core.ui.mvi.MviViewModel
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
    @ApplicationContext private val context: Context,
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
        }
    }

    /**
     * Observe settings continuously so UI updates when data changes.
     */
    private fun observeSettings() {
        viewModelScope.launch {
            try {
                // Check notification listener status (one-time check)
                val isListenerActive = isNotificationServiceEnabled()
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

    /**
     * Check if notification listener service is enabled.
     */
    private fun isNotificationServiceEnabled(): Boolean {
        val packageName = context.packageName
        val flat = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        )
        return flat?.contains(packageName) == true
    }
}
