package dev.gaferneira.notificapp.core.notification.action.alarm

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Starts the [AlarmService] foreground service to ring an alarm. Uses
 * [androidx.core.content.ContextCompat.startForegroundService] so the service is allowed to promote itself to the
 * foreground within the required window on all supported OS versions.
 *
 * Refuses to start when app notifications are disabled: the alarm's Dismiss/Snooze controls live
 * only on the ongoing notification, so ringing without it would be unstoppable.
 */
class AndroidAlarmController @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmController {

    override fun start(request: AlarmRequest): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Timber.w("Alarm not started: notifications are disabled, so it could not be stopped")
            return false
        }
        ContextCompat.startForegroundService(context, AlarmService.startIntent(context, request))
        return true
    }
}
