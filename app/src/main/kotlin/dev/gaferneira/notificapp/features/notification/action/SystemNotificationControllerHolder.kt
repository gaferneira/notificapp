package dev.gaferneira.notificapp.features.notification.action

import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds a reference to the currently connected [SystemNotificationController], if any.
 *
 * `NotificappListenerService` publishes itself here from `onListenerConnected()` and clears it
 * from `onListenerDisconnected()`/`onDestroy()` via [clear]. System-dependent action executors
 * read from this holder and return [dev.gaferneira.notificapp.domain.model.ActionOutcome.SKIPPED]
 * when the listener is not currently connected, per ADR 010.
 */
@Singleton
class SystemNotificationControllerHolder @Inject constructor() {
    private val ref = AtomicReference<SystemNotificationController?>(null)

    fun set(controller: SystemNotificationController?) = ref.set(controller)

    /**
     * Clear the holder only if it still holds [expected].
     *
     * If the system starts a new service instance and it calls [set] before the old instance's
     * teardown runs, an unconditional `set(null)` from the old instance's `onListenerDisconnected()`
     * or `onDestroy()` would wipe out the new registration. Using a compare-and-set here means
     * teardown only clears the reference it actually owns.
     */
    fun clear(expected: SystemNotificationController) = ref.compareAndSet(expected, null)

    fun get(): SystemNotificationController? = ref.get()
}
