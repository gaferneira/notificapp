package dev.gaferneira.notificapp.domain.model.preferences

/**
 * Enum representing the user's preferred theme setting.
 */
enum class ThemePreference {
    /** Use the system's theme setting */
    SYSTEM,

    /** Force light theme */
    LIGHT,

    /** Force dark theme */
    DARK,
}
