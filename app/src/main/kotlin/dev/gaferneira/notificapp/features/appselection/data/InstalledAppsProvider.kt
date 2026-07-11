package dev.gaferneira.notificapp.features.appselection.data

import dev.gaferneira.notificapp.domain.model.AppInfo

/**
 * Resolves installed apps that can plausibly send notifications, so
 * [dev.gaferneira.notificapp.features.appselection.viewmodel.AppSelectionViewModel] never touches
 * `PackageManager` directly (a static Android API that can't be stubbed on the JVM).
 */
interface InstalledAppsProvider {
    /** Launchable, non-pure-system apps that can post notifications. */
    suspend fun getMonitorableApps(): List<AppInfo>
}
