package dev.gaferneira.notificapp.core.data.local.converter

import androidx.room.TypeConverter
import dev.gaferneira.notificapp.domain.model.HttpMethod
import dev.gaferneira.notificapp.domain.model.RuleField
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * Room TypeConverters for complex rule-related types.
 *
 * Handles serialization of polymorphic sealed classes to JSON strings for database storage.
 *
 * Note: RuleField, RuleCondition, and RuleAction are no longer stored as JSON lists - they have their own tables.
 */
class RuleTypeConverters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ========== String Map (for extracted data) ==========
    @TypeConverter
    fun fromStringMap(map: Map<String, String>): String = json.encodeToString(map)

    @TypeConverter
    fun toStringMap(jsonString: String): Map<String, String> = json.decodeFromString(jsonString)

    // ========== String List (for targetApps and other lists) ==========
    @TypeConverter
    fun fromStringList(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun toStringList(jsonString: String): List<String> = json.decodeFromString(jsonString)

    // ========== RuleField.FieldType (for the DataBrowserRow raw-query projection) ==========
    // Column storage format matches RuleFieldMapper, which persists `fieldType.name` (not the
    // kotlinx-serialization @SerialName).
    @TypeConverter
    fun fromFieldType(fieldType: RuleField.FieldType): String = fieldType.name

    @TypeConverter
    fun toFieldType(value: String): RuleField.FieldType = RuleField.FieldType.valueOf(value)

    // ========== LocalDate (for the DataBrowserRow raw-query projection of value_epoch_day) ==========
    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    // ========== HttpMethod (for Webhook HTTP verb) ==========
    @TypeConverter
    fun fromHttpMethod(method: HttpMethod): String = method.name

    @TypeConverter
    fun toHttpMethod(value: String): HttpMethod = runCatching { HttpMethod.valueOf(value) }.getOrDefault(HttpMethod.POST)
}
