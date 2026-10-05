package dev.gaferneira.notificapp.domain.model

/**
 * Structured, string-free description of what a rule does, used to render the plain-language
 * summary ("When a notification from X matches Y, then Z") in the rule editor's review step and on
 * the rule details screen. Kept pure so the content rules are unit-testable; the sentence itself is
 * assembled from string resources in the UI (`core/ui/components/RuleSummaryText.kt`).
 */
data class RuleSummary(
    val scope: AppScope,
    val conditionMatch: ConditionMatch,
    val conditions: List<RuleCondition>,
    /** Actions to describe, in rule order. Disabled ones are only present when requested from the builder. */
    val actions: List<Action>,
    /** Whether the enabled actions will run, or matches are only recorded (test mode). */
    val actionRun: ActionRun = ActionRun.NONE,
) {
    /** An action in the summary; [isEnabled] false means it is configured but currently switched off. */
    data class Action(val type: ActionType, val isEnabled: Boolean = true)

    sealed interface AppScope {
        /** Apps the scope refers to; empty for [AllApps]. */
        val apps: List<AppInfo>

        /** Display names of [apps], falling back to the package name when the name is blank. */
        val appNames: List<String> get() = apps.map { it.name.ifBlank { it.packageName } }

        /** No app restriction. */
        data object AllApps : AppScope {
            override val apps: List<AppInfo> = emptyList()
        }

        /** Only these apps. */
        data class Only(override val apps: List<AppInfo>) : AppScope

        /** Every app except these. */
        data class Except(override val apps: List<AppInfo>) : AppScope
    }

    /** How the conditions are described; [ALL]/[ANY] only apply with two or more conditions. */
    enum class ConditionMatch { NONE, SINGLE, ALL, ANY }

    /** [NONE]: no enabled action to describe; [RUN]: actions execute; [RECORD_ONLY]: test mode, nothing executes. */
    enum class ActionRun { NONE, RUN, RECORD_ONLY }
}

/**
 * Builds the [RuleSummary] of this rule.
 *
 * @param includeDisabledActions false (rule editor) lists only enabled actions; true (rule details)
 *   lists every action and flags the disabled ones via [RuleSummary.Action.isEnabled].
 */
fun Rule.toSummary(includeDisabledActions: Boolean = false): RuleSummary {
    val apps = targetApps.orEmpty()
    val scope = when {
        apps.isEmpty() -> RuleSummary.AppScope.AllApps
        isIncludeMode -> RuleSummary.AppScope.Only(apps)
        else -> RuleSummary.AppScope.Except(apps)
    }
    val match = when {
        conditions.isEmpty() -> RuleSummary.ConditionMatch.NONE
        conditions.size == 1 -> RuleSummary.ConditionMatch.SINGLE
        conditionLogic == ConditionCombinator.ANY -> RuleSummary.ConditionMatch.ANY
        else -> RuleSummary.ConditionMatch.ALL
    }
    val hasEnabledActions = actions.any { it.isEnabled }
    val actionRun = when {
        isDryRun -> RuleSummary.ActionRun.RECORD_ONLY
        !hasEnabledActions -> RuleSummary.ActionRun.NONE
        else -> RuleSummary.ActionRun.RUN
    }
    val described = if (includeDisabledActions) actions else actions.filter { it.isEnabled }
    return RuleSummary(
        scope = scope,
        conditionMatch = match,
        conditions = conditions,
        actions = described.map { RuleSummary.Action(type = it.type, isEnabled = it.isEnabled) },
        actionRun = actionRun,
    )
}
