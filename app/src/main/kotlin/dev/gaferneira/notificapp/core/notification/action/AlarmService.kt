package dev.gaferneira.notificapp.core.notification.action

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_VIBRATION_ENABLED
import timber.log.Timber
import javax.inject.Inject

/**
 * Foreground service that owns a ringing [dev.gaferneira.notificapp.domain.model.ActionType.CREATE_ALARM]
 * alarm for its whole lifetime: looping audio + repeating vibration (via [AlarmPlayer]) plus the
 * ongoing Dismiss/Snooze notification. Decoupling playback from the notification-listener coroutine
 * that triggered it is what lets the alarm keep ringing until the user acts.
 *
 * Commands arrive as intents: [ACTION_START] (from [AndroidAlarmController] and the snooze re-ring),
 * [ACTION_DISMISS] and [ACTION_SNOOZE] (from the notification actions). Per ADR 013 the alarm's
 * lifetime is owned here, not by `AlarmActionExecutor`.
 */
@AndroidEntryPoint
class AlarmService : Service() {

    @Inject
    lateinit var alarmPlayer: AlarmPlayer

    private var current: AlarmRequest = EMPTY_REQUEST

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_DISMISS -> handleDismiss()
            ACTION_SNOOZE -> handleSnooze()
            else -> {
                // Unknown/null intent (e.g. a system restart): make sure we don't leave a
                // half-started foreground service lingering.
                promoteToForeground()
                stopAlarm()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Defensive: never leave audio/vibration running if the service dies for any reason.
        alarmPlayer.stop()
        super.onDestroy()
    }

    private fun handleStart(intent: Intent) {
        current = intent.toAlarmRequest()

        // Without the ongoing notification there is no Dismiss/Snooze, so a ring would be
        // unstoppable. AndroidAlarmController already guards this for the normal path; this covers
        // the AlarmManager snooze re-ring, which starts the service directly.
        if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
            Timber.w("Alarm not rung: notifications are disabled, so it could not be stopped")
            promoteToForeground()
            stopAlarm()
            return
        }

        promoteToForeground()

        // Single active alarm: stop any prior ring before starting this one.
        alarmPlayer.stop()
        alarmPlayer.play(current.soundUri)
        if (current.vibrationEnabled) alarmPlayer.vibrate()
    }

    private fun handleDismiss() {
        promoteToForeground()
        stopAlarm()
    }

    private fun handleSnooze() {
        promoteToForeground()
        scheduleReRing(current)
        stopAlarm()
    }

    /**
     * Single stop routine used by every stop branch: releases audio/vibration, removes the ongoing
     * notification, and stops the service.
     */
    private fun stopAlarm() {
        alarmPlayer.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleReRing(request: AlarmRequest) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + SNOOZE_DELAY_MS
        val pendingIntent = PendingIntent.getForegroundService(
            this,
            REQUEST_RE_RING,
            startIntent(this, request),
            PENDING_INTENT_FLAGS,
        )
        // Inexact on purpose: a minutes-long snooze does not need SCHEDULE_EXACT_ALARM, and
        // setAndAllowWhileIdle still fires through Doze.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        Timber.d("Alarm snoozed; will re-ring in ${SNOOZE_DELAY_MS / 60_000} min")
    }

    /**
     * Promote to foreground with the ongoing alarm notification. Wrapped defensively so an
     * unexpected framework failure does not crash the service while an alarm is ringing.
     */
    private fun promoteToForeground() {
        ensureChannel()
        runCatching {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), foregroundServiceType())
        }.onFailure { e ->
            Timber.w(e, "Could not post the ongoing alarm notification")
        }
    }

    private fun buildNotification(): Notification {
        val text = current.text.ifBlank { getString(R.string.alarm_notification_text) }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(current.title.ifBlank { getString(R.string.alarm_notification_title) })
            .setContentText(text)
            .setSubText(current.appName.ifBlank { null })
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(0, getString(R.string.alarm_action_dismiss), actionIntent(ACTION_DISMISS, REQUEST_DISMISS))
            .addAction(0, getString(R.string.alarm_action_snooze), actionIntent(ACTION_SNOOZE, REQUEST_SNOOZE))
            .build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AlarmService::class.java).setAction(action)
        return PendingIntent.getForegroundService(this, requestCode, intent, PENDING_INTENT_FLAGS)
    }

    private fun ensureChannel() {
        // The service plays the sound and drives vibration itself, so the channel is silent to
        // avoid a second sound stacking on top of the looping MediaPlayer.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.alarm_channel_description)
            setSound(null, null)
            enableVibration(false)
        }
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun foregroundServiceType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
    } else {
        0
    }

    private fun Intent.toAlarmRequest(): AlarmRequest = AlarmRequest(
        soundUri = getStringExtra(EXTRA_SOUND_URI),
        vibrationEnabled = getBooleanExtra(EXTRA_VIBRATION_ENABLED, DEFAULT_ALARM_VIBRATION_ENABLED),
        title = getStringExtra(EXTRA_TITLE).orEmpty(),
        text = getStringExtra(EXTRA_TEXT).orEmpty(),
        appName = getStringExtra(EXTRA_APP_NAME).orEmpty(),
    )

    companion object {
        private const val CHANNEL_ID = "alarm_ringing"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "dev.gaferneira.notificapp.action.ALARM_START"
        private const val ACTION_DISMISS = "dev.gaferneira.notificapp.action.ALARM_DISMISS"
        private const val ACTION_SNOOZE = "dev.gaferneira.notificapp.action.ALARM_SNOOZE"

        private const val EXTRA_SOUND_URI = "extra_sound_uri"
        private const val EXTRA_VIBRATION_ENABLED = "extra_vibration_enabled"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TEXT = "extra_text"
        private const val EXTRA_APP_NAME = "extra_app_name"

        private const val REQUEST_DISMISS = 1
        private const val REQUEST_SNOOZE = 2
        private const val REQUEST_RE_RING = 3

        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        /** Fixed 5-minute snooze for iteration 1 (alarm-appropriate; shorter than the 15-min notification snooze). */
        private const val SNOOZE_DELAY_MS = 5L * 60L * 1000L

        private val EMPTY_REQUEST = AlarmRequest(
            soundUri = null,
            vibrationEnabled = DEFAULT_ALARM_VIBRATION_ENABLED,
            title = "",
            text = "",
            appName = "",
        )

        /** Build the [ACTION_START] intent that rings the alarm described by [request]. */
        fun startIntent(context: Context, request: AlarmRequest): Intent = Intent(context, AlarmService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_SOUND_URI, request.soundUri)
            .putExtra(EXTRA_VIBRATION_ENABLED, request.vibrationEnabled)
            .putExtra(EXTRA_TITLE, request.title)
            .putExtra(EXTRA_TEXT, request.text)
            .putExtra(EXTRA_APP_NAME, request.appName)
    }
}
