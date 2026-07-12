package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.repository.NotificationRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deduplication utility for notifications.
 *
 * Prevents storing duplicate notifications by checking if a similar notification
 * was recently captured from the same app.
 */
@Singleton
class NotificationDeduplicator @Inject constructor(private val notificationRepository: NotificationRepository) {
    private val recentHashes = mutableMapOf<String, Long>()

    // recentHashes is check-then-act (read + conditional insert); a ConcurrentHashMap alone
    // would keep each individual access thread-safe but not the compound operation, so bursts
    // of notifications arriving concurrently could still race past the duplicate check.
    private val mutex = Mutex()

    /**
     * Check if a notification is a duplicate of a recently stored one.
     *
     * @param notification The notification to check
     * @return true if this is a duplicate, false otherwise
     */
    suspend fun isDuplicate(notification: Notification): Boolean = mutex.withLock {
        val contentHash = generateContentHash(notification)
        val currentTime = System.currentTimeMillis()

        // Clean up old entries
        cleanupOldEntries(currentTime)

        // Check if we've seen this hash recently
        val lastSeen = recentHashes[contentHash]
        if (lastSeen != null && (currentTime - lastSeen) < DUPLICATE_WINDOW_MS) {
            Timber.d("Duplicate detected for ${notification.packageName}: $contentHash")
            return@withLock true
        }

        // Also check database for recent duplicates with same content hash
        val recentNotifications = notificationRepository
            .getRecentNotifications(notification.packageName, DB_LOOKBACK_MS)
            .getOrNull() ?: emptyList()

        val isDuplicateInDb = recentNotifications.any { existing ->
            generateContentHash(existing) == contentHash
        }

        if (isDuplicateInDb) {
            Timber.d("Duplicate found in database for ${notification.packageName}: $contentHash")
            return@withLock true
        }

        // Record this hash
        recentHashes[contentHash] = currentTime
        false
    }

    /**
     * Generate a hash of notification content for deduplication.
     * Includes app package, title (normalized), and content (normalized).
     */
    private fun generateContentHash(notification: Notification): String {
        val normalizedTitle = notification.title?.lowercase()?.trim()?.take(50) ?: ""
        val normalizedContent = notification.content?.lowercase()?.trim()?.take(100) ?: ""

        val content = "${notification.packageName}|$normalizedTitle|$normalizedContent"
        return hashString(content)
    }

    /**
     * Generate MD5 hash of a string.
     */
    private fun hashString(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX_CHARS[v ushr 4]
            out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(out)
    }

    /**
     * Remove old entries from the in-memory cache.
     */
    private fun cleanupOldEntries(currentTime: Long) {
        val iterator = recentHashes.iterator()
        while (iterator.hasNext()) {
            val (_, timestamp) = iterator.next()
            if ((currentTime - timestamp) > DUPLICATE_WINDOW_MS) {
                iterator.remove()
            }
        }
    }

    companion object {
        // Consider notifications duplicates if they appear within 30 seconds
        private const val DUPLICATE_WINDOW_MS = 30_000L

        // Look back 5 minutes in the database for duplicates
        private const val DB_LOOKBACK_MS = 5 * 60 * 1000L

        private val HEX_CHARS = "0123456789abcdef".toCharArray()
    }
}
