package dev.gaferneira.notificapp.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.gaferneira.notificapp.core.data.preferences.datastore.PreferenceKeys
import dev.gaferneira.notificapp.domain.model.preferences.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local data source for user preferences using DataStore.
 *
 * This class abstracts DataStore operations and handles JSON serialization
 * of complex preference objects.
 *
 * @property dataStore The DataStore instance for preferences
 */
@Singleton
class UserPreferencesLocalDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    /**
     * Observe user preferences as a Flow.
     *
     * @return Flow emitting UserPreferences, starting with defaults if not set
     */
    fun observeUserPreferences(): Flow<UserPreferences> = dataStore.data.map { preferences ->
        preferences[PreferenceKeys.USER_PREFERENCES]?.let { jsonString ->
            try {
                json.decodeFromString<UserPreferences>(jsonString)
            } catch (e: Exception) {
                UserPreferences() // Return defaults on parsing error
            }
        } ?: UserPreferences() // Return defaults if not set
    }

    /**
     * Get user preferences (one-shot).
     *
     * @return Result containing UserPreferences or exception
     */
    suspend fun getUserPreferences(): Result<UserPreferences> = try {
        val preferences = dataStore.data.map { prefs ->
            prefs[PreferenceKeys.USER_PREFERENCES]?.let { jsonString ->
                try {
                    json.decodeFromString<UserPreferences>(jsonString)
                } catch (e: Exception) {
                    UserPreferences()
                }
            } ?: UserPreferences()
        }.map { Result.success(it) }
        preferences.first()
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Update user preferences.
     *
     * @param preferences The new preferences to store
     * @return Result indicating success or failure
     */
    suspend fun updateUserPreferences(preferences: UserPreferences): Result<Unit> = try {
        dataStore.edit { prefs ->
            prefs[PreferenceKeys.USER_PREFERENCES] = json.encodeToString(preferences)
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
