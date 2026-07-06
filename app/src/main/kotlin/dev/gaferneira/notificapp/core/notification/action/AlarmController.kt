package dev.gaferneira.notificapp.core.notification.action

/**
 * What to show and play for a ringing alarm. [title]/[text]/[appName] come from the notification
 * that triggered the alarm so the ongoing notification reflects it, rather than static text.
 */
data class AlarmRequest(
    val soundUri: String?,
    val vibrationEnabled: Boolean,
    val fullScreenEnabled: Boolean,
    val title: String,
    val text: String,
    val appName: String,
)

/**
 * Narrow seam over "start a ringing alarm", kept separate so [AlarmActionExecutor] stays a pure
 * unit testable against a fake, without touching Android's `Context`/`startForegroundService`.
 *
 * The Android implementation ([AndroidAlarmController]) starts the [AlarmService] foreground
 * service, which then owns the ring lifecycle (looping audio, vibration, the ongoing
 * Dismiss/Snooze notification). The executor's job ends once the service is started.
 */
interface AlarmController {
    /**
     * Start ringing the alarm described by [request]. If an alarm is already ringing it is
     * restarted for this request, so at most one alarm rings at a time.
     *
     * Returns `true` if the alarm was started, or `false` if it was refused — notably when app
     * notifications are disabled, since without the ongoing notification the alarm would have no
     * Dismiss/Snooze controls and could not be stopped.
     */
    fun start(request: AlarmRequest): Boolean
}
