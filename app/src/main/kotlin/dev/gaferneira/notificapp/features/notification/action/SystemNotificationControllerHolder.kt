package dev.gaferneira.notificapp.features.notification.action

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a reference to the currently connected [SystemNotificationController], if any.
 *
 * `NotificappListenerService` publishes itself here from `onListenerConnected()` and clears it
 * from `onListenerDisconnected()`/`onDestroy()`. System-dependent action executors read from this
 * holder and return [dev.gaferneira.notificapp.domain.model.ActionOutcome.SKIPPED] when the
 * listener is not currently connected, per ADR 010.
 */
@Singleton
class SystemNotificationControllerHolder @Inject constructor() {
    private val ref = AtomicReference<SystemNotificationController?>(null)

    fun set(controller: SystemNotificationController?) = ref.set(controller)

    fun get(): SystemNotificationController? = ref.get()
}
