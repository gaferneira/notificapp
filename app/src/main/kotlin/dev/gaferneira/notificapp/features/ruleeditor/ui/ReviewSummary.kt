package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.RuleSummaryText
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.domain.model.RuleSummary
import dev.gaferneira.notificapp.domain.model.RuleSummary.ActionRun

/** Plain-language read-back of the rule shown on the review step. */
@Composable
internal fun ReviewSummaryCard(summary: RuleSummary, modifier: Modifier = Modifier) {
    TonalCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.rule_editor_summary_title),
                style = MaterialTheme.typography.titleMedium,
            )
            RuleSummaryText(summary = summary)
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
