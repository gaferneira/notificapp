package dev.gaferneira.notificapp.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4

/**
 * FTS4 shadow table backing full-text search over [ExtractedFieldValueEntity.valueText], mirroring
 * [NotificationFtsEntity]. Room keeps this synchronized with `extracted_field_values` via generated
 * triggers; queries join on `rowid` and filter with `MATCH` instead of a non-sargable
 * `LIKE '%term%'` scan.
 */
@Fts4(contentEntity = ExtractedFieldValueEntity::class)
@Entity(tableName = "extracted_field_values_fts")
internal data class ExtractedFieldValueFtsEntity(
    @ColumnInfo(name = "value_text")
    val valueText: String?,
)
