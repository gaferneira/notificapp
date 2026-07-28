package dev.gaferneira.notificapp.domain.model

/**
 * One row of Home's "Recent Activity" feed: a rule execution joined with its source notification
 * and the rule that fired, flattened for direct display.
 *
 * @property executionId The rule execution's id.
 * @property ruleId The rule that fired.
 * @property ruleName The rule's display name at execution time.
 * @property notificationId The source notification's id (opens `features/notificationdetail`).
 * @property notificationTitle The source notification's title, nullable to match [Notification.title].
 * @property notificationContent The source notification's content, nullable to match [Notification.content].
 * @property packageName The source app's package name.
 * @property appName The source app's display name.
 * @property executedAt When the rule execution was recorded (epoch millis).
 */
data class RecentActivity(
    val executionId: String,
    val ruleId: String,
    val ruleName: String,
    val notificationId: String,
    val notificationTitle: String?,
    val notificationContent: String?,
    val packageName: String,
    val appName: String,
    val executedAt: Long,
)
