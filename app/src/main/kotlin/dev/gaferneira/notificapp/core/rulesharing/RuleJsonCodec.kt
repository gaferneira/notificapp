package dev.gaferneira.notificapp.core.rulesharing

import dev.gaferneira.notificapp.domain.model.Rule
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Encodes/decodes a [Rule] to and from the versioned JSON wire format used for rule
 * export/import and the community rules gallery. Pure Kotlin, no I/O - reading the source
 * JSON (file, clipboard) and writing the imported [Rule] are the caller's responsibility.
 */
object RuleJsonCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /**
     * Serialize [rule] to its shareable JSON representation.
     */
    fun encode(rule: Rule): String = json.encodeToString(RuleExport(rule = rule))

    /**
     * Parse [source] into a [Rule], validating the envelope's schema version and that the rule
     * has a name. Returns [Result.failure] with a user-presentable message on any problem -
     * malformed JSON, an unsupported (newer) schema version, or a blank name.
     */
    fun decode(source: String): Result<Rule> = runCatching {
        val export = json.decodeFromString<RuleExport>(source)
        require(export.schemaVersion <= RULE_EXPORT_SCHEMA_VERSION) {
            "This rule was exported from a newer version of Notificapp and can't be imported here."
        }
        require(export.rule.name.isNotBlank()) {
            "This rule has no name."
        }
        export.rule
    }

    /**
     * Prepare an imported [Rule] for local persistence: fresh IDs for the rule and every nested
     * condition/field/action (so importing the same file twice doesn't collide with itself),
     * reset timestamps, and force dry-run mode on - the "imported rules start in dry-run mode"
     * import-safety rule. The user reviews matches before trusting an imported rule to act on
     * real notifications.
     */
    fun Rule.withFreshIdentityForImport(): Rule {
        val now = System.currentTimeMillis()
        return copy(
            id = UUID.randomUUID().toString(),
            isActive = true,
            isDryRun = true,
            conditions = conditions.map { it.copy(id = UUID.randomUUID().toString()) },
            fields = fields.map { it.copy(id = UUID.randomUUID().toString()) },
            actions = actions.map { it.copy(id = UUID.randomUUID().toString()) },
            createdAt = now,
            updatedAt = now,
        )
    }
}
