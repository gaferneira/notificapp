package dev.gaferneira.notificapp.domain.model

/**
 * Information about an installed app. Not `@Serializable`: see [Rule].
 */
data class AppInfo(
    /** Package name (unique identifier) */
    val packageName: String,
    /** Display name of the app */
    val name: String,
    /** App category or type (optional) */
    val category: String? = null,
)
