package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.saveDataFields
import java.util.UUID

/**
 * UI model representing a Rule being edited.
 * Groups all Rule-related attributes for cleaner state management.
 */
data class RuleUiModel(
    /** Rule ID - null for new rules */
    val id: String? = null,
    /** Rule name */
    val name: String = "",
    /** Rule description */
    val description: String = "",
    /** Rule category */
    val category: String = "",
    /**
     * When true, matches are logged but no actions execute. Defaults to true for new
     * rules (id == null) so a rule can be trialed before it's trusted to act on real
     * notifications - matches the default already used for imported rules.
     */
    val isDryRun: Boolean = true,
    /** Target app package names (empty = all apps) */
    val targetApps: List<AppInfo> = emptyList(),
    /** List of configured triggers */
    val triggers: List<RuleCondition> = emptyList(),
    /** List of configured actions */
    val actions: List<RuleAction> = emptyList(),
    /** Fields to extract (only for SAVE_DATA action) */
    val fields: List<RuleField> = emptyList(),
) {
    fun toEntity() = Rule(
        id = id ?: UUID.randomUUID().toString(),
        name = name.trim(),
        description = description.takeIf { it.isNotBlank() },
        category = category,
        conditions = triggers,
        actions = actionsWithFieldsAttached(),
        isActive = true,
        isDryRun = isDryRun,
        targetApps = targetApps,
    )

    /**
     * Attach the draft [fields] to the `SAVE_DATA` action, creating one if the draft has fields
     * but no such action yet. Every other action is passed through untouched.
     */
    private fun actionsWithFieldsAttached(): List<RuleAction> {
        val hasSaveDataAction = actions.any { it.type == ActionType.SAVE_DATA }
        return when {
            hasSaveDataAction -> actions.map { if (it.type == ActionType.SAVE_DATA) it.copy(fields = fields) else it }
            fields.isNotEmpty() -> actions + RuleAction.createSaveData(id = UUID.randomUUID().toString(), fields = fields)
            else -> actions
        }
    }

    companion object {
        /**
         * Creates a [RuleUiModel] from a domain [Rule] for editing in the UI.
         */
        fun fromDomain(rule: Rule): RuleUiModel = RuleUiModel(
            id = rule.id,
            name = rule.name,
            description = rule.description ?: "",
            category = rule.category.orEmpty(),
            isDryRun = rule.isDryRun,
            targetApps = rule.targetApps ?: emptyList(),
            triggers = rule.conditions,
            actions = rule.actions,
            fields = rule.saveDataFields(),
        )
    }
}
