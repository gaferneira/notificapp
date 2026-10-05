package dev.gaferneira.notificapp.domain.model

/**
 * Aggregate match statistics for a single rule, derived from its recorded executions.
 *
 * Executions are removed together with their source notification (retention / manual delete), so
 * these counts only cover notifications still on the device.
 *
 * @property totalMatches All recorded matches.
 * @property matchesLast7Days Matches in the 7 days before the stats were queried.
 * @property matchesLast30Days Matches in the 30 days before the stats were queried.
 * @property liveMatches Matches recorded while the rule was live (actions ran).
 * @property testModeMatches Matches recorded while the rule was in dry-run (test) mode.
 * @property lastTriggeredAt When the rule last matched (epoch millis), or null if it never has.
 */
data class RuleStats(
    val totalMatches: Int = 0,
    val matchesLast7Days: Int = 0,
    val matchesLast30Days: Int = 0,
    val liveMatches: Int = 0,
    val testModeMatches: Int = 0,
    val lastTriggeredAt: Long? = null,
)
