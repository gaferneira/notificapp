package dev.gaferneira.notificapp.core.notification.action

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks notifications expected to reappear from a scheduled-snooze release, so that reappearance
 * isn't immediately re-batched to the next checkpoint by [SnoozeActionExecutor]. Once `snooze()`
 * is called for a key in scheduled mode, the next post event observed for that same key is by
 * construction the release - nothing else should legitimately post under an actively-snoozed key
 * - so a plain presence check is enough; no timestamp/epsilon matching is needed.
 *
 * Mirrors [dev.gaferneira.notificapp.core.notification.NotificationDeduplicator]'s in-memory,
 * mutex-guarded pattern rather than adding persistence for what is inherently transient state. If
 * the listener service process dies between [markPending] and the repost, that one checkpoint's
 * release is treated as a fresh match and re-batched to the next checkpoint instead of shown -
 * self-correcting on the next cycle, same accepted risk class as the deduplicator's cache.
 */
@Singleton
class SnoozeReleaseTracker @Inject constructor() {
    private val pending = mutableSetOf<String>()
    private val mutex = Mutex()

    /** Mark [sbnKey] as expected to reappear from a scheduled release. */
    suspend fun markPending(sbnKey: String) = mutex.withLock { pending.add(sbnKey) }

    /** If [sbnKey] was marked pending, clear it and return true. Otherwise return false. */
    suspend fun consumeIfPending(sbnKey: String): Boolean = mutex.withLock { pending.remove(sbnKey) }
}
