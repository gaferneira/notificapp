package dev.gaferneira.notificapp.domain.model

import kotlinx.serialization.Serializable

/**
 * Information about an installed app.
 */
@Serializable
data class AppInfo(
    /** Package name (unique identifier) */
    val packageName: String,
    /** Display name of the app */
    val name: String,
    /** App category or type (optional) */
    val category: String? = null,
)
