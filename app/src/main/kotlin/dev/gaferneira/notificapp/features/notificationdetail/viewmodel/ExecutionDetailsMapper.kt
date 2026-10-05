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
 * Executions only store field/action ids, so anything the rule no longer has (deleted rule,
 * removed field, removed action) is surfaced as a typed "unknown" (`null` name/type) instead of
 * leaking ids or hardcoded text into the UI.
 *
 * @param rule The rule that produced [execution], or `null` if it was deleted.
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
        ruleId = execution.ruleId,
        ruleName = rule?.name,
        extractedFields = execution.extractedData.map { (fieldId, value) ->
            val field = fieldsById[fieldId]
            ExtractedFieldDisplay(
                fieldId = fieldId,
                fieldName = field?.name,
                fieldType = field?.fieldType ?: RuleField.FieldType.STRING,
                value = value,
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
