package dev.gaferneira.notificapp.core.notification.action.alarm

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
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundConfig
import dev.gaferneira.notificapp.domain.model.AlarmBackgroundType
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_FULLSCREEN_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_DURATION_MINUTES
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SNOOZE_MAX_COUNT
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_SOUND_ENABLED
import dev.gaferneira.notificapp.domain.model.DEFAULT_ALARM_VIBRATION_ENABLED
import dev.gaferneira.notificapp.domain.model.VibrationPattern
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

    @Inject
    lateinit var alarmStateHolder: AlarmStateHolder

    @Inject
    lateinit var alarmUiIntentFactory: AlarmUiIntentFactory

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

        if (!current.suppressNotification) {
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
        }

        // Single active alarm: stop any prior ring before starting this one.
        alarmPlayer.stop()
        if (current.soundEnabled) alarmPlayer.play(current.soundUri)
        if (current.vibrationEnabled) alarmPlayer.vibrate(current.vibrationPattern)
        alarmStateHolder.setRinging(true)
    }

    private fun handleDismiss() {
        if (!current.suppressNotification) promoteToForeground()
        stopAlarm()
    }

    private fun handleSnooze() {
        if (!current.suppressNotification) promoteToForeground()
        // Snooze disabled, or already exhausted: stop instead of re-ringing. The notification's
        // Snooze action is already omitted in this case (see buildNotification), but this guard
        // also covers the AlarmActivity Snooze button, which reads its own (extra-carried) copy of
        // this state and could theoretically race with a stale UI.
        if (!current.canSnoozeAgain) {
            stopAlarm()
            return
        }
        scheduleReRing(current)
        stopAlarm()
    }

    /**
     * Single stop routine used by every stop branch: releases audio/vibration, removes the ongoing
     * notification, and stops the service.
     */
    private fun stopAlarm() {
        alarmStateHolder.setRinging(false)
        alarmPlayer.stop()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun scheduleReRing(request: AlarmRequest) {
        // Carry the incremented snooze count through the re-ring's own start intent, since
        // handleStart rebuilds `current` from scratch from the intent extras — this is the only
        // way the counter survives from one ring episode to the next re-ring.
        val reRingRequest = request.copy(snoozeCount = request.snoozeCount + 1)
        val delayMs = request.snoozeDurationMinutes * MINUTES_TO_MS
        val alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + delayMs
        // A suppressed alarm never calls promoteToForeground/startForeground, so the re-ring must
        // not be started via the foreground-service entry point either, or the service would crash
        // for not calling startForeground() within the OS time limit.
        val pendingIntent = if (request.suppressNotification) {
            PendingIntent.getService(
                this,
                REQUEST_RE_RING,
                startIntent(this, reRingRequest),
                PENDING_INTENT_FLAGS,
            )
        } else {
            PendingIntent.getForegroundService(
                this,
                REQUEST_RE_RING,
                startIntent(this, reRingRequest),
                PENDING_INTENT_FLAGS,
            )
        }
        // Inexact on purpose: a minutes-long snooze does not need SCHEDULE_EXACT_ALARM, and
        // setAndAllowWhileIdle still fires through Doze.
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        Timber.d("Alarm snoozed; will re-ring in ${delayMs / MINUTES_TO_MS} min")
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
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

        // Only offer Snooze while it's still enabled and not yet exhausted for this episode.
        if (current.canSnoozeAgain) {
            builder.addAction(0, getString(R.string.alarm_action_snooze), actionIntent(ACTION_SNOOZE, REQUEST_SNOOZE))
        }

        // Only raise the call-style full-screen UI when this alarm is configured for it; otherwise
        // it stays a plain (heads-up) notification.
        if (current.fullScreenEnabled) {
            builder.setFullScreenIntent(fullScreenIntent(), true)
        }
        return builder.build()
    }

    private fun actionIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, AlarmService::class.java).setAction(action)
        return PendingIntent.getForegroundService(this, requestCode, intent, PENDING_INTENT_FLAGS)
    }

    /**
     * Full-screen intent that raises the call-style alarm UI, built by [alarmUiIntentFactory] so
     * this pipeline-layer service never depends on `core/ui`/Compose directly. The system shows
     * this full-screen over the lock screen / when the screen is off, and degrades to the
     * heads-up notification otherwise (or when `USE_FULL_SCREEN_INTENT` is not granted).
     */
    private fun fullScreenIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_FULL_SCREEN,
        alarmUiIntentFactory.createFullScreenIntent(this, current),
        PENDING_INTENT_FLAGS,
    )

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
        title = getStringExtra(EXTRA_TITLE).orEmpty(),
        text = getStringExtra(EXTRA_TEXT).orEmpty(),
        appName = getStringExtra(EXTRA_APP_NAME).orEmpty(),
        snoozeCount = getIntExtra(EXTRA_SNOOZE_COUNT, 0),
        options = AlarmRingOptions(
            vibrationEnabled = getBooleanExtra(EXTRA_VIBRATION_ENABLED, DEFAULT_ALARM_VIBRATION_ENABLED),
            fullScreenEnabled = getBooleanExtra(EXTRA_FULLSCREEN_ENABLED, DEFAULT_ALARM_FULLSCREEN_ENABLED),
            soundEnabled = getBooleanExtra(EXTRA_SOUND_ENABLED, DEFAULT_ALARM_SOUND_ENABLED),
            vibrationPattern = VibrationPattern.fromId(getStringExtra(EXTRA_VIBRATION_PATTERN)),
            snooze = AlarmSnoozeSettings(
                enabled = getBooleanExtra(EXTRA_SNOOZE_ENABLED, DEFAULT_ALARM_SNOOZE_ENABLED),
                durationMinutes = getIntExtra(EXTRA_SNOOZE_DURATION_MINUTES, DEFAULT_ALARM_SNOOZE_DURATION_MINUTES),
                maxCount = getIntExtra(EXTRA_SNOOZE_MAX_COUNT, DEFAULT_ALARM_SNOOZE_MAX_COUNT),
            ),
            background = AlarmBackgroundConfig(
                type = AlarmBackgroundType.fromName(getStringExtra(EXTRA_BACKGROUND_TYPE)),
                presetId = getStringExtra(EXTRA_BACKGROUND_PRESET_ID),
                imageUri = getStringExtra(EXTRA_BACKGROUND_IMAGE_URI),
                imageIsDark = getBooleanExtra(EXTRA_BACKGROUND_IMAGE_IS_DARK, DEFAULT_ALARM_BACKGROUND_IMAGE_IS_DARK),
            ),
            suppressNotification = getBooleanExtra(EXTRA_SUPPRESS_NOTIFICATION, false),
        ),
    )

    companion object {
        private const val CHANNEL_ID = "alarm_ringing"
        private const val NOTIFICATION_ID = 1001

        private const val ACTION_START = "dev.gaferneira.notificapp.action.ALARM_START"
        private const val ACTION_DISMISS = "dev.gaferneira.notificapp.action.ALARM_DISMISS"
        private const val ACTION_SNOOZE = "dev.gaferneira.notificapp.action.ALARM_SNOOZE"

        private const val EXTRA_SOUND_URI = "extra_sound_uri"
        private const val EXTRA_VIBRATION_ENABLED = "extra_vibration_enabled"
        private const val EXTRA_FULLSCREEN_ENABLED = "extra_fullscreen_enabled"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_TEXT = "extra_text"
        private const val EXTRA_APP_NAME = "extra_app_name"
        private const val EXTRA_SOUND_ENABLED = "extra_sound_enabled"
        private const val EXTRA_VIBRATION_PATTERN = "extra_vibration_pattern"
        private const val EXTRA_SNOOZE_ENABLED = "extra_snooze_enabled"
        private const val EXTRA_SNOOZE_DURATION_MINUTES = "extra_snooze_duration_minutes"
        private const val EXTRA_SNOOZE_MAX_COUNT = "extra_snooze_max_count"
        private const val EXTRA_SNOOZE_COUNT = "extra_snooze_count"
        private const val EXTRA_BACKGROUND_TYPE = "extra_background_type"
        private const val EXTRA_BACKGROUND_PRESET_ID = "extra_background_preset_id"
        private const val EXTRA_BACKGROUND_IMAGE_URI = "extra_background_image_uri"
        private const val EXTRA_BACKGROUND_IMAGE_IS_DARK = "extra_background_image_is_dark"
        private const val EXTRA_SUPPRESS_NOTIFICATION = "extra_suppress_notification"

        private const val REQUEST_DISMISS = 1
        private const val REQUEST_SNOOZE = 2
        private const val REQUEST_RE_RING = 3
        private const val REQUEST_FULL_SCREEN = 4

        private const val PENDING_INTENT_FLAGS =
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE

        /** Minutes-to-milliseconds conversion for [AlarmRequest.snoozeDurationMinutes]. */
        private const val MINUTES_TO_MS = 60_000L

        private val EMPTY_REQUEST = AlarmRequest(
            soundUri = null,
            title = "",
            text = "",
            appName = "",
        )

        /** Build the [ACTION_START] intent that rings the alarm described by [request]. */
        fun startIntent(context: Context, request: AlarmRequest): Intent = Intent(context, AlarmService::class.java)
            .setAction(ACTION_START)
            .putExtra(EXTRA_SOUND_URI, request.soundUri)
            .putExtra(EXTRA_VIBRATION_ENABLED, request.vibrationEnabled)
            .putExtra(EXTRA_FULLSCREEN_ENABLED, request.fullScreenEnabled)
            .putExtra(EXTRA_TITLE, request.title)
            .putExtra(EXTRA_TEXT, request.text)
            .putExtra(EXTRA_APP_NAME, request.appName)
            .putExtra(EXTRA_SOUND_ENABLED, request.soundEnabled)
            .putExtra(EXTRA_VIBRATION_PATTERN, request.vibrationPattern.id)
            .putExtra(EXTRA_SNOOZE_ENABLED, request.snoozeEnabled)
            .putExtra(EXTRA_SNOOZE_DURATION_MINUTES, request.snoozeDurationMinutes)
            .putExtra(EXTRA_SNOOZE_MAX_COUNT, request.snoozeMaxCount)
            .putExtra(EXTRA_SNOOZE_COUNT, request.snoozeCount)
            .putExtra(EXTRA_BACKGROUND_TYPE, request.backgroundType.name)
            .putExtra(EXTRA_BACKGROUND_PRESET_ID, request.backgroundPresetId)
            .putExtra(EXTRA_BACKGROUND_IMAGE_URI, request.backgroundImageUri)
            .putExtra(EXTRA_BACKGROUND_IMAGE_IS_DARK, request.backgroundImageIsDark)
            .putExtra(EXTRA_SUPPRESS_NOTIFICATION, request.suppressNotification)

        /** Intent that dismisses the ringing alarm — used by the alarm UI's Dismiss control. */
        fun dismissIntent(context: Context): Intent = Intent(context, AlarmService::class.java).setAction(ACTION_DISMISS)

        /** Intent that snoozes the ringing alarm — used by the alarm UI's Snooze control. */
        fun snoozeIntent(context: Context): Intent = Intent(context, AlarmService::class.java).setAction(ACTION_SNOOZE)
    }
}
