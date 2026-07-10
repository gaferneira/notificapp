package dev.gaferneira.notificapp.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import timber.log.Timber

/**
 * Checks whether this app is currently enabled as a notification listener,
 * i.e. whether the user granted `NotificationListenerService` access in
 * system settings. There is no direct API for this - Android only exposes
 * it via the `enabled_notification_listeners` secure setting.
 */
fun isNotificationListenerEnabled(context: Context): Boolean {
    val packageName = context.packageName
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners",
    )
    return flat
        ?.split(":")
        ?.any { component -> component.startsWith("$packageName/") } == true
}

/**
 * Opens the system screen where the user can grant or revoke this app's
 * notification listener access.
 *
 * `ACTION_NOTIFICATION_LISTENER_SETTINGS` isn't guaranteed to resolve on
 * every OEM skin, so this falls back to the general settings screen if
 * it fails to launch.
 */
fun openNotificationListenerSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "Notification listener settings screen not found, falling back to general settings")
        try {
            val fallbackIntent = Intent(Settings.ACTION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (fallbackException: ActivityNotFoundException) {
            Timber.e(fallbackException, "General settings screen not found either")
        }
    }
}
