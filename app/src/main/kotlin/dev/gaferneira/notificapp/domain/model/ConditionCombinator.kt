package dev.gaferneira.notificapp.domain.model

/**
 * How the conditions of a [Rule] are combined when matching a notification.
 */
enum class ConditionCombinator {
    /** Every condition must match (AND semantics). */
    ALL,

    /** At least one condition must match (OR semantics). */
    ANY,
    ;

    companion object {
        /** Maps a stored/wire string to its combinator, defaulting to [ALL] for unknown or legacy values. */
        fun fromStorageValue(value: String): ConditionCombinator = runCatching { valueOf(value) }.getOrDefault(ALL)
    }
}
