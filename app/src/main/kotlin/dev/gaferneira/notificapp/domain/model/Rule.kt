package dev.gaferneira.notificapp.domain.model

/**
 * Domain model representing an extraction rule.
 *
 * Rules define patterns to extract structured data from notifications. Not `@Serializable`: the
 * wire format for import/export is defined by the DTOs in `core/rulesharing/dto/`, independent of
 * this model's property names (see TD-9 in `docs/roadmap_tech_debt.md`).
 */
data class Rule(
    val id: String,
    val name: String,
    val description: String?,
    /** Category for grouping rules (e.g., "Finance", "Deliveries") */
    val category: String? = null,
    /** Whether this rule is active */
    val isActive: Boolean = true,
    /** When true, matches are logged but no actions execute - a safe way to trial a rule */
    val isDryRun: Boolean = false,
    /** App scope: null means all apps, or list of specific package names */
    val targetApps: List<AppInfo>? = null,
    /** Triggers that determine when rule applies */
    val conditions: List<RuleCondition> = emptyList(),
    /** Fields to extract from the notification */
    val fields: List<RuleField> = emptyList(),
    /** Actions to take when rule matches */
    val actions: List<RuleAction> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
