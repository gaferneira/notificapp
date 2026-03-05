package dev.gaferneira.notificapp.domain.model

/**
 * Domain model representing an app selected for notification monitoring.
 *
 * @property packageName The unique package name of the app (e.g., "com.whatsapp")
 * @property appName The display name of the app (e.g., "WhatsApp")
 * @property isEnabled Whether notification monitoring is enabled for this app
 * @property createdAt Timestamp when the app was added
 */
data class SelectedApp(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)
