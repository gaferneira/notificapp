package dev.gaferneira.notificapp.domain.model

import kotlinx.serialization.Serializable

/**
 * Domain model representing an extraction rule.
 *
 * Rules define patterns to extract structured data from notifications.
 */
@Serializable
data class Rule(
    val id: String,
    val name: String,
    val description: String?,
    /** Whether this rule is active */
    val isActive: Boolean = true,
    /** App scope: null means all apps, or list of specific package names */
    val targetApps: List<String>? = null,
    /** Triggers that determine when rule applies */
    val triggers: List<RuleTrigger> = emptyList(),
    /** Fields to extract from the notification */
    val fields: List<RuleField> = emptyList(),
    /** Actions to take when rule matches */
    val actions: List<RuleAction> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
