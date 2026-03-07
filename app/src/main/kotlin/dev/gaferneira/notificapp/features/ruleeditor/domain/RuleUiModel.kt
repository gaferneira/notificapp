package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleTrigger

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
    /** Rule area */
    val area: String = "",
    /** Whether this is a global rule (applies to all apps) */
    val isGlobalRule: Boolean = true,
    /** Target app package names (empty = all apps) */
    val targetApps: List<String> = emptyList(),
    /** List of configured triggers */
    val triggers: List<RuleTrigger> = emptyList(),
    /** List of configured actions */
    val actions: List<RuleAction> = emptyList(),
    /** Fields to extract (only for SAVE_DATA action) */
    val extractionFields: List<RuleField> = emptyList(),
) {
    companion object {
        /**
         * Creates a [RuleUiModel] from a domain [Rule] for editing in the UI.
         */
        fun fromDomain(rule: Rule): RuleUiModel = RuleUiModel(
            id = rule.id,
            name = rule.name,
            description = rule.description ?: "",
            isGlobalRule = rule.targetApps == null,
            targetApps = rule.targetApps ?: emptyList(),
            triggers = rule.triggers,
            actions = rule.actions,
            extractionFields = rule.fields,
        )
    }
}
