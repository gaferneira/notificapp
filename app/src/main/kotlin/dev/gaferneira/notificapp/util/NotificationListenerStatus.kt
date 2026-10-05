package dev.gaferneira.notificapp.util

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import dev.gaferneira.notificapp.features.notification.NotificappListenerService
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
 * On API 30+ this deep-links straight to this app's own detail screen
 * (`ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS`). None of these screens is guaranteed to
 * resolve on every OEM skin, so it falls back to the generic listener list
 * (`ACTION_NOTIFICATION_LISTENER_SETTINGS`), then to the general settings screen.
 */
fun openNotificationListenerSettings(context: Context) {
    val candidates = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val component = ComponentName(context, NotificappListenerService::class.java)
            add(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString()),
            )
        }
        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }

    for (intent in candidates) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (e: ActivityNotFoundException) {
            Timber.e(e, "Settings screen %s not found, trying next fallback", intent.action)
        }
    }
    Timber.e("No settings screen could be opened for notification listener access")
}
