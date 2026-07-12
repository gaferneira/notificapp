package dev.gaferneira.notificapp.domain

/**
 * Resolves whether this app is currently enabled as a notification listener, so ViewModels never
 * touch `Context` or the underlying platform secure-settings lookup directly (a static Android
 * call that can't be stubbed on a pure JVM unit test).
 */
fun interface NotificationListenerStatusProvider {
    fun isEnabled(): Boolean
}
