package dev.gaferneira.notificapp.features.ruleeditor.domain

import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.ConditionCombinator
import dev.gaferneira.notificapp.domain.model.RuleCondition

/**
 * Structured, string-free description of what a draft rule does, used to render the plain-language
 * summary ("When a notification from X matches Y, then Z") on the review step. Kept pure so the
 * content rules are unit-testable; the sentence itself is assembled from string resources in the UI.
 */
data class RuleSummary(
    val scope: AppScope,
    val conditionMatch: ConditionMatch,
    val conditions: List<RuleCondition>,
    /** Types of the enabled actions, in rule order. */
    val actions: List<ActionType>,
) {
    sealed interface AppScope {
        /** No app restriction. */
        data object AllApps : AppScope

        /** Only these apps (display names). */
        data class Only(val appNames: List<String>) : AppScope

        /** Every app except these (display names). */
        data class Except(val appNames: List<String>) : AppScope
    }

    /** How the conditions are described; [ALL]/[ANY] only apply with two or more conditions. */
    enum class ConditionMatch { NONE, SINGLE, ALL, ANY }
}

fun RuleUiModel.toSummary(): RuleSummary {
    val appNames = targetApps.map { app -> app.name.ifBlank { app.packageName } }
    val scope = when {
        appNames.isEmpty() -> RuleSummary.AppScope.AllApps
        isIncludeMode -> RuleSummary.AppScope.Only(appNames)
        else -> RuleSummary.AppScope.Except(appNames)
    }
    val match = when {
        triggers.isEmpty() -> RuleSummary.ConditionMatch.NONE
        triggers.size == 1 -> RuleSummary.ConditionMatch.SINGLE
        conditionLogic == ConditionCombinator.ANY -> RuleSummary.ConditionMatch.ANY
        else -> RuleSummary.ConditionMatch.ALL
    }
    return RuleSummary(
        scope = scope,
        conditionMatch = match,
        conditions = triggers,
        actions = actions.filter { it.isEnabled }.map { it.type },
    )
}
