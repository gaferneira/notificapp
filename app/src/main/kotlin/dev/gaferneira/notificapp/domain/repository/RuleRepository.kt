package dev.gaferneira.notificapp.domain.repository

import dev.gaferneira.notificapp.domain.model.Rule
import kotlinx.coroutines.flow.Flow

/**
 * Repository for managing extraction rules.
 */
interface RuleRepository {

    /**
     * Observe all rules as a Flow.
     */
    fun observeAllRules(): Flow<List<Rule>>

    /**
     * Get all rules.
     */
    suspend fun getAllRules(): Result<List<Rule>>

    /**
     * Get a specific rule by ID.
     */
    suspend fun getRule(id: String): Result<Rule?>

    /**
     * Get rules that apply to a specific app.
     */
    suspend fun getRulesForApp(packageName: String): Result<List<Rule>>

    /**
     * Save a new rule.
     */
    suspend fun saveRule(rule: Rule): Result<Unit>

    /**
     * Update an existing rule.
     */
    suspend fun updateRule(rule: Rule): Result<Unit>

    /**
     * Delete a rule.
     */
    suspend fun deleteRule(id: String): Result<Unit>

    /**
     * Toggle a rule's active state.
     */
    suspend fun toggleRuleActive(id: String): Result<Unit>

    /**
     * Get count of rules.
     */
    suspend fun getRuleCount(): Result<Int>

    /**
     * Get count of active rules.
     */
    suspend fun getActiveRuleCount(): Result<Int>
}
