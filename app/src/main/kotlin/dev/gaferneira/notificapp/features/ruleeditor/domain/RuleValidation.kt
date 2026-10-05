package dev.gaferneira.notificapp.features.ruleeditor.domain

import androidx.annotation.StringRes
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.domain.model.ActionType

/**
 * Reasons a draft cannot be saved. Each issue knows the guided [step] it belongs to (so the flow can
 * gate Next and jump to it), its full [message] (inline notice and "Needs attention" card) and, for
 * issues that gate a Next button, a short [nextBlockedMessage] explaining why Next is disabled.
 */
enum class EditorIssue(
    val step: EditorStep,
    @StringRes val message: Int,
    @StringRes val nextBlockedMessage: Int? = null,
) {
    NAME_REQUIRED(EditorStep.REVIEW, R.string.rule_editor_attention_name),
    RULE_TOO_BROAD(EditorStep.WHEN, R.string.rule_editor_issue_too_broad, R.string.rule_editor_next_blocked_too_broad),
    ACTION_REQUIRED(EditorStep.DO, R.string.rule_editor_issue_action_required, R.string.rule_editor_next_blocked_action_required),
}

/** Non-blocking heads-ups about a draft; the rule can still be saved. */
enum class EditorWarning(val step: EditorStep, @StringRes val message: Int) {
    MATCHES_EVERY_NOTIFICATION_OF_APPS(EditorStep.WHEN, R.string.rule_editor_warning_matches_all_of_apps),
}

/** Outcome of [RuleValidation.evaluate], ordered the way the single-page editor lays its sections out. */
data class ValidationResult(
    val blocking: List<EditorIssue>,
    val warnings: List<EditorWarning>,
)

/** Pure validation of a draft rule; the single source of truth for gating, messages and warnings. */
object RuleValidation {

    fun evaluate(rule: RuleUiModel): ValidationResult {
        val blocking = buildList {
            if (rule.name.isBlank()) add(EditorIssue.NAME_REQUIRED)
            if (rule.isTooBroad()) add(EditorIssue.RULE_TOO_BROAD)
            if (!rule.hasEnabledAction()) add(EditorIssue.ACTION_REQUIRED)
        }
        val warnings = buildList {
            if (rule.triggers.isEmpty() && rule.targetApps.isNotEmpty() && rule.isIncludeMode) {
                add(EditorWarning.MATCHES_EVERY_NOTIFICATION_OF_APPS)
            }
        }
        return ValidationResult(blocking, warnings)
    }

    /** No conditions and an app scope of "all apps" or "all except ...": matches (almost) everything. */
    private fun RuleUiModel.isTooBroad(): Boolean = triggers.isEmpty() && (targetApps.isEmpty() || !isIncludeMode)

    /**
     * Draft fields without any Extract-data action get an enabled one on save (see
     * [RuleUiModel.toEntity]), so they count as an enabled action here too.
     */
    private fun RuleUiModel.hasEnabledAction(): Boolean = actions.any { it.isEnabled } ||
        (fields.isNotEmpty() && actions.none { it.type == ActionType.SAVE_DATA })
}
