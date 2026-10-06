package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
import dev.gaferneira.notificapp.domain.model.FieldTotal
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for extracted field values.
 *
 * Provides CRUD operations and type-specific filtering queries.
 */
@Dao
internal interface ExtractedFieldValueDao {

    /**
     * Get all values for a specific rule execution.
     */
    @Query("SELECT * FROM extracted_field_values WHERE rule_execution_id = :executionId")
    suspend fun getValuesForExecution(executionId: String): List<ExtractedFieldValueEntity>

    /**
     * Get all values for a specific rule execution as a Flow.
     */
    @Query("SELECT * FROM extracted_field_values WHERE rule_execution_id = :executionId")
    fun observeValuesForExecution(executionId: String): Flow<List<ExtractedFieldValueEntity>>

    /**
     * Get all values for a specific field definition across all executions.
     */
    @Query("SELECT * FROM extracted_field_values WHERE rule_field_id = :ruleFieldId")
    suspend fun getValuesForField(ruleFieldId: String): List<ExtractedFieldValueEntity>

    /**
     * Get all values for a specific field definition as a Flow.
     */
    @Query("SELECT * FROM extracted_field_values WHERE rule_field_id = :ruleFieldId")
    fun observeValuesForField(ruleFieldId: String): Flow<List<ExtractedFieldValueEntity>>

    /**
     * Get numeric values in a range for a specific field.
     */
    @Query(
        """
        SELECT * FROM extracted_field_values 
        WHERE rule_field_id = :ruleFieldId 
        AND value_number BETWEEN :minValue AND :maxValue
        """,
    )
    suspend fun getNumericValuesInRange(
        ruleFieldId: String,
        minValue: Double,
        maxValue: Double,
    ): List<ExtractedFieldValueEntity>

    /**
     * Get date values in a range for a specific field.
     */
    @Query(
        """
        SELECT * FROM extracted_field_values 
        WHERE rule_field_id = :ruleFieldId 
        AND value_date BETWEEN :startDate AND :endDate
        """,
    )
    suspend fun getDateValuesInRange(
        ruleFieldId: String,
        startDate: Long,
        endDate: Long,
    ): List<ExtractedFieldValueEntity>

    /**
     * Get text values matching a pattern for a specific field.
     */
    @Query(
        """
        SELECT * FROM extracted_field_values 
        WHERE rule_field_id = :ruleFieldId 
        AND value_text LIKE '%' || :pattern || '%'
        """,
    )
    suspend fun getTextValuesMatching(ruleFieldId: String, pattern: String): List<ExtractedFieldValueEntity>

    /**
     * Insert a single field value.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(value: ExtractedFieldValueEntity)

    /**
     * Insert multiple field values.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(values: List<ExtractedFieldValueEntity>)

    /**
     * Delete all values for a specific execution.
     */
    @Query("DELETE FROM extracted_field_values WHERE rule_execution_id = :executionId")
    suspend fun deleteValuesForExecution(executionId: String)

    /**
     * Delete all values for a specific field.
     */
    @Query("DELETE FROM extracted_field_values WHERE rule_execution_id = :executionId AND rule_field_id IN (:fieldIds)")
    suspend fun deleteValuesForExecutionFields(executionId: String, fieldIds: List<String>)

    @Query("DELETE FROM extracted_field_values WHERE rule_field_id = :ruleFieldId")
    suspend fun deleteValuesForField(ruleFieldId: String)

    /**
     * Delete a specific value by ID.
     */
    @Query("DELETE FROM extracted_field_values WHERE id = :id")
    suspend fun delete(id: String)

    /**
     * Get count of values for an execution.
     */
    @Query("SELECT COUNT(*) FROM extracted_field_values WHERE rule_execution_id = :executionId")
    suspend fun getCountForExecution(executionId: String): Int

    /**
     * Get count of all extracted field values.
     */
    @Query("SELECT COUNT(*) FROM extracted_field_values")
    suspend fun getCount(): Int

    /** Count of extracted values from non-dry-run executions since [since] (epoch millis). */
    @Query(
        """
        SELECT COUNT(*) FROM extracted_field_values efv
        JOIN rule_executions re ON efv.rule_execution_id = re.id
        WHERE re.was_dry_run = 0 AND re.created_at >= :since
        """,
    )
    fun observeCountSince(since: Long): Flow<Int>

    /**
     * Sum and count of `value_number` per NUMBER/CURRENCY field over non-dry-run executions since
     * [since]; most-populated fields first. Values that failed numeric parsing are ignored.
     *
     * History survives rule/field deletion, so rule and field are `LEFT JOIN`ed: a deleted rule or
     * removed field yields a null `ruleId`/`fieldId` and falls back to the snapshot names, and the
     * type filter uses the snapshot `efv.field_type` so orphaned values still count.
     */
    @Query(
        """
        SELECT re.rule_id AS ruleId, COALESCE(r.name, re.rule_name) AS ruleName,
               efv.rule_field_id AS fieldId, COALESCE(rf.name, efv.field_name) AS fieldName,
               efv.field_type AS fieldType, SUM(efv.value_number) AS sum, COUNT(efv.value_number) AS count
        FROM extracted_field_values efv
        JOIN rule_executions re ON efv.rule_execution_id = re.id
        LEFT JOIN rule_fields rf ON efv.rule_field_id = rf.id
        LEFT JOIN rules r ON re.rule_id = r.id
        WHERE re.was_dry_run = 0 AND re.created_at >= :since
        AND efv.field_type IN ('NUMBER', 'CURRENCY') AND efv.value_number IS NOT NULL
        GROUP BY efv.rule_field_id, COALESCE(rf.name, efv.field_name), efv.field_type,
                 re.rule_id, COALESCE(r.name, re.rule_name)
        ORDER BY count DESC, ruleName ASC, fieldName ASC, efv.rule_field_id ASC
        """,
    )
    fun observeNumericTotalsSince(since: Long): Flow<List<FieldTotal>>
}
