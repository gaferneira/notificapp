package dev.gaferneira.notificapp.core.data.local.converter

import androidx.room.TypeConverter
import kotlinx.serialization.json.Json

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
        // Use different discriminator to avoid conflict with ExtractionMethod's 'type' property
        classDiscriminator = "classType"
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
}
