package dev.gaferneira.notificapp.core.data.local.converter

import androidx.room.TypeConverter
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleTrigger
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverters for complex rule-related types.
 *
 * Handles serialization of polymorphic sealed classes (ExtractionMethod)
 * to JSON strings for database storage.
 */
class RuleTypeConverters {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        // Use different discriminator to avoid conflict with ExtractionMethod's 'type' property
        classDiscriminator = "classType"
    }

    // ========== RuleField List ==========
    @TypeConverter
    fun fromRuleFieldList(fields: List<RuleField>): String = json.encodeToString(fields)

    @TypeConverter
    fun toRuleFieldList(jsonString: String): List<RuleField> = json.decodeFromString(jsonString)

    // ========== RuleTrigger List ==========
    @TypeConverter
    fun fromRuleTriggerList(triggers: List<RuleTrigger>): String = json.encodeToString(triggers)

    @TypeConverter
    fun toRuleTriggerList(jsonString: String): List<RuleTrigger> = json.decodeFromString(jsonString)

    // ========== RuleAction List ==========
    @TypeConverter
    fun fromRuleActionList(actions: List<RuleAction>): String = json.encodeToString(actions)

    @TypeConverter
    fun toRuleActionList(jsonString: String): List<RuleAction> = json.decodeFromString(jsonString)

    // ========== String List (for targetApps and other lists) ==========
    @TypeConverter
    fun fromStringList(list: List<String>): String = json.encodeToString(list)

    @TypeConverter
    fun toStringList(jsonString: String): List<String> = json.decodeFromString(jsonString)
}
