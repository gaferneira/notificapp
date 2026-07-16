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
 * @property retentionPeriod How long captured notifications are kept before auto-deletion.
 * Defaults to [RetentionPeriod.NEVER] so existing installs don't silently start deleting data
 * until the user explicitly opts into a retention window.
 * @property version Version for migration handling
 */
@Serializable
data class UserPreferences(
    val inboxFilterSettings: InboxFilterSettings = InboxFilterSettings(),
    val rulesFilterSettings: RulesFilterSettings = RulesFilterSettings(),
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    val retentionPeriod: RetentionPeriod = RetentionPeriod.NEVER,
    val version: Int = CURRENT_VERSION,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}
