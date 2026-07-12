package dev.gaferneira.notificapp.core.data.local.dao

/**
 * Turns free-text user input into a safe SQLite FTS4 MATCH expression (DATA-04). Splits on
 * whitespace, drops FTS4 syntax characters (`"`, `*`, `-`, `(`, `)`) from each token so user input
 * can't be interpreted as query operators, then wraps every token as a quoted prefix match
 * (`"term"*`) joined with implicit AND — the closest FTS4 equivalent of the old `LIKE '%term%'`
 * "contains all these words" behavior.
 */
internal object FtsQuerySanitizer {

    private val DISALLOWED_CHARS = charArrayOf('"', '*', '-', '(', ')')

    fun toMatchExpression(rawQuery: String): String = rawQuery
        .trim()
        .split(Regex("\\s+"))
        .map { token -> token.filterNot { it in DISALLOWED_CHARS } }
        .filter { it.isNotEmpty() }
        .joinToString(" ") { "\"$it\"*" }
}
