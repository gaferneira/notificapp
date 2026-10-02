package dev.gaferneira.notificapp.domain.model

/**
 * One "you could automate this" candidate produced by
 * [dev.gaferneira.notificapp.core.notification.RecurringNotificationSuggester]: a group of
 * notifications from the same app that normalize to the same title pattern, repeating often enough
 * to be worth suggesting a rule for.
 *
 * @property packageName The source app's package name.
 * @property appName The source app's display name.
 * @property normalizedTitleKey The stable grouping key produced by
 *   [dev.gaferneira.notificapp.core.notification.NotificationTitleNormalizer]; also the dismissal key.
 * @property sampleTitle Raw title of the group's most recent member, for display.
 * @property sampleContent Raw content/body of the group's most recent member, for display.
 * @property sampleNotificationId Notification id of the group's most recent member; seeds
 *   `Screen.RuleEditor(notificationId = ...)` when the user taps "Create rule from this".
 * @property occurrences How many notifications in the window belong to this group.
 * @property distinctDays How many distinct calendar days those occurrences span.
 * @property coverage How much an existing rule already covers this pattern.
 */
data class RecurringSuggestion(
    val packageName: String,
    val appName: String,
    val normalizedTitleKey: String,
    val sampleTitle: String,
    val sampleContent: String? = null,
    val sampleNotificationId: String,
    val occurrences: Int,
    val distinctDays: Int,
    val coverage: RuleCoverage,
)
