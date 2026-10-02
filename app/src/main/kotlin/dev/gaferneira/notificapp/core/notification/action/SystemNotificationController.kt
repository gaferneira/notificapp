package dev.gaferneira.notificapp.core.notification.action

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

    /**
     * Reply to the notification identified by [sbnKey] using its Android direct-reply
     * (`RemoteInput`) action, if it has one.
     *
     * This is inherently fragile: only apps that expose a `Notification.Action` with a non-empty
     * `RemoteInput` support it, the notification must still be live (present in
     * `activeNotifications`), and delivering the built `PendingIntent` can fail for reasons outside
     * our control (the target app crashed, revoked the pending intent, etc). None of that is a bug
     * in this app - it's the nature of poking another app's notification via a reply action it may
     * or may not have wired up - so this never throws to the caller.
     *
     * @return `true` if a reply was actually dispatched, `false` if the notification has no usable
     * reply action, is no longer live, or dispatch failed for any reason.
     */
    fun reply(sbnKey: String, text: String): Boolean
}
