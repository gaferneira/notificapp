package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.domain.model.Notification

/**
 * Normalizes raw notification data into domain [Notification] models.
 *
 * Pure Kotlin: the Android-facing extraction of [RawNotificationData] from a
 * `StatusBarNotification` lives in `features/notification/RawNotificationReader.kt`, keeping this
 * class testable on the JVM.
 */
class NotificationNormalizer {

    /**
     * Converts [raw] notification data into a domain [Notification] model.
     *
     * @param raw Raw fields read from the system notification
     * @param appName Resolved human-readable app name
     * @return Normalized Notification domain model
     */
    fun normalize(raw: RawNotificationData, appName: String): Notification {
        val content = extractContent(raw)
        val rawContent = buildRawContent(raw, raw.title, content)

        return Notification(
            id = generateNotificationId(raw),
            packageName = raw.packageName,
            appName = appName,
            title = raw.title,
            content = content,
            rawContent = rawContent,
            timestamp = raw.postTime,
            isProcessed = false,
            sbnKey = raw.key,
        )
    }

    /**
     * Extracts the content text from the notification, in priority order: big text, regular
     * text, multi-line text, sub-text. Returns null if none of those carry any content - there is
     * deliberately no further fallback (e.g. dumping the raw extras bundle), since a notification
     * with none of these fields has nothing meaningful to extract from.
     */
    private fun extractContent(raw: RawNotificationData): String? = raw.bigText?.takeIf { it.isNotBlank() }
        ?: raw.text?.takeIf { it.isNotBlank() }
        ?: raw.textLines.takeIf { it.isNotEmpty() }?.joinToString("\n")
        ?: raw.subText?.takeIf { it.isNotBlank() }

    /**
     * Builds a raw content string containing all available text.
     */
    private fun buildRawContent(raw: RawNotificationData, title: String?, content: String?): String {
        val parts = mutableListOf<String>()

        title?.let { parts.add("Title: $it") }
        content?.let { parts.add("Content: $it") }

        raw.tickerText?.takeIf { it.isNotBlank() && it !in parts }?.let {
            parts.add("Ticker: $it")
        }

        return parts.joinToString("\n")
    }

    /**
     * Generates a unique ID for the notification.
     * Format: packageName_notificationId_postTime - dedup and DB identity depend on this exact
     * shape, do not change it without a migration plan.
     */
    private fun generateNotificationId(raw: RawNotificationData): String = "${raw.packageName}_${raw.notificationId}_${raw.postTime}"
}
