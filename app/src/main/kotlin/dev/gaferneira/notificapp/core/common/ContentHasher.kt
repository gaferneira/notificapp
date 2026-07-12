package dev.gaferneira.notificapp.core.common

import java.security.MessageDigest

/**
 * Deterministic MD5 content hash for notification deduplication. Shared between
 * `core/notification` (in-memory + DB duplicate checks) and `core/data` (persisted
 * `content_hash` column) so both sides compute the exact same value.
 */
object ContentHasher {
    private val HEX_CHARS = "0123456789abcdef".toCharArray()
    private const val TITLE_MAX_LENGTH = 50
    private const val CONTENT_MAX_LENGTH = 100

    fun hash(packageName: String, title: String?, content: String?): String {
        val normalizedTitle = title?.lowercase()?.trim()?.take(TITLE_MAX_LENGTH) ?: ""
        val normalizedContent = content?.lowercase()?.trim()?.take(CONTENT_MAX_LENGTH) ?: ""
        val input = "$packageName|$normalizedTitle|$normalizedContent"

        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        val out = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            out[i * 2] = HEX_CHARS[v ushr 4]
            out[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(out)
    }
}
