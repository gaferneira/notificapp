package dev.gaferneira.notificapp.features.insights.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.mapping.ui
import dev.gaferneira.notificapp.features.insights.contract.ActionBreakdownUiState

/** Bottom sheet listing which rules produced the action results behind a number on the dashboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActionBreakdownSheet(
    state: ActionBreakdownUiState,
    onRuleClick: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, modifier = modifier) {
        ActionBreakdownContent(state = state, onRuleClick = onRuleClick)
    }
}

@Composable
internal fun ActionBreakdownContent(state: ActionBreakdownUiState, onRuleClick: (String) -> Unit, modifier: Modifier = Modifier) {
    val title = state.type?.let { stringResource(it.ui().labelRes) } ?: stringResource(R.string.insights_action_sheet_title_all)
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        when {
            state.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(24.dp))
            state.rows.isEmpty() -> Text(
                text = stringResource(R.string.insights_action_sheet_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp),
            )
            else -> LazyColumn {
                itemsIndexed(state.rows, key = { _, row -> "${row.type}/${row.ruleId ?: row.ruleName}" }) { index, row ->
                    if (index > 0) HorizontalDivider()
                    val typeUi = row.type.ui()
                    InsightsRow(
                        title = row.ruleName,
                        annotatedSubtitle = outcomeCountsText(
                            counts = row.counts,
                            // Per-type sheets already say which action in the title.
                            prefix = if (state.type == null) stringResource(typeUi.labelRes) else null,
                        ),
                        icon = typeUi.icon.takeIf { state.type == null },
                        // Deleted rule (null id): history is still counted but there is nothing to open.
                        onClick = row.ruleId?.let { id -> { onRuleClick(id) } },
                    )
                }
            }
        }
    }
}
