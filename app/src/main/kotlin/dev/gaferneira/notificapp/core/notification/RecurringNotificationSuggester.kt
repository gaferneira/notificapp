package dev.gaferneira.notificapp.core.notification

import dev.gaferneira.notificapp.core.extraction.RuleMatcher
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RecurringSuggestion
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleCoverage
import dev.gaferneira.notificapp.domain.model.SuggestionDismissalKey
import dev.gaferneira.notificapp.domain.model.appliesToPackage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Detects notifications repeating for the same app with a similar title, and ranks them as
 * "you could automate this" candidates. Pure - no DB access, no coroutines; the caller supplies
 * already-queried, pre-windowed data. Mirrors [dev.gaferneira.notificapp.core.extraction.RuleMatcher]
 * and [dev.gaferneira.notificapp.core.extraction.FieldExtractor]'s purity discipline.
 */
class RecurringNotificationSuggester @Inject constructor() {

    /**
     * @param notifications Pre-windowed and capped by the caller (e.g. the last 14 days, 500 rows).
     * @param activeRules Every currently-active rule, for coverage classification.
     * @param dismissals Keys the user has already dismissed - excluded from candidacy entirely.
     * @param zoneId The timezone to evaluate calendar-day boundaries and rule time-conditions in.
     * @param config Threshold overrides; defaults to [Config]'s documented values.
     */
    fun suggest(
        notifications: List<Notification>,
        activeRules: List<Rule>,
        dismissals: Set<SuggestionDismissalKey>,
        zoneId: ZoneId,
        config: Config = Config(),
    ): List<RecurringSuggestion> {
        val groups: Map<Pair<String, String>, List<Notification>> = notifications
            .mapNotNull { notification ->
                val key = NotificationTitleNormalizer.normalize(notification.title) ?: return@mapNotNull null
                (notification.packageName to key) to notification
            }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })

        val suggestions = groups.mapNotNull { (groupKey, members) ->
            val (packageName, normalizedTitleKey) = groupKey
            val dismissalKey = SuggestionDismissalKey(packageName, normalizedTitleKey)
            val distinctDays = members.map { LocalDate.ofInstant(Instant.ofEpochMilli(it.timestamp), zoneId) }.toSet().size

            if (members.size < config.minOccurrences ||
                distinctDays < config.minDistinctDays ||
                dismissalKey in dismissals
            ) {
                return@mapNotNull null
            }

            val representative = members.maxBy { it.timestamp }
            val applicableRules = activeRules.filter { it.appliesToPackage(packageName) }
            val representativeNow = Instant.ofEpochMilli(representative.timestamp).atZone(zoneId).toLocalDateTime()
            val coverage = when {
                applicableRules.isEmpty() -> RuleCoverage.UNCOVERED
                applicableRules.any { rule ->
                    RuleMatcher.matches(representative, rule.conditions, representativeNow, rule.conditionLogic)
                } -> RuleCoverage.CONDITIONS_MATCH
                else -> RuleCoverage.APP_ONLY
            }

            if (coverage == RuleCoverage.CONDITIONS_MATCH) return@mapNotNull null

            RecurringSuggestion(
                packageName = packageName,
                appName = representative.appName,
                normalizedTitleKey = normalizedTitleKey,
                sampleTitle = representative.title.orEmpty(),
                sampleContent = representative.content,
                sampleNotificationId = representative.id,
                occurrences = members.size,
                distinctDays = distinctDays,
                coverage = coverage,
            )
        }

        return suggestions
            .sortedWith(
                compareBy<RecurringSuggestion> { it.coverage.ordinal }
                    .thenByDescending { it.occurrences },
            )
            .take(config.maxSuggestions)
    }

    /** Threshold defaults for candidacy and result-count capping. Windowing is the caller's responsibility. */
    data class Config(
        val minOccurrences: Int = DEFAULT_MIN_OCCURRENCES,
        val minDistinctDays: Int = DEFAULT_MIN_DISTINCT_DAYS,
        val maxSuggestions: Int = DEFAULT_MAX_SUGGESTIONS,
    )

    companion object {
        internal const val DEFAULT_MIN_OCCURRENCES = 4
        internal const val DEFAULT_MIN_DISTINCT_DAYS = 2
        internal const val DEFAULT_MAX_SUGGESTIONS = 3
    }
}
