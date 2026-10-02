package dev.gaferneira.notificapp.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation routes for the app using Navigation3.
 *
 * All screens are defined as serializable sealed classes for type-safe navigation.
 */
@Serializable
sealed class Screen : NavKey {

    /**
     * Inbox screen - main screen showing captured notifications.
     */
    @Serializable
    data object Inbox : Screen()

    /**
     * Home dashboard - launch summary: monitoring status, starter rules or recurring-notification
     * suggestions, weekly stats, and recent activity. Occupies the first bottom-nav slot Inbox
     * previously held.
     */
    @Serializable
    data object Home : Screen()

    /**
     * Rules screen - list of extraction rules.
     */
    @Serializable
    data object Rules : Screen()

    /**
     * Data Browser screen - browse, filter, search, and export extracted rule data.
     */
    @Serializable
    data object Data : Screen()

    /**
     * Settings screen - app settings and configuration.
     */
    @Serializable
    data object Settings : Screen()

    /**
     * Notification details screen - view a specific notification.
     */
    @Serializable
    data class NotificationDetails(val notificationId: String) : Screen()

    /**
     * App selection screen - select which apps to monitor.
     */
    @Serializable
    data class AppSelection(val isInitialSetup: Boolean = false) : Screen()

    /**
     * App selection screen specifically for rule creation.
     */
    @Serializable
    data class AppSelectionForRule(val preSelectedApps: List<String> = emptyList()) : Screen()

    /**
     * Rule editor screen - create or edit extraction rules.
     */
    @Serializable
    data class RuleEditor(
        val ruleId: String? = null,
        val notificationId: String? = null,
        val templateAssetFileName: String? = null,
    ) : Screen()

    /**
     * Starter rule template gallery with category filters; the create-rule entry point.
     */
    @Serializable
    data object RuleTemplates : Screen()

    /**
     * Onboarding screen - initial setup for notification permission.
     */
    @Serializable
    data object Onboarding : Screen()

    /**
     * Webhook list screen - view, edit, and delete user-defined webhooks.
     */
    @Serializable
    data object WebhookList : Screen()

    /**
     * Webhook editor screen - create or edit a webhook.
     */
    @Serializable
    data class WebhookEditor(val webhookId: String? = null) : Screen()
}
