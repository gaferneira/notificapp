package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleSummary
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleSummary.ActionRun
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleSummary.AppScope
import dev.gaferneira.notificapp.features.ruleeditor.domain.RuleSummary.ConditionMatch
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.displayText

/** Apps listed by name in the summary; longer lists collapse to a count. */
private const val MAX_NAMED_APPS = 3

/** Plain-language read-back of the rule shown on the review step. */
@Composable
internal fun ReviewSummaryCard(summary: RuleSummary, modifier: Modifier = Modifier) {
    TonalCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.rule_editor_summary_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = summary.sentence(),
                style = MaterialTheme.typography.bodyLarge,
            )
            summary.actionRunText()?.let { runText ->
                Text(
                    text = runText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Whether the actions will run or matches are only recorded, so test mode is visible with Advanced collapsed. */
@Composable
private fun RuleSummary.actionRunText(): String? = when (actionRun) {
    ActionRun.RUN -> stringResource(R.string.rule_editor_summary_actions_run)
    ActionRun.RECORD_ONLY -> stringResource(R.string.rule_editor_summary_record_only)
    ActionRun.NONE -> null
}

/** "When a notification from <apps> matches <conditions>, then <actions>." resolved from string resources. */
@Composable
private fun RuleSummary.sentence(): String {
    val scopeText = scopeText()
    val actionsText = if (actions.isEmpty()) {
        stringResource(R.string.rule_editor_summary_no_actions)
    } else {
        actions.map { stringResource(it.ui().labelRes).replaceFirstChar(Char::lowercase) }.joinToString(", ")
    }
    if (conditionMatch == ConditionMatch.NONE) {
        return stringResource(R.string.rule_editor_summary_no_conditions, scopeText, actionsText)
    }
    val conditionsText = conditions.map { it.displayText() }.joinToString("; ")
    val matchText = stringResource(
        when (conditionMatch) {
            ConditionMatch.ALL -> R.string.rule_editor_summary_match_all
            ConditionMatch.ANY -> R.string.rule_editor_summary_match_any
            else -> R.string.rule_editor_summary_match_single
        },
        conditionsText,
    )
    return stringResource(R.string.rule_editor_summary_with_conditions, scopeText, matchText, actionsText)
}

@Composable
private fun RuleSummary.scopeText(): String = when (val appScope = scope) {
    AppScope.AllApps -> stringResource(R.string.rule_editor_summary_scope_all)
    is AppScope.Only -> appListText(appScope.appNames)
    is AppScope.Except -> stringResource(R.string.rule_editor_summary_scope_except, appListText(appScope.appNames))
}

@Composable
private fun appListText(names: List<String>): String = if (names.size <= MAX_NAMED_APPS) {
    names.joinToString(", ")
} else {
    pluralStringResource(R.plurals.rule_editor_summary_apps_count, names.size, names.size)
}
