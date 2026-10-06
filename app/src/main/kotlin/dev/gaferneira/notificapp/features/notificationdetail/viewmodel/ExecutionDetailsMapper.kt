package dev.gaferneira.notificapp.features.notificationdetail.viewmodel

import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleExecution
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ExecutionWithDetails
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.ExtractedFieldDisplay
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.TriggeredActionDisplay

/**
 * Resolves names and types for a stored execution from the rule as it is now.
 *
 * Executions only store action ids, so a removed action is surfaced as a typed "unknown" (`null`
 * type) instead of leaking ids or hardcoded text into the UI. Extracted values carry a snapshot of
 * their field's name and type, so a removed field still shows its original name.
 *
 * @param rule The rule that produced [execution], or `null` if it was deleted (the name then comes
 *   from the execution's own snapshot).
 * @param isRedactionSource Whether this execution's rule scrubbed the notification content.
 */
internal fun buildExecutionDetails(
    execution: RuleExecution,
    rule: Rule?,
    isRedactionSource: Boolean,
): ExecutionWithDetails {
    // Every field of every Extract-data action, enabled or not: a stored value stays labelled even
    // if the action was later disabled.
    val fieldsById: Map<String, RuleField> = rule?.actions.orEmpty().flatMap { it.fields }.associateBy { it.id }
    val actionsById = rule?.actions.orEmpty().associateBy { it.id }

    return ExecutionWithDetails(
        execution = execution,
        // Only an existing rule can be opened; null also covers a deleted (SET NULL) rule.
        ruleId = execution.ruleId.takeIf { rule != null },
        ruleName = rule?.name ?: execution.ruleName,
        extractedFields = execution.extractedValues.map { stored ->
            val field = stored.ruleFieldId?.let { fieldsById[it] }
            ExtractedFieldDisplay(
                fieldId = stored.ruleFieldId,
                fieldName = field?.name ?: stored.fieldName,
                fieldType = field?.fieldType ?: stored.fieldType,
                value = stored.valueText.orEmpty(),
            )
        },
        triggeredActions = execution.triggeredActions.map { actionId ->
            TriggeredActionDisplay(
                actionId = actionId,
                type = actionsById[actionId]?.type,
                outcome = execution.actionOutcomes[actionId],
            )
        },
        wasRedactionSource = isRedactionSource,
    )
}
