package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleCondition
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/**
 * Pure Kotlin object for checking if a notification matches rule conditions.
 *
 * No Android dependencies - fully unit testable. "Now" arrives as a parameter (never read from a
 * clock internally) so day-of-week/time-range evaluation stays a pure function of its inputs -
 * `core/extraction` cannot depend on `core/notification`'s `CurrentTimeProvider` (reverse
 * dependency), per this change's design D2/D6.
 */
object RuleMatcher {

    /**
     * Check if a notification matches the given conditions according to [combinator].
     *
     * @param notification The notification to check
     * @param conditions List of conditions to evaluate
     * @param now The evaluation instant, for day-of-week/time-range conditions
     * @param combinator How to combine the conditions: ALL (AND) or ANY (OR)
     * @return true if the conditions match according to the combinator, false otherwise
     */
    fun matches(
        notification: Notification,
        conditions: List<RuleCondition>,
        now: LocalDateTime,
        combinator: ConditionCombinator = ConditionCombinator.ALL,
    ): Boolean {
        if (conditions.isEmpty()) return true
        return when (combinator) {
            ConditionCombinator.ALL -> conditions.all { condition ->
                matchesCondition(notification, condition, now)
            }
            ConditionCombinator.ANY -> conditions.any { condition ->
                matchesCondition(notification, condition, now)
            }
        }
    }

    /**
     * Check if a single condition matches the notification. Total and fail-closed: every sealed
     * member resolves to a boolean, never throws.
     */

    private fun matchesCondition(notification: Notification, condition: RuleCondition, now: LocalDateTime): Boolean = when (condition) {
        is RuleCondition.ContentMatchCondition -> matchesContent(notification, condition)
        is RuleCondition.DayOfWeekCondition -> now.dayOfWeek in condition.days
        is RuleCondition.TimeRangeCondition -> matchesTimeRange(condition, now.toLocalTime().truncatedTo(ChronoUnit.MINUTES))
    }

    private fun matchesContent(notification: Notification, condition: RuleCondition.ContentMatchCondition): Boolean {
        val value = getValueForCondition(notification, condition.condition) ?: return false

        return when (condition.operator) {
            MatchingOperator.CONTAINS -> value.contains(condition.value)
            MatchingOperator.STARTS_WITH -> value.startsWith(condition.value)
            MatchingOperator.ENDS_WITH -> value.endsWith(condition.value)
            MatchingOperator.EQUALS -> value == condition.value
            MatchingOperator.REGEX_MATCH -> matchesRegex(value, condition.value)
            MatchingOperator.NOT_CONTAINS -> !value.contains(condition.value)
        }
    }

    /**
     * Evaluate a [RuleCondition.TimeRangeCondition] against a minute-truncated [t]. Closed-inclusive:
     * `start <= end` matches `[start, end]`; `start > end` wraps midnight, matching `t >= start` OR
     * `t <= end`. A degenerate `start == end` range therefore matches only that exact minute.
     */
    private fun matchesTimeRange(condition: RuleCondition.TimeRangeCondition, t: LocalTime): Boolean = if (condition.start <= condition.end) {
        t >= condition.start && t <= condition.end
    } else {
        t >= condition.start || t <= condition.end
    }

    /**
     * Extract the relevant value from the notification based on the condition type.
     */
    private fun getValueForCondition(
        notification: Notification,
        condition: MatchingCondition,
    ): String? = when (condition) {
        MatchingCondition.TEXT_CONTENT -> notification.content
        MatchingCondition.TITLE -> notification.title
        MatchingCondition.APP_NAME -> notification.appName
        MatchingCondition.PACKAGE_NAME -> notification.packageName
        MatchingCondition.RAW_CONTENT -> notification.rawContent
    }

    /**
     * Check if a value matches a regex pattern.
     */
    private fun matchesRegex(value: String, pattern: String): Boolean = try {
        RegexCache.compiled(pattern).containsMatchIn(value)
    } catch (e: Exception) {
        false
    }
}
