package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.MatchingCondition
import dev.gaferneira.notificapp.domain.model.MatchingOperator
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleCondition
import java.util.UUID

/** Longest value a prefilled condition carries, so a long headline doesn't become an unmatchable rule. */
const val PREFILL_VALUE_MAX_LENGTH = 60

/**
 * The single content condition a rule created from [notification] starts with: the title when it has
 * one, otherwise the first non-blank line of the body, otherwise none. Always a "contains" match,
 * trimmed and capped at [PREFILL_VALUE_MAX_LENGTH] characters.
 */
fun prefillConditionFrom(notification: Notification, newId: () -> String = { UUID.randomUUID().toString() }): RuleCondition? {
    val title = notification.title?.trim().orEmpty()
    val bodyLine = notification.content?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }.orEmpty()
    val (field, value) = when {
        title.isNotEmpty() -> MatchingCondition.TITLE to title
        bodyLine.isNotEmpty() -> MatchingCondition.TEXT_CONTENT to bodyLine
        else -> return null
    }
    return RuleCondition.ContentMatchCondition(
        id = newId(),
        condition = field,
        operator = MatchingOperator.CONTAINS,
        value = value.take(PREFILL_VALUE_MAX_LENGTH).trim(),
    )
}
