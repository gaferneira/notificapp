package dev.gaferneira.notificapp.core.extraction

import dev.gaferneira.notificapp.domain.model.ExtractedDataUpdate
import dev.gaferneira.notificapp.domain.model.ExtractionPreview
import dev.gaferneira.notificapp.domain.model.FieldChange
import dev.gaferneira.notificapp.domain.model.FieldDiff
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleMatchPreview
import dev.gaferneira.notificapp.domain.model.UnmatchedExecution
import dev.gaferneira.notificapp.domain.model.saveDataFields
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject

/**
 * Read-only "what would the current rules do to this notification" evaluator.
 *
 * Pure: delegates matching/extraction to [RuleEngine] and compares the result with the stored
 * [RuleExecution]s. It never persists anything and never runs actions.
 */
class ExtractionPreviewer @Inject constructor(
    private val ruleEngine: RuleEngine,
) {

    /**
     * @param rules Rules to evaluate (callers pass only active ones that apply to the notification's app).
     * @param executions Stored executions of this notification.
     * @param ruleNames Names for stored executions' rules, used when such a rule stops matching.
     * @param zone Zone used to turn the capture timestamp into the wall-clock time conditions see.
     */
    fun preview(
        notification: Notification,
        rules: List<Rule>,
        executions: List<RuleExecution>,
        ruleNames: Map<String, String?> = emptyMap(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): ExtractionPreview {
        // Evaluate at capture time (like the rule backtest), so day/time conditions reflect
        // whether the rule would have matched when the notification arrived.
        val capturedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(notification.timestamp), zone)
        val matches = ruleEngine.evaluate(notification, rules, capturedAt)
        val executionByRule = executions.groupBy { it.ruleId }.mapValues { (_, list) -> list.maxBy { it.createdAt } }

        val matchPreviews = matches.map { match ->
            val rule = match.rule
            val execution = executionByRule[rule.id]
            val fields = rule.saveDataFields()
            val stored = execution?.extractedData.orEmpty()
            val diffs = fields.map { field ->
                val old = stored[field.id]
                val new = match.extractedData[field.id]
                FieldDiff(field.id, field.name, field.fieldType, old, new, compare(old, new))
            }
            val fieldIds = fields.map { it.id }.toSet()
            val changed = diffs.any { it.change != FieldChange.UNCHANGED }
            RuleMatchPreview(
                ruleId = rule.id,
                ruleName = rule.name,
                isDryRun = rule.isDryRun,
                executionId = execution?.id,
                fieldDiffs = diffs,
                actionTypes = rule.actions.filter { it.isEnabled }.map { it.type }.distinct(),
                update = if (execution != null && changed) {
                    ExtractedDataUpdate(
                        executionId = execution.id,
                        fields = fields,
                        extractedData = stored.filterKeys { it !in fieldIds } + match.extractedData,
                    )
                } else {
                    null
                },
            )
        }

        val matchedRuleIds = matches.map { it.rule.id }.toSet()
        val unmatched = executions.filter { it.ruleId !in matchedRuleIds }.map {
            UnmatchedExecution(executionId = it.id, ruleId = it.ruleId, ruleName = ruleNames[it.ruleId])
        }
        return ExtractionPreview(matches = matchPreviews, noLongerMatching = unmatched)
    }

    private fun compare(old: String?, new: String?): FieldChange = when {
        old == null && new == null -> FieldChange.UNCHANGED
        old == null -> FieldChange.NEW
        new == null -> FieldChange.REMOVED
        old == new -> FieldChange.UNCHANGED
        else -> FieldChange.CHANGED
    }
}
