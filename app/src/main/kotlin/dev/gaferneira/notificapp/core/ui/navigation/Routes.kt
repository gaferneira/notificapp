package dev.gaferneira.notificapp.core.ui.navigation

/**
 * Centralized route factory for type-safe navigation.
 *
 * This object provides factory methods for creating Screen instances,
 * ensuring consistent route creation across the app.
 *
 * Benefits:
 * - Centralized route creation logic
 * - Named parameters for clarity
 * - Easy to refactor routes
 * - Consistent defaults
 *
 * Example usage:
 * ```kotlin
 * navigator.navigate(Routes.inbox())
 * navigator.navigate(Routes.notificationDetails("abc123"))
 * navigator.navigate(Routes.ruleEditor(notificationId = "abc123"))
 * ```
 */
object Routes {
    /**
     * Inbox screen - main screen showing captured notifications.
     */
    fun inbox(): Screen = Screen.Inbox

    /**
     * Rules screen - list of extraction rules.
     */
    fun rules(): Screen = Screen.Rules

    /**
     * Settings screen - app settings and configuration.
     */
    fun settings(): Screen = Screen.Settings

    /**
     * Notification details screen - view a specific notification.
     *
     * @param notificationId The ID of the notification to display
     */
    fun notificationDetails(notificationId: String): Screen = Screen.NotificationDetails(notificationId)

    /**
     * App selection screen - select which apps to monitor.
     *
     * @param isInitialSetup Whether this is the first-time setup flow
     */
    fun appSelection(isInitialSetup: Boolean = false): Screen = Screen.AppSelection(isInitialSetup)

    /**
     * App selection screen specifically for rule creation.
     *
     * @param preSelectedApps List of apps already selected for the rule
     */
    fun appSelectionForRule(preSelectedApps: List<String> = emptyList()): Screen = Screen.AppSelectionForRule(preSelectedApps)

    /**
     * Rule editor screen - create or edit extraction rules.
     *
     * @param ruleId The ID of the rule to edit (null for new rule)
     * @param notificationId Optional notification ID to pre-populate the rule
     */
    fun ruleEditor(
        ruleId: String? = null,
        notificationId: String? = null,
    ): Screen = Screen.RuleEditor(ruleId, notificationId)

    /**
     * Onboarding screen - initial setup for notification permission.
     */
    fun onboarding(): Screen = Screen.Onboarding

    /**
     * Webhook list screen - view, edit, and delete user-defined webhooks.
     */
    fun webhookList(): Screen = Screen.WebhookList

    /**
     * Webhook editor screen - create or edit a webhook.
     *
     * @param webhookId The id of the webhook to edit (null for a new webhook)
     */
    fun webhookEditor(webhookId: String? = null): Screen = Screen.WebhookEditor(webhookId)
}

/**
 * Extension function to navigate using Routes.
 *
 * Example:
 * ```kotlin
 * navigator.navigateTo { inbox() }
 * ```
 */
fun Navigator.navigateTo(routeFactory: Routes.() -> Screen) {
    navigate(Routes.routeFactory())
}

/**
 * Extension function to clear and navigate using Routes.
 *
 * Example:
 * ```kotlin
 * navigator.clearAndNavigateTo { inbox() }
 * ```
 */
fun Navigator.clearAndNavigateTo(routeFactory: Routes.() -> Screen) {
    clearAndNavigate(Routes.routeFactory())
}
