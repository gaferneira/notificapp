package dev.gaferneira.notificapp.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.gaferneira.notificapp.core.data.local.entity.ExtractedFieldValueEntity
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
}
