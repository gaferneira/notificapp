package dev.gaferneira.notificapp.core.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dev.gaferneira.notificapp.core.common.toFailureResult
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
        decodeOrDefault(preferences[PreferenceKeys.USER_PREFERENCES])
    }

    /**
     * Get user preferences (one-shot).
     *
     * @return Result containing UserPreferences or exception
     */
    suspend fun getUserPreferences(): Result<UserPreferences> = try {
        val preferences = dataStore.data.map { prefs ->
            decodeOrDefault(prefs[PreferenceKeys.USER_PREFERENCES])
        }.map { Result.success(it) }
        preferences.first()
    } catch (e: Exception) {
        e.toFailureResult()
    }

    /**
     * Atomically update user preferences by applying [transform] to the current snapshot.
     *
     * The read and write happen inside a single [DataStore.updateData] transaction (via
     * [androidx.datastore.preferences.core.edit]), which DataStore itself serializes across
     * concurrent callers. This closes the lost-update race that a separate read-then-write
     * would otherwise have: two concurrent updates touching different fields will both be
     * applied, because each transform runs against the latest committed snapshot rather than
     * a snapshot captured before the other writer committed.
     *
     * @param transform Pure function computing the new preferences from the current ones.
     * @return Result indicating success or failure.
     */
    suspend fun updateUserPreferences(transform: (UserPreferences) -> UserPreferences): Result<Unit> = try {
        dataStore.edit { prefs ->
            val current = decodeOrDefault(prefs[PreferenceKeys.USER_PREFERENCES])
            prefs[PreferenceKeys.USER_PREFERENCES] = json.encodeToString(transform(current))
        }
        Result.success(Unit)
    } catch (e: Exception) {
        e.toFailureResult()
    }

    private fun decodeOrDefault(jsonString: String?): UserPreferences = jsonString?.let {
        try {
            json.decodeFromString<UserPreferences>(it)
        } catch (e: Exception) {
            UserPreferences() // Return defaults on parsing error
        }
    } ?: UserPreferences() // Return defaults if not set
}
