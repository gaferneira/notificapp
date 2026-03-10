package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.preferences.InboxFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.RulesFilterSettings
import dev.gaferneira.notificapp.domain.model.preferences.ThemePreference
import dev.gaferneira.notificapp.domain.model.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing user preferences.
 *
 * This interface defines the contract for accessing and modifying user preferences
 * following the Repository Pattern per ADR 005. All preferences are stored
 * locally (per-device) using DataStore.
 *
 * The repository provides Flow-based observation for reactive UI updates.
 */
interface UserPreferencesRepository {

    /**
     * Observe all user preferences as a Flow.
     */
    fun observeUserPreferences(): Flow<UserPreferences>

    /**
     * Get all user preferences (one-shot).
     */
    suspend fun getUserPreferences(): Result<UserPreferences>

    /**
     * Observe inbox filter settings as a Flow.
     */
    fun observeInboxFilters(): Flow<InboxFilterSettings>

    /**
     * Update inbox filter settings.
     */
    suspend fun setInboxFilters(filters: InboxFilterSettings): Result<Unit>

    /**
     * Observe rules filter settings as a Flow.
     */
    fun observeRulesFilters(): Flow<RulesFilterSettings>

    /**
     * Update rules filter settings.
     */
    suspend fun setRulesFilters(filters: RulesFilterSettings): Result<Unit>

    /**
     * Observe theme preference as a Flow.
     */
    fun observeTheme(): Flow<ThemePreference>

    /**
     * Update theme preference.
     */
    suspend fun setTheme(theme: ThemePreference): Result<Unit>

    /**
     * Reset all preferences to default values.
     */
    suspend fun resetToDefaults(): Result<Unit>
}
