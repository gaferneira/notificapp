package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.gaferneira.notificapp.core.data.local.entity.RuleActionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleConditionEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleFieldEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleTargetAppEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for extraction rules.
 *
 * Provides CRUD operations and queries for rule management.
 */
@Dao
internal interface RuleDao {

    /**
     * Observe all rules as a Flow, ordered by most recently updated first.
     */
    @Query("SELECT * FROM rules ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<RuleEntity>>

    /**
     * Get all rules.
     */
    @Query("SELECT * FROM rules ORDER BY updated_at DESC")
    suspend fun getAll(): List<RuleEntity>

    /**
     * Get a specific rule by ID.
     */
    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getById(id: String): RuleEntity?

    /**
     * Get rules that apply to a specific app package.
     * Returns global rules (is_global = 1) plus rules specifically targeting this app.
     */
    @Query(
        """
        SELECT r.* FROM rules r
        LEFT JOIN rule_target_apps ta ON r.id = ta.rule_id
        WHERE r.is_global = 1 OR ta.package_name = :packageName
        ORDER BY r.updated_at DESC
        """,
    )
    suspend fun getRulesForApp(packageName: String): List<RuleEntity>

    /**
     * Get only active rules for a specific app.
     */
    @Query(
        """
        SELECT r.* FROM rules r
        LEFT JOIN rule_target_apps ta ON r.id = ta.rule_id
        WHERE r.is_active = 1 AND (r.is_global = 1 OR ta.package_name = :packageName)
        ORDER BY r.updated_at DESC
        """,
    )
    suspend fun getActiveRulesForApp(packageName: String): List<RuleEntity>

    /**
     * Insert or update a rule.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: RuleEntity)

    /**
     * Insert or update target app associations.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTargetApps(apps: List<RuleTargetAppEntity>)

    /**
     * Delete all target app associations for a rule.
     */
    @Query("DELETE FROM rule_target_apps WHERE rule_id = :ruleId")
    suspend fun deleteTargetAppsForRule(ruleId: String)

    /**
     * Delete a rule by ID.
     */
    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Toggle rule active state.
     */
    @Query("UPDATE rules SET is_active = NOT is_active WHERE id = :id")
    suspend fun toggleActive(id: String)

    /**
     * Get count of all rules.
     */
    @Query("SELECT COUNT(*) FROM rules")
    suspend fun getCount(): Int

    /**
     * Get count of active rules.
     */
    @Query("SELECT COUNT(*) FROM rules WHERE is_active = 1")
    suspend fun getActiveCount(): Int

    /**
     * Get target apps for a specific rule.
     */
    @Query("SELECT package_name FROM rule_target_apps WHERE rule_id = :ruleId")
    suspend fun getTargetAppsForRule(ruleId: String): List<String>

    @Transaction
    suspend fun saveRuleWithApps(rule: RuleEntity, apps: List<String>?) {
        insert(rule)
        deleteTargetAppsForRule(rule.id)
        if (!apps.isNullOrEmpty()) {
            insertTargetApps(
                apps.map { packageName ->
                    RuleTargetAppEntity(rule.id, packageName)
                },
            )
        }
    }

    /**
     * Update only the rule metadata without touching target apps or fields.
     * Use saveRuleWithAppsAndFields if you need to update related data as well.
     */
    @Transaction
    suspend fun updateRule(rule: RuleEntity) {
        insert(rule)
    }

    // ========== Rule Condition Operations ==========

    /**
     * Get all conditions for a specific rule.
     */
    @Query("SELECT * FROM rule_conditions WHERE rule_id = :ruleId")
    suspend fun getConditionsForRule(ruleId: String): List<RuleConditionEntity>

    /**
     * Get all conditions for a specific rule as a Flow.
     */
    @Query("SELECT * FROM rule_conditions WHERE rule_id = :ruleId")
    fun observeConditionsForRule(ruleId: String): Flow<List<RuleConditionEntity>>

    /**
     * Insert or update conditions.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConditions(conditions: List<RuleConditionEntity>)

    /**
     * Delete all conditions for a rule.
     */
    @Query("DELETE FROM rule_conditions WHERE rule_id = :ruleId")
    suspend fun deleteConditionsForRule(ruleId: String)

    // ========== Rule Action Operations ==========

    /**
     * Get all actions for a specific rule.
     */
    @Query("SELECT * FROM rule_actions WHERE rule_id = :ruleId")
    suspend fun getActionsForRule(ruleId: String): List<RuleActionEntity>

    /**
     * Get all actions for a specific rule as a Flow.
     */
    @Query("SELECT * FROM rule_actions WHERE rule_id = :ruleId")
    fun observeActionsForRule(ruleId: String): Flow<List<RuleActionEntity>>

    /**
     * Get all actions of a given [type] (e.g. `CREATE_ALARM`) across every rule. Used for
     * config-level scans (e.g. checking whether a background image URI is still referenced by
     * another alarm action) where `rule_actions.config` is an opaque JSON-string column with no
     * indexed field to query against directly.
     */
    @Query("SELECT * FROM rule_actions WHERE type = :type")
    suspend fun getActionsByType(type: String): List<RuleActionEntity>

    /**
     * Insert or update actions.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActions(actions: List<RuleActionEntity>)

    /**
     * Delete all actions for a rule.
     */
    @Query("DELETE FROM rule_actions WHERE rule_id = :ruleId")
    suspend fun deleteActionsForRule(ruleId: String)

    // ========== Rule Field Operations ==========

    /**
     * Get all fields owned by a specific action (the rule's `SAVE_DATA` action, in practice).
     */
    @Query("SELECT * FROM rule_fields WHERE action_id = :actionId")
    suspend fun getFieldsForAction(actionId: String): List<RuleFieldEntity>

    /**
     * Insert or update fields.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFields(fields: List<RuleFieldEntity>)

    /**
     * Delete all fields owned by a specific action. Not required for the combined save/delete
     * operations below (their action deletes already cascade to `rule_fields`), but kept for
     * callers that need to clear a single action's fields without touching the rest of the rule.
     */
    @Query("DELETE FROM rule_fields WHERE action_id = :actionId")
    suspend fun deleteFieldsForAction(actionId: String)

    // ========== Combined Operations ==========

    @Transaction
    suspend fun saveRuleWithRelatedData(
        rule: RuleEntity,
        fields: List<RuleFieldEntity>,
        conditions: List<RuleConditionEntity>,
        actions: List<RuleActionEntity>,
        apps: List<String>?,
    ) {
        insert(rule)
        // Deleting actions cascades to their owned rule_fields rows (FK ON DELETE CASCADE), so
        // fields are re-inserted under the fresh action ids without a separate delete-by-rule step.
        deleteConditionsForRule(rule.id)
        deleteActionsForRule(rule.id)
        deleteTargetAppsForRule(rule.id)
        if (conditions.isNotEmpty()) {
            insertConditions(conditions)
        }
        if (actions.isNotEmpty()) {
            insertActions(actions)
        }
        if (fields.isNotEmpty()) {
            insertFields(fields)
        }
        if (!apps.isNullOrEmpty()) {
            insertTargetApps(
                apps.map { packageName ->
                    RuleTargetAppEntity(rule.id, packageName)
                },
            )
        }
    }

    /**
     * Delete a rule and all its related data (cascades to actions -> fields, conditions, and
     * target apps via FK constraints).
     */
    @Transaction
    suspend fun deleteRuleWithRelatedData(ruleId: String) {
        deleteConditionsForRule(ruleId)
        deleteActionsForRule(ruleId)
        deleteTargetAppsForRule(ruleId)
        delete(ruleId)
    }
}
