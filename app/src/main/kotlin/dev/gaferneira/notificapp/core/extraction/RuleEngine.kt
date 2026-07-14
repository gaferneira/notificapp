package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleMatch
import dev.gaferneira.notificapp.domain.model.saveDataFields
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Pure rule evaluation engine: matches a notification against a set of rules
 * and extracts fields from the matches. No I/O, no persistence, no coroutines —
 * loading rules and recording results is the caller's responsibility
 * (see `ProcessNotificationUseCase`).
 */
class RuleEngine @Inject constructor() {

    /**
     * Evaluate a notification against a list of rules.
     *
     * @param notification The notification to check
     * @param rules The rules to evaluate against
     * @param now The evaluation instant, forwarded to [RuleMatcher] for day-of-week/time-range conditions
     * @return One [RuleMatch] per rule whose conditions matched
     */
    fun evaluate(notification: Notification, rules: List<Rule>, now: LocalDateTime): List<RuleMatch> = rules.mapNotNull { rule -> evaluateRule(notification, rule, now) }

    /**
     * Evaluate a single rule against a notification.
     *
     * @param notification The notification to check
     * @param rule The rule to evaluate
     * @param now The evaluation instant
     * @return RuleMatch if the rule's conditions matched, null otherwise
     */
    private fun evaluateRule(
        notification: Notification,
        rule: Rule,
        now: LocalDateTime,
    ): RuleMatch? {
        if (!RuleMatcher.matches(notification, rule.conditions, now, rule.conditionLogic)) {
            Timber.d("Rule ${rule.id} did not match notification ${notification.id}")
            return null
        }

        Timber.d("Rule ${rule.id} matched notification ${notification.id}")

        val extractedData = extractFields(notification, rule)

        if (extractedData.isEmpty() && rule.saveDataFields().isNotEmpty()) {
            Timber.w("Rule ${rule.id} matched but no fields could be extracted")
        }

        return RuleMatch(rule = rule, extractedData = extractedData)
    }

    /**
     * Extract all fields from the notification using the rule's field definitions.
     */
    private fun extractFields(
        notification: Notification,
        rule: Rule,
    ): Map<String, String> {
        val extractedData = mutableMapOf<String, String>()
        val sourceText = notification.rawContent

        for (field in rule.saveDataFields()) {
            val result = FieldExtractor.extract(sourceText, field)

            if (result is ExtractionResult.Success) {
                extractedData[field.id] = result.value
                Timber.d("Extracted field ${field.name}: ${result.value}")
            } else if (field.isRequired) {
                // If a required field fails, we might want to mark this execution as partial
                Timber.w("Failed to extract required field ${field.name} for rule ${rule.id}")
            }
        }

        return extractedData
    }
}
