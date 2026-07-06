package dev.gaferneira.notificapp.core.notification.action

/**
 * Narrow interface over the Android audio/vibration APIs that [AlarmActionExecutor] needs,
 * kept separate so the executor can be unit tested without touching real system services.
 */
interface AlarmPlayer {
    /**
     * Start playing an alarm sound in a loop. [soundUri] is a content URI string, or null to use
     * the device's default alarm sound. Any sound already playing is stopped first, so at most one
     * alarm is ever audible. The sound keeps looping until [stop] is called.
     */
    fun play(soundUri: String?)

    /**
     * Start vibrating the device with a repeating alarm pattern. The vibration repeats until [stop]
     * is called.
     */
    fun vibrate()

    /**
     * Stop any looping sound and repeating vibration started by [play]/[vibrate] and release the
     * audio focus held while ringing. Safe to call when nothing is playing.
     */
    fun stop()
}
