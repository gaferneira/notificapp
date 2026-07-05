package dev.gaferneira.notificapp.domain.model

/**
 * Domain model representing a captured notification.
 *
 * @property id Unique identifier (usually packageName + notificationId + timestamp)
 * @property packageName Package name of the app that posted the notification
 * @property appName Display name of the app
 * @property title Notification title
 * @property content Notification content/body text
 * @property rawContent Full raw text representation of the notification
 * @property timestamp When the notification was received
 * @property isProcessed Whether this notification has been processed by rules
 * @property appliedRulesCount Number of rules that have been applied to this notification
 */
data class Notification(
    val id: String,
    val packageName: String,
    val appName: String,
    val title: String?,
    val content: String?,
    val rawContent: String,
    val timestamp: Long,
    val isProcessed: Boolean = false,
    val appliedRulesCount: Int = 0,
    val sbnKey: String? = null,
) {
    val app = AppInfo(packageName, appName)
}
