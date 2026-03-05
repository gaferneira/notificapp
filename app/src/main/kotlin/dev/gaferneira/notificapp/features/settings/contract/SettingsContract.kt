package dev.gaferneira.notificapp.features.settings.contract

import dev.gaferneira.notificapp.domain.model.SelectedApp

/**
 * Contract for the Settings screen.
 *
 * Provides app configuration options including monitored apps,
 * notification settings, and app information.
 */
object SettingsContract {

    /**
     * UI State for the settings screen.
     */
    data class UiState(
        /** List of apps currently being monitored */
        val monitoredApps: List<SelectedApp> = emptyList(),
        /** Whether notification listener is active */
        val isNotificationListenerActive: Boolean = false,
        /** Whether data collection is enabled */
        val isCollectionEnabled: Boolean = true,
        /** Whether to show app icons in lists */
        val showAppIcons: Boolean = true,
        /** Whether the screen is loading */
        val isLoading: Boolean = true,
        /** Error message if loading failed */
        val error: String? = null,
    ) {
        /** Count of monitored apps */
        val monitoredAppsCount: Int
            get() = monitoredApps.size

        /** Whether any apps are being monitored */
        val hasMonitoredApps: Boolean
            get() = monitoredApps.isNotEmpty()
    }

    /**
     * UI Events from user interactions.
     */
    sealed class UiEvent {
        /** User clicked to select/monitor apps */
        data object OnSelectAppsClicked : UiEvent()

        /** User toggled data collection */
        data class OnCollectionToggled(val isEnabled: Boolean) : UiEvent()

        /** User toggled show app icons preference */
        data class OnShowAppIconsToggled(val isEnabled: Boolean) : UiEvent()

        /** User refreshed the settings */
        data object OnRefresh : UiEvent()

        /** User dismissed error */
        data object OnDismissError : UiEvent()
    }

    /**
     * One-time effects (navigation, actions).
     */
    sealed class UiEffect {
        /** Navigate to app selection screen */
        data object NavigateToAppSelection : UiEffect()

        /** Navigate to notification settings */
        data object NavigateToNotificationSettings : UiEffect()

        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }
}
