package dev.gaferneira.notificapp.core.extraction

import java.util.Collections

/**
 * Bounded, thread-safe cache of compiled [Regex] instances, keyed by pattern string.
 *
 * Shared by [FieldExtractor] and [RuleMatcher] so user-supplied patterns (rule regex conditions,
 * field regex extraction methods) are compiled once and reused across every notification/backtest
 * candidate, instead of paying `Pattern.compile` on every extraction/match call.
 */
internal object RegexCache {

    private const val CACHE_MAX = 64

    private val cache: MutableMap<String, Regex> =
        Collections.synchronizedMap(
            object : LinkedHashMap<String, Regex>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Regex>) = size > CACHE_MAX
            },
        )

    fun compiled(pattern: String): Regex = cache.getOrPut(pattern) { pattern.toRegex() }
}
