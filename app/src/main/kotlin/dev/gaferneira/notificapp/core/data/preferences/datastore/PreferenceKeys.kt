package dev.gaferneira.notificapp.core.data.preferences.datastore

import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Preference keys for DataStore storage.
 *
 * These keys define the structure for storing user preferences in DataStore.
 */
internal object PreferenceKeys {
    /** Key for storing the complete user preferences as JSON */
    val USER_PREFERENCES = stringPreferencesKey("user_preferences")
}
