package dev.gaferneira.notificapp.features.settings.contract

import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.model.StorageStats
import dev.gaferneira.notificapp.domain.model.preferences.AppLanguage
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
import dev.gaferneira.notificapp.domain.model.preferences.ThemePreference

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
        /** Whether monitoring is globally paused (persisted; shared with the Quick Settings tile) */
        val monitoringPaused: Boolean = false,
        /** Whether the system exempts the app from battery optimizations */
        val isIgnoringBatteryOptimizations: Boolean = true,
        /** Preferred theme (system, light, dark) */
        val themePreference: ThemePreference = ThemePreference.SYSTEM,
        /** Notification retention period (auto-delete window) */
        val retentionPeriod: RetentionPeriod = RetentionPeriod.NEVER,
        /** Preferred app language (system, English, Spanish) */
        val appLanguage: AppLanguage = AppLanguage.SYSTEM,
        /** One-shot storage usage snapshot; null until loaded */
        val storageStats: StorageStats? = null,
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

        /** User clicked to manage webhooks */
        data object OnWebhooksClicked : UiEvent()

        /** User toggled the global pause-monitoring switch */
        data class OnMonitoringPausedChanged(val paused: Boolean) : UiEvent()

        /** User picked a new theme from the selection dialog */
        data class OnThemeChanged(val theme: ThemePreference) : UiEvent()

        /** User asked to open the system battery optimization settings */
        data object OnOpenBatterySettingsClicked : UiEvent()

        /** User confirmed clearing all collected data (notifications, executions, extracted values) */
        data object OnClearAllData : UiEvent()

        /** User picked a new retention period from the selection dialog */
        data class RetentionPeriodChanged(val period: RetentionPeriod) : UiEvent()

        /** User picked a new app language from the selection dialog */
        data class AppLanguageChanged(val language: AppLanguage) : UiEvent()

        /** User refreshed the settings */
        data object OnRefresh : UiEvent()

        /** User dismissed error */
        data object OnDismissError : UiEvent()

        /** Re-check notification listener status (called on resume) */
        data object OnResume : UiEvent()
    }

    /**
     * One-time effects (navigation, actions).
     */
    sealed class UiEffect {
        /** Navigate to app selection screen */
        data object NavigateToAppSelection : UiEffect()

        /** Navigate to the webhook list screen */
        data object NavigateToWebhookList : UiEffect()

        /** Open the system battery optimization settings (UI launches the platform intent) */
        data object OpenBatteryOptimizationSettings : UiEffect()

        /** Collected data was cleared successfully */
        data object DataCleared : UiEffect()

        /** Clearing collected data failed */
        data object ClearDataFailed : UiEffect()

        /** Show error message */
        data class ShowError(val message: String) : UiEffect()
    }
}
