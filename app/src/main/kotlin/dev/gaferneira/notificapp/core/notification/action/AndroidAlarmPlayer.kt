package dev.gaferneira.notificapp.core.notification.action

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import javax.inject.Inject

/**
 * Plays an alarm sound via [RingtoneManager] and vibrates via the platform [Vibrator], using
 * whichever vibrator API is available for the running OS version.
 */
class AndroidAlarmPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmPlayer {

    override fun play(soundUri: String?) {
        val uri = soundUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val ringtone = RingtoneManager.getRingtone(context, uri)
        if (ringtone == null) {
            Timber.w("No ringtone resolved for alarm sound URI: $uri")
            return
        }
        ringtone.play()
    }

    override fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (!vibrator.hasVibrator()) return

        vibrator.vibrate(VibrationEffect.createOneShot(ALARM_VIBRATION_DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private companion object {
        const val ALARM_VIBRATION_DURATION_MS = 800L
    }
}
