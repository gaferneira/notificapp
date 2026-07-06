package dev.gaferneira.notificapp.core.rulesharing

import dev.gaferneira.notificapp.domain.model.Rule
import kotlinx.serialization.Serializable

/**
 * Current version of the rule export wire format. Bump this and handle the previous version in
 * [RuleJsonCodec.decode] if the shape of [RuleExport] ever needs a breaking change.
 */
const val RULE_EXPORT_SCHEMA_VERSION = 1

/**
 * Versioned envelope for sharing a [Rule] as JSON (export/import, community rule gallery).
 * See `docs/rule-format.md` for the full format documentation.
 */
@Serializable
data class RuleExport(
    val schemaVersion: Int = RULE_EXPORT_SCHEMA_VERSION,
    val rule: Rule,
)
