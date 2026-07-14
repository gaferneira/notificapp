package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.SelectedApp
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing selected apps for notification monitoring.
 *
 * This interface defines the contract for selected app operations following
 * the Repository Pattern per ADR 005.
 */
interface SelectedAppRepository {

    /**
     * Observe all selected apps as a Flow.
     * Emits whenever the data changes.
     */
    fun observeAllApps(): Flow<List<SelectedApp>>

    /**
     * Observe only enabled apps as a Flow.
     * Emits whenever the data changes.
     */
    fun observeEnabledApps(): Flow<List<SelectedApp>>

    /**
     * Get all selected apps.
     */
    suspend fun getAllApps(): Result<List<SelectedApp>>

    /**
     * Get only enabled apps.
     */
    suspend fun getEnabledApps(): Result<List<SelectedApp>>

    /**
     * Get a specific app by package name.
     */
    suspend fun getApp(packageName: String): Result<SelectedApp?>

    /**
     * Check if an app is selected.
     */
    suspend fun isAppSelected(packageName: String): Result<Boolean>

    /**
     * Check if an app is enabled.
     */
    suspend fun isAppEnabled(packageName: String): Result<Boolean>

    /**
     * Add a new app to the selected list.
     */
    suspend fun addApp(app: SelectedApp): Result<Unit>

    /**
     * Add multiple apps to the selected list.
     */
    suspend fun addApps(apps: List<SelectedApp>): Result<Unit>

    /**
     * Remove an app from the selected list.
     */
    suspend fun removeApp(packageName: String): Result<Unit>

    /**
     * Remove multiple apps from the selected list.
     */
    suspend fun removeApps(packageNames: List<String>): Result<Unit>

    /**
     * Toggle the enabled state of an app.
     */
    suspend fun setAppEnabled(packageName: String, isEnabled: Boolean): Result<Unit>

    /**
     * Remove all selected apps.
     */
    suspend fun removeAllApps(): Result<Unit>

    /**
     * Get the count of selected apps.
     */
    suspend fun getSelectedCount(): Result<Int>

    /**
     * Get the count of enabled apps.
     */
    suspend fun getEnabledCount(): Result<Int>
}
