package dev.gaferneira.notificapp.core.rulesharing

import dev.gaferneira.notificapp.core.rulesharing.dto.RULE_EXPORT_SCHEMA_VERSION
import dev.gaferneira.notificapp.core.rulesharing.dto.RuleExportDto
import dev.gaferneira.notificapp.domain.model.Rule
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Encodes/decodes a [Rule] to and from the versioned JSON wire format used for rule
 * export/import and the community rules gallery, via the DTO layer in `core/rulesharing/dto/`
 * (the canonical definition of the wire format - see `docs/rule-format.md`). Pure Kotlin, no I/O -
 * reading the source JSON (file, clipboard) and writing the imported [Rule] are the caller's
 * responsibility.
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
    fun encode(rule: Rule): String = json.encodeToString(rule.toDto())

    /**
     * Parse [source] into a [RuleImportResult], validating the envelope's schema version and that
     * the rule has a name. Returns [Result.failure] with a user-presentable message on any
     * problem - malformed JSON, an unsupported (newer) schema version, a blank name, or an
     * unrecognized condition/extraction method. Actions this app version doesn't recognize are
     * dropped rather than failing the import - see [RuleImportResult.skippedActions].
     */
    fun decode(source: String): Result<RuleImportResult> = runCatching {
        val export = try {
            json.decodeFromString<RuleExportDto>(source)
        } catch (e: SerializationException) {
            throw IllegalArgumentException("This doesn't look like a valid rule file.", e)
        }
        require(export.schemaVersion <= RULE_EXPORT_SCHEMA_VERSION) {
            "This rule was exported from a newer version of Notificapp and can't be imported here."
        }
        require(export.rule.name.isNotBlank()) {
            "This rule has no name."
        }
        export.toDomain()
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
            actions = actions.map { action ->
                action.copy(
                    id = UUID.randomUUID().toString(),
                    fields = action.fields.map { it.copy(id = UUID.randomUUID().toString()) },
                )
            },
            createdAt = now,
            updatedAt = now,
        )
    }
}
