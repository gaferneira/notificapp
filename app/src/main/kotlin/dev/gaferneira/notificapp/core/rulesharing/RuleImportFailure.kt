package dev.gaferneira.notificapp.core.rulesharing

import dev.gaferneira.notificapp.core.common.Failure

/**
 * Reasons [RuleJsonCodec.decode] can reject a rule. Typed (ADR 006) so the presentation layer can
 * map each case to a localized message instead of surfacing the English [message], which is kept
 * only as a technical detail for logs.
 */
sealed class RuleImportFailure(
    override val message: String,
    cause: Throwable? = null,
) : Failure.FeatureFailure(cause) {

    /** The source is not valid JSON or doesn't match the rule envelope. */
    class InvalidFile(cause: Throwable? = null) : RuleImportFailure("This doesn't look like a valid rule file.", cause)

    /** The envelope was exported by a newer schema version than this app understands. */
    class UnsupportedSchemaVersion(val version: Int) : RuleImportFailure("This rule was exported from a newer version of Notificapp and can't be imported here.")

    /** The rule has a blank name. */
    class MissingName : RuleImportFailure("This rule has no name.")

    /** A condition type, operator or field type this app version doesn't recognize. */
    class UnknownValue(val label: String, val value: String) : RuleImportFailure("Unknown $label: \"$value\". This rule may require a newer version of Notificapp.")

    /** Conditions are nested deeper than [maxDepth]. */
    class NestedTooDeeply(val maxDepth: Int) : RuleImportFailure("This rule's conditions are nested too deeply (max $maxDepth levels) and can't be imported.")
}
