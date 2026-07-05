package dev.gaferneira.notificapp.features.notification

import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import dev.gaferneira.notificapp.domain.model.Notification

/**
 * Normalizes Android StatusBarNotification objects into domain Notification models.
 *
 * This class handles the extraction of text content from various notification formats
 * and creates a standardized representation for processing.
 */
class NotificationNormalizer {

    /**
     * Converts a StatusBarNotification into a domain Notification model.
     *
     * @param sbn The StatusBarNotification from the system
     * @param packageManager To resolve app names
     * @return Normalized Notification domain model
     */
    fun normalize(sbn: StatusBarNotification, packageManager: PackageManager): Notification {
        val packageName = sbn.packageName
        val appName = getAppName(packageName, packageManager)

        // Extract text content from the notification
        val title = extractTitle(sbn)
        val content = extractContent(sbn)
        val rawContent = buildRawContent(sbn, title, content)

        return Notification(
            id = generateNotificationId(sbn),
            packageName = packageName,
            appName = appName,
            title = title,
            content = content,
            rawContent = rawContent,
            timestamp = sbn.postTime,
            isProcessed = false,
            sbnKey = sbn.key,
        )
    }

    /**
     * Extracts the title from the notification.
     */
    private fun extractTitle(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras

        // Try various title sources
        return extras.getString(android.app.Notification.EXTRA_TITLE_BIG)
            ?: extras.getString(android.app.Notification.EXTRA_TITLE)
            ?: extras.getCharSequence(android.app.Notification.EXTRA_TITLE_BIG)?.toString()
            ?: extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
    }

    /**
     * Extracts the content text from the notification.
     */
    private fun extractContent(sbn: StatusBarNotification): String? {
        val extras = sbn.notification.extras

        // Try big text first (for expanded notifications)
        val bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString()
        if (!bigText.isNullOrBlank()) {
            return bigText
        }

        // Try regular text
        val text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        if (!text.isNullOrBlank()) {
            return text
        }

        // Try text lines (for multi-line notifications)
        val textLines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
        if (textLines != null && textLines.isNotEmpty()) {
            return textLines.joinToString("\n")
        }

        // Try subText as fallback
        val subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString()
        if (!subText.isNullOrBlank()) {
            return subText
        }

        // Last resort: try toString on extras
        return extras.toString().takeIf { it != "Bundle[EMPTY]" }
    }

    /**
     * Builds a raw content string containing all available text.
     */
    private fun buildRawContent(sbn: StatusBarNotification, title: String?, content: String?): String {
        val parts = mutableListOf<String>()

        title?.let { parts.add("Title: $it") }
        content?.let { parts.add("Content: $it") }

        // Add ticker text if available
        sbn.notification.tickerText?.toString()?.let {
            if (it.isNotBlank() && !parts.contains(it)) {
                parts.add("Ticker: $it")
            }
        }

        return parts.joinToString("\n")
    }

    /**
     * Gets the human-readable app name from the package name.
     */
    private fun getAppName(packageName: String, packageManager: PackageManager): String = try {
        val appInfo = packageManager.getApplicationInfo(packageName, 0)
        packageManager.getApplicationLabel(appInfo).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        // Fallback to package name if app info not found
        packageName
    }

    /**
     * Generates a unique ID for the notification.
     * Format: packageName_notificationId_postTime
     */
    private fun generateNotificationId(sbn: StatusBarNotification): String = "${sbn.packageName}_${sbn.id}_${sbn.postTime}"
}
