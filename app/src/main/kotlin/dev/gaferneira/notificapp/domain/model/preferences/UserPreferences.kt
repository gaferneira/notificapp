package dev.gaferneira.notificapp.domain.model.preferences

import kotlinx.serialization.Serializable

/**
 * Aggregated container for all user preferences.
 *
 * This class holds all user preference settings for persistence and
 * provides a complete snapshot of the user's configuration.
 *
 * @property inboxFilterSettings Filter settings for the inbox screen
 * @property rulesFilterSettings Filter settings for the rules screen
 * @property themePreference Theme preference (system, light, dark)
 * @property version Version for migration handling
 */
@Serializable
data class UserPreferences(
    val inboxFilterSettings: InboxFilterSettings = InboxFilterSettings(),
    val rulesFilterSettings: RulesFilterSettings = RulesFilterSettings(),
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
