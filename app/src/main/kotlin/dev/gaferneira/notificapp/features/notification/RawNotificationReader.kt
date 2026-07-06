package dev.gaferneira.notificapp.features.notification

import android.content.pm.PackageManager
import android.service.notification.StatusBarNotification
import dev.gaferneira.notificapp.core.notification.RawNotificationData
import timber.log.Timber

/**
 * Reads the fields [dev.gaferneira.notificapp.core.notification.NotificationNormalizer] needs out
 * of a system [StatusBarNotification]. Deliberately no branching logic beyond null-safety - the
 * normalization decisions live in the pure normalizer, not here.
 */
fun StatusBarNotification.toRawData(): RawNotificationData {
    val extras = notification.extras

    val title = extras.getString(android.app.Notification.EXTRA_TITLE_BIG)
        ?: extras.getString(android.app.Notification.EXTRA_TITLE)
        ?: extras.getCharSequence(android.app.Notification.EXTRA_TITLE_BIG)?.toString()
        ?: extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()

    return RawNotificationData(
        packageName = packageName,
        notificationId = id,
        postTime = postTime,
        key = key,
        title = title,
        text = extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString(),
        bigText = extras.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT)?.toString(),
        textLines = extras.getCharSequenceArray(android.app.Notification.EXTRA_TEXT_LINES)
            ?.map { it.toString() }
            ?: emptyList(),
        subText = extras.getCharSequence(android.app.Notification.EXTRA_SUB_TEXT)?.toString(),
        tickerText = notification.tickerText?.toString(),
    )
}

/**
 * Resolves a human-readable app name for [packageName], falling back to the package name itself
 * if the app can't be looked up.
 */
fun PackageManager.resolveAppName(packageName: String): String = try {
    val appInfo = getApplicationInfo(packageName, 0)
    getApplicationLabel(appInfo).toString()
} catch (e: PackageManager.NameNotFoundException) {
    Timber.w(e, "App info not found for package: $packageName")
    packageName
}
