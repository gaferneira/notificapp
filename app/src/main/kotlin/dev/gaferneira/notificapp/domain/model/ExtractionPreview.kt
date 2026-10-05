package dev.gaferneira.notificapp.domain.model

/** How one field's stored value compares to what the current rules would extract. */
enum class FieldChange {
    /** Stored and previewed values are identical. */
    UNCHANGED,

    /** Both exist but differ ([FieldDiff.oldValue] -> [FieldDiff.newValue]). */
    CHANGED,

    /** Nothing stored, but the current rule would extract a value. */
    NEW,

    /** A value is stored, but the current rule no longer extracts one. */
    REMOVED,
}

/** Stored-vs-previewed comparison for one field of one rule. */
data class FieldDiff(
    val fieldId: String,
    val fieldName: String,
    val fieldType: RuleField.FieldType,
    val oldValue: String?,
    val newValue: String?,
    val change: FieldChange,
)

/**
 * One rule that matches the notification when evaluated with the rules as they are now.
 *
 * @property executionId The stored execution of this rule for the notification, or `null` when the
 *   rule never ran on it (e.g. it was created or edited afterwards). Such matches are shown but
 *   never persisted.
 * @property fieldDiffs One entry per current field of the rule.
 * @property actionTypes Types of the rule's enabled actions (what would run on a live match).
 * @property update What applying this preview would write, or `null` when there is nothing to
 *   write (no stored execution, or no changed field).
 */
data class RuleMatchPreview(
    val ruleId: String,
    val ruleName: String,
    val isDryRun: Boolean,
    val executionId: String?,
    val fieldDiffs: List<FieldDiff>,
    val actionTypes: List<ActionType>,
    val update: ExtractedDataUpdate?,
) {
    val hasChanges: Boolean get() = fieldDiffs.any { it.change != FieldChange.UNCHANGED }
}

/** A stored execution whose rule no longer matches (or no longer exists / is inactive). */
data class UnmatchedExecution(
    val executionId: String,
    val ruleId: String,
    val ruleName: String?,
)

/**
 * Read-only result of evaluating the currently active rules against a notification.
 * Nothing in it has been persisted.
 */
data class ExtractionPreview(
    val matches: List<RuleMatchPreview>,
    val noLongerMatching: List<UnmatchedExecution>,
) {
    /** Updates that applying the preview would write to existing executions. */
    val updates: List<ExtractedDataUpdate> get() = matches.mapNotNull { it.update }

    /** Whether applying the preview would change any stored extracted data. */
    val hasUpdatableChanges: Boolean get() = updates.isNotEmpty()
}

/**
 * Replacement of the extracted values of one existing execution.
 *
 * @property fields The rule's current fields; their stored values are replaced (and typed from these).
 * @property extractedData The execution's complete new `extractedData` map. Stored values for
 *   fields that no longer exist on the rule are carried over unchanged, never dropped.
 */
data class ExtractedDataUpdate(
    val executionId: String,
    val fields: List<RuleField>,
    val extractedData: Map<String, String>,
)
