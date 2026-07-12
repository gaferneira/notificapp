package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleCondition

/**
 * Pure Kotlin object for checking if a notification matches rule conditions.
 *
 * No Android dependencies - fully unit testable.
 */
object RuleMatcher {

    /**
     * Check if a notification matches all the given conditions.
     *
     * @param notification The notification to check
     * @param conditions List of conditions that must all match (AND logic)
     * @return true if all conditions match, false otherwise
     */
    fun matches(notification: Notification, conditions: List<RuleCondition>): Boolean {
        if (conditions.isEmpty()) return true
        return conditions.all { condition ->
            matchesCondition(notification, condition)
        }
    }

    /**
     * Check if a single condition matches the notification.
     */
    private fun matchesCondition(notification: Notification, condition: RuleCondition): Boolean {
        val value = getValueForCondition(notification, condition.condition ?: return false)
            ?: return false

        return when (condition.operator) {
            MatchingOperator.CONTAINS -> value.contains(condition.value ?: "")
            MatchingOperator.STARTS_WITH -> value.startsWith(condition.value ?: "")
            MatchingOperator.ENDS_WITH -> value.endsWith(condition.value ?: "")
            MatchingOperator.EQUALS -> value == condition.value
            MatchingOperator.REGEX_MATCH -> matchesRegex(value, condition.value ?: "")
            MatchingOperator.NOT_CONTAINS -> !value.contains(condition.value ?: "")
            null -> false
        }
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
