package dev.gaferneira.notificapp.features.notification.action

/**
 * Narrow interface over the Android system notification operations that action executors need.
 *
 * Implemented by `NotificappListenerService` (the only component that can call
 * `cancelNotification`/`snoozeNotification`, inherited from `NotificationListenerService`) and
 * published to [SystemNotificationControllerHolder] while the listener is connected.
 */
interface SystemNotificationController {
    fun cancel(sbnKey: String)
    fun snooze(sbnKey: String, durationMs: Long)
}
