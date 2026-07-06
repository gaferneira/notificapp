package dev.gaferneira.notificapp.core.notification

/**
 * Plain data holder capturing everything [NotificationNormalizer] reads from an Android
 * notification, with no Android imports.
 *
 * This is the seam between the Android-facing reader (which knows how to pull these values out
 * of a `StatusBarNotification` + `PackageManager`) and the pure normalization logic, so the logic
 * can be unit-tested on the JVM.
 *
 * @property packageName Package name of the app that posted the notification
 * @property notificationId System notification ID
 * @property postTime When the notification was posted (epoch millis)
 * @property key System notification key, if available
 * @property title Resolved title (EXTRA_TITLE_BIG / EXTRA_TITLE)
 * @property text EXTRA_TEXT
 * @property bigText EXTRA_BIG_TEXT
 * @property textLines EXTRA_TEXT_LINES
 * @property subText EXTRA_SUB_TEXT
 * @property tickerText Notification ticker text
 */
data class RawNotificationData(
    val packageName: String,
    val notificationId: Int,
    val postTime: Long,
    val key: String?,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val textLines: List<String>,
    val subText: String?,
    val tickerText: String?,
)
