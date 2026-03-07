package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.gaferneira.notificapp.core.data.local.entity.RuleEntity
import dev.gaferneira.notificapp.core.data.local.entity.RuleTargetAppEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for extraction rules.
 *
 * Provides CRUD operations and queries for rule management.
 */
@Dao
interface RuleDao {

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
     * Update only the rule metadata without touching target apps.
     * Use saveRuleWithApps if you need to update apps as well.
     */
    @Transaction
    suspend fun updateRule(rule: RuleEntity) {
        insert(rule)
    }
}
