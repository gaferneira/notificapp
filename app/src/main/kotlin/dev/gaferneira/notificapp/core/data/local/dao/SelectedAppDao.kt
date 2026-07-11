package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import dev.gaferneira.notificapp.core.data.local.entity.SelectedAppEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for managing selected apps in the database.
 */
@Dao
internal interface SelectedAppDao {

    /**
     * Get all selected apps as a Flow for reactive observation.
     */
    @Query("SELECT * FROM selected_apps ORDER BY app_name ASC")
    fun getAll(): Flow<List<SelectedAppEntity>>

    /**
     * Get all selected apps synchronously.
     */
    @Query("SELECT * FROM selected_apps ORDER BY app_name ASC")
    suspend fun getAllSync(): List<SelectedAppEntity>

    /**
     * Get only enabled apps as a Flow.
     */
    @Query("SELECT * FROM selected_apps WHERE is_enabled = 1 ORDER BY app_name ASC")
    fun getEnabledApps(): Flow<List<SelectedAppEntity>>

    /**
     * Get only enabled apps synchronously.
     */
    @Query("SELECT * FROM selected_apps WHERE is_enabled = 1 ORDER BY app_name ASC")
    suspend fun getEnabledAppsSync(): List<SelectedAppEntity>

    /**
     * Get a specific app by package name.
     */
    @Query("SELECT * FROM selected_apps WHERE package_name = :packageName LIMIT 1")
    suspend fun getByPackageName(packageName: String): SelectedAppEntity?

    /**
     * Get multiple apps by their package names (batch query).
     */
    @Query("SELECT * FROM selected_apps WHERE package_name IN (:packageNames)")
    suspend fun getByPackageNames(packageNames: List<String>): List<SelectedAppEntity>

    /**
     * Check if an app is selected (exists in database).
     */
    @Query("SELECT EXISTS(SELECT 1 FROM selected_apps WHERE package_name = :packageName LIMIT 1)")
    suspend fun isAppSelected(packageName: String): Boolean

    /**
     * Check if an app is enabled.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM selected_apps WHERE package_name = :packageName AND is_enabled = 1 LIMIT 1)")
    suspend fun isAppEnabled(packageName: String): Boolean

    /**
     * Insert a new selected app.
     * Replaces on conflict (UPSERT behavior).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(app: SelectedAppEntity)

    /**
     * Insert multiple selected apps.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(apps: List<SelectedAppEntity>)

    /**
     * Update an existing app.
     */
    @Update
    suspend fun update(app: SelectedAppEntity)

    /**
     * Delete a selected app.
     */
    @Delete
    suspend fun delete(app: SelectedAppEntity)

    /**
     * Delete an app by package name.
     */
    @Query("DELETE FROM selected_apps WHERE package_name = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    /**
     * Delete all selected apps.
     */
    @Query("DELETE FROM selected_apps")
    suspend fun deleteAll()

    /**
     * Get the count of selected apps.
     */
    @Query("SELECT COUNT(*) FROM selected_apps")
    suspend fun getCount(): Int

    /**
     * Get the count of enabled apps.
     */
    @Query("SELECT COUNT(*) FROM selected_apps WHERE is_enabled = 1")
    suspend fun getEnabledCount(): Int

    /**
     * Toggle the enabled state of an app.
     */
    @Query("UPDATE selected_apps SET is_enabled = :isEnabled WHERE package_name = :packageName")
    suspend fun setEnabled(packageName: String, isEnabled: Boolean)
}
