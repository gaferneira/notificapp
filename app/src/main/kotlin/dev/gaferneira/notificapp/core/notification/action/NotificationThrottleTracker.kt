package dev.gaferneira.notificapp.core.notification.action

import dev.gaferneira.notificapp.domain.repository.RuleExecutionRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks per-`rule action + source app` throttle windows for [SnoozeMode.THROTTLE] snoozes: the
 * first match in a rolling window delivers, further matches inside that window are suppressed.
 *
 * Modeled on [dev.gaferneira.notificapp.core.notification.NotificationDeduplicator]'s hybrid
 * pattern: an in-memory `Map` fast path (steady-state decisions never touch the database) guarded
 * by a [Mutex] (the read-decide-write is check-then-act, so per-access thread-safety alone isn't
 * enough), backed by a single indexed [RuleExecutionRepository] lookback on a cache miss so the
 * decision survives process restart.
 *
 * A concrete `@Inject` class with no interface, like `SnoozeReleaseTracker` - Hilt provides it
 * directly, no `ActionModule` binding needed.
 */
@Singleton
class NotificationThrottleTracker @Inject constructor(
    private val ruleExecutionRepository: RuleExecutionRepository,
    private val timeProvider: CurrentTimeProvider,
) {
    // key ("actionId|packageName") -> epoch millis of the delivery that opened its window.
    private val lastDelivered = mutableMapOf<String, Long>()
    private val mutex = Mutex()

    /**
     * Atomically decide whether this match opens/continues a delivery window.
     *
     * @param resetAt watermark (epoch millis, see [dev.gaferneira.notificapp.domain.model.getThrottleResetAt]);
     * any delivery recorded before this no longer counts, so an edit that bumps it forces the
     * next match to deliver a fresh window.
     * @return `true` to let the notification through (window opens/stays open at [resetAt] or
     * later), `false` to suppress (drop) it.
     */
    suspend fun shouldDeliver(
        actionId: String,
        packageName: String,
        windowMs: Long,
        resetAt: Long,
    ): Boolean = mutex.withLock {
        val key = scopeKey(actionId, packageName)
        val now = timeProvider.nowEpochMillis()
        cleanupOldEntries(now, windowMs)

        val last = lastDelivered[key]
            ?: dbLastDeliveredAt(actionId, packageName, since = now - windowMs)?.also { lastDelivered[key] = it }
        // A delivery recorded before the last edit no longer counts (D5 - window resets on edit).
        val effectiveLast = last?.takeIf { it >= resetAt }

        val deliver = effectiveLast == null || (now - effectiveLast) >= windowMs
        if (deliver) lastDelivered[key] = now
        deliver
    }

    private suspend fun dbLastDeliveredAt(actionId: String, packageName: String, since: Long): Long? {
        val resolvedPackage = packageName.ifBlank { UNKNOWN_PACKAGE }
        return ruleExecutionRepository.lastThrottleDeliveryAt(actionId, resolvedPackage, since)
            .fold(
                onSuccess = { it },
                onFailure = { error ->
                    // Fail-open: a stray duplicate alert right after a cold start/DB error beats
                    // silently suppressing a legitimately-due delivery.
                    Timber.w(error, "Throttle lookback failed for action $actionId, package $packageName; failing open")
                    null
                },
            )
    }

    /**
     * Evict keys whose opening delivery is older than a generous multiple of the current
     * window, mirroring [dev.gaferneira.notificapp.core.notification.NotificationDeduplicator.cleanupOldEntries].
     */
    private fun cleanupOldEntries(now: Long, windowMs: Long) {
        val staleThreshold = windowMs * STALE_WINDOW_MULTIPLIER
        val iterator = lastDelivered.iterator()
        while (iterator.hasNext()) {
            val (_, timestamp) = iterator.next()
            if ((now - timestamp) > staleThreshold) {
                iterator.remove()
            }
        }
    }

    private fun scopeKey(actionId: String, packageName: String): String = "$actionId|${packageName.ifBlank { UNKNOWN_PACKAGE }}"

    companion object {
        /** Fallback scope key when a notification's source package can't be resolved. */
        const val UNKNOWN_PACKAGE = "unknown_package"

        private const val STALE_WINDOW_MULTIPLIER = 10L
    }
}
