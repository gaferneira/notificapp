package dev.gaferneira.notificapp.core.notification.action.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.gaferneira.notificapp.domain.model.VibrationPattern
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plays a looping alarm sound via [MediaPlayer] and vibrates with a repeating pattern via the
 * platform [Vibrator], holding alarm audio focus for the duration.
 *
 * Bound as a [Singleton] (see `core/di/ActionModule`) so that [stop] — invoked from the alarm
 * foreground service when the user dismisses or snoozes — acts on the very same player and
 * vibration that [play]/[vibrate] started. A per-injection instance would leak an unstoppable ring.
 *
 * [MediaPlayer] with `isLooping` is used instead of `Ringtone.setLooping` so a single code path
 * loops uniformly across the app's whole supported range (minSdk 26); `Ringtone` looping requires
 * API 28.
 */
@Singleton
class AndroidAlarmPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
) : AlarmPlayer {

    private val audioManager: AudioManager
        get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val alarmAudioAttributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private var mediaPlayer: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null

    @Synchronized
    override fun play(soundUri: String?) {
        stopSound()

        val uri = soundUri?.let(Uri::parse)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        val player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(alarmAudioAttributes)
                setDataSource(context, uri)
                isLooping = true
                prepare()
            }
        }.onFailure { e ->
            Timber.w(e, "Failed to prepare alarm sound for URI: $uri")
        }.getOrNull() ?: return

        requestAudioFocus()
        mediaPlayer = player
        player.start()
    }

    @Synchronized
    override fun vibrate(pattern: VibrationPattern) {
        val vibrator = resolveVibrator()
        if (!vibrator.hasVibrator()) return

        // Repeating waveform: wait, buzz, pause, then loop back to repeatIndex until cancelled.
        vibrator.vibrate(VibrationEffect.createWaveform(pattern.timings, pattern.repeatIndex))
    }

    @Synchronized
    override fun stop() {
        stopSound()
        resolveVibrator().cancel()
    }

    private fun stopSound() {
        mediaPlayer?.let { player ->
            runCatching {
                if (player.isPlaying) player.stop()
            }
            player.release()
        }
        mediaPlayer = null
        abandonAudioFocus()
    }

    private fun requestAudioFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(alarmAudioAttributes)
            .build()
        focusRequest = request
        audioManager.requestAudioFocus(request)
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun resolveVibrator(): Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        manager.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
}
