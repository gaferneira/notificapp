package dev.gaferneira.notificapp.domain.model

import androidx.compose.runtime.Immutable

/**
 * Domain model representing an extraction rule.
 *
 * Rules define patterns to extract structured data from notifications. Not `@Serializable`: the
 * wire format for import/export is defined by the DTOs in `core/rulesharing/dto/`, independent of
 * this model's property names.
 *
 * `@Immutable` is a promise to the Compose compiler that this type is stable across
 * recompositions (all properties are read-only and never mutate in place) so composables reading
 * a [Rule] can be skipped when it's unchanged, even though its `List` properties are themselves
 * unstable interfaces the compiler can't otherwise prove immutable (see ADR/audit UI-001).
 */
@Immutable
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
    /** Actions to take when rule matches */
    val actions: List<RuleAction> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Fields to extract from a notification, sourced from this rule's enabled `SAVE_DATA`
 * ("Extract data") action. Empty when there is no enabled `SAVE_DATA` action - the single place
 * consumers should read extraction fields from (see `action-execution` spec).
 */
fun Rule.saveDataFields(): List<RuleField> = actions.firstOrNull { it.type == ActionType.SAVE_DATA && it.isEnabled }?.fields.orEmpty()
