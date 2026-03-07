package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.AppInfo
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleCondition
import dev.gaferneira.notificapp.domain.model.RuleField
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
        actions = actions,
        isActive = true,
        targetApps = targetApps,
        fields = fields,
    )

    companion object {
        /**
         * Creates a [RuleUiModel] from a domain [Rule] for editing in the UI.
         */
        fun fromDomain(rule: Rule): RuleUiModel = RuleUiModel(
            id = rule.id,
            name = rule.name,
            description = rule.description ?: "",
            targetApps = rule.targetApps ?: emptyList(),
            triggers = rule.conditions,
            actions = rule.actions,
            fields = rule.fields,
        )
    }
}
