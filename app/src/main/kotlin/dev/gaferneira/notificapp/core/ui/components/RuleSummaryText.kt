package dev.gaferneira.notificapp.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.mapping.ui
import dev.gaferneira.notificapp.core.ui.text.displayText
import dev.gaferneira.notificapp.domain.model.RuleSummary
import dev.gaferneira.notificapp.domain.model.RuleSummary.AppScope
import dev.gaferneira.notificapp.domain.model.RuleSummary.ConditionMatch

/** Apps listed by name in the sentence; longer lists collapse to a count. */
private const val MAX_NAMED_APPS = 3

/**
 * The plain-language sentence of a [RuleSummary] ("When a notification from X matches Y, then Z"),
 * shared by the rule editor's review step and the rule details screen. Disabled actions (only
 * present when the summary was built with them) are flagged "(disabled)".
 */
@Composable
fun RuleSummaryText(
    summary: RuleSummary,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    Text(text = summary.sentence(), style = style, modifier = modifier)
}

/** "When a notification from <apps> matches <conditions>, then <actions>." resolved from string resources. */
@Composable
private fun RuleSummary.sentence(): String {
    val scopeText = scopeText()
    val actionsText = if (actions.isEmpty()) {
        stringResource(R.string.rule_summary_no_actions)
    } else {
        actions.map { action -> action.text() }.joinToString(", ")
    }
    if (conditionMatch == ConditionMatch.NONE) {
        return stringResource(R.string.rule_summary_no_conditions, scopeText, actionsText)
    }
    val conditionsText = conditions.map { it.displayText() }.joinToString("; ")
    val matchText = stringResource(
        when (conditionMatch) {
            ConditionMatch.ALL -> R.string.rule_summary_match_all
            ConditionMatch.ANY -> R.string.rule_summary_match_any
            else -> R.string.rule_summary_match_single
        },
        conditionsText,
    )
    return stringResource(R.string.rule_summary_with_conditions, scopeText, matchText, actionsText)
}

@Composable
private fun RuleSummary.Action.text(): String {
    val label = stringResource(type.ui().labelRes).replaceFirstChar(Char::lowercase)
    return if (isEnabled) label else stringResource(R.string.rule_summary_action_disabled, label)
}

@Composable
private fun RuleSummary.scopeText(): String = when (val appScope = scope) {
    AppScope.AllApps -> stringResource(R.string.rule_summary_scope_all)
    is AppScope.Only -> appListText(appScope.appNames)
    is AppScope.Except -> stringResource(R.string.rule_summary_scope_except, appListText(appScope.appNames))
}

@Composable
private fun appListText(names: List<String>): String = if (names.size <= MAX_NAMED_APPS) {
    names.joinToString(", ")
} else {
    pluralStringResource(R.plurals.rule_summary_apps_count, names.size, names.size)
}
