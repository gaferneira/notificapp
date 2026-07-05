package dev.gaferneira.notificapp.core.notification.action

/**
 * Narrow interface over the Android audio/vibration APIs that [AlarmActionExecutor] needs,
 * kept separate so the executor can be unit tested without touching real system services.
 */
interface AlarmPlayer {
    /**
     * Play an alarm sound. [soundUri] is a content URI string, or null to use the device's
     * default alarm sound.
     */
    fun play(soundUri: String?)

    /**
     * Vibrate the device with a short alarm pattern.
     */
    fun vibrate()
}
