package dev.gaferneira.notificapp.features.notificationdetail.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.DryRunBadge
import dev.gaferneira.notificapp.core.ui.components.StatusPill
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.mapping.ui
import dev.gaferneira.notificapp.domain.model.ExtractionPreview
import dev.gaferneira.notificapp.domain.model.FieldChange
import dev.gaferneira.notificapp.domain.model.FieldDiff
import dev.gaferneira.notificapp.domain.model.RuleMatchPreview
import dev.gaferneira.notificapp.domain.model.UnmatchedExecution
import dev.gaferneira.notificapp.features.notificationdetail.contract.NotificationDetailContract.PreviewFailure

/** Bottom sheet hosting the read-only "test current rules" result. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TestRulesPreviewSheet(
    preview: ExtractionPreview?,
    failure: PreviewFailure?,
    canApply: Boolean,
    isApplying: Boolean,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        TestRulesPreviewContent(
            preview = preview,
            failure = failure,
            canApply = canApply,
            isApplying = isApplying,
            onApply = onApply,
            onRetry = onRetry,
        )
    }
}

@Composable
internal fun TestRulesPreviewContent(
    preview: ExtractionPreview?,
    failure: PreviewFailure?,
    canApply: Boolean,
    isApplying: Boolean,
    onApply: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = stringResource(R.string.notification_detail_preview_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(R.string.notification_detail_preview_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
        }
        if (failure != null) {
            FailureMessage(failure = failure, onRetry = onRetry)
        } else if (preview != null) {
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                previewItems(preview)
            }
            Button(
                onClick = onApply,
                enabled = canApply,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .heightIn(min = 48.dp),
            ) {
                if (isApplying) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.notification_detail_preview_updating))
                } else {
                    Text(stringResource(R.string.notification_detail_preview_update))
                }
            }
        }
    }
}

private fun LazyListScope.previewItems(preview: ExtractionPreview) {
    if (preview.matches.isEmpty()) {
        item(key = "no-matches") {
            Text(
                text = stringResource(R.string.notification_detail_preview_no_matches),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    items(items = preview.matches, key = { "match-${it.ruleId}" }) { MatchCard(it) }
    if (preview.noLongerMatching.isNotEmpty()) {
        item(key = "no-longer-header") { NoLongerMatchingHeader() }
        items(items = preview.noLongerMatching, key = { "gone-${it.executionId}" }) { UnmatchedRow(it) }
    }
}

@Composable
private fun FailureMessage(failure: PreviewFailure, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(
                when (failure) {
                    PreviewFailure.CONTENT_REDACTED -> R.string.notification_detail_preview_failure_redacted
                    PreviewFailure.EVALUATION_FAILED -> R.string.notification_detail_preview_failure_evaluation
                },
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (failure == PreviewFailure.EVALUATION_FAILED) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.notification_detail_preview_try_again))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatchCard(match: RuleMatchPreview, modifier: Modifier = Modifier) {
    TonalCard(modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = match.ruleName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            if (match.isDryRun) DryRunBadge()
        }
        if (match.executionId == null) {
            StatusPill(
                icon = Icons.Default.Info,
                text = stringResource(R.string.notification_detail_preview_not_recorded),
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.notification_detail_preview_not_recorded_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        if (match.fieldDiffs.isEmpty()) {
            Text(
                text = stringResource(R.string.notification_detail_preview_no_fields),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        } else {
            Column(
                modifier = Modifier.padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                match.fieldDiffs.forEach { FieldDiffRow(it) }
            }
        }
        if (match.actionTypes.isNotEmpty()) {
            WouldRunActions(match)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WouldRunActions(match: RuleMatchPreview) {
    Text(
        text = stringResource(R.string.notification_detail_preview_actions_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        match.actionTypes.forEach { type ->
            val ui = type.ui()
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = ui.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(stringResource(ui.labelRes), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FieldDiffRow(diff: FieldDiff, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = diff.fieldName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            ChangeChip(diff.change)
        }
        when (diff.change) {
            FieldChange.UNCHANGED -> DiffValue(diff.newValue ?: diff.oldValue.orEmpty())
            FieldChange.CHANGED -> {
                DiffValue(stringResource(R.string.notification_detail_diff_was, diff.oldValue.orEmpty()))
                DiffValue(stringResource(R.string.notification_detail_diff_now, diff.newValue.orEmpty()))
            }
            FieldChange.NEW -> DiffValue(stringResource(R.string.notification_detail_diff_now, diff.newValue.orEmpty()))
            FieldChange.REMOVED -> {
                DiffValue(stringResource(R.string.notification_detail_diff_was, diff.oldValue.orEmpty()))
                DiffValue(stringResource(R.string.notification_detail_diff_no_longer_extracted))
            }
        }
    }
}

@Composable
private fun DiffValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun ChangeChip(change: FieldChange) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (change) {
        FieldChange.UNCHANGED -> colors.surfaceContainerHighest to colors.onSurfaceVariant
        FieldChange.CHANGED -> colors.tertiaryContainer to colors.onTertiaryContainer
        FieldChange.NEW -> colors.primaryContainer to colors.onPrimaryContainer
        FieldChange.REMOVED -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Surface(shape = MaterialTheme.shapes.small, color = container) {
        Text(
            text = stringResource(
                when (change) {
                    FieldChange.UNCHANGED -> R.string.notification_detail_diff_unchanged
                    FieldChange.CHANGED -> R.string.notification_detail_diff_changed
                    FieldChange.NEW -> R.string.notification_detail_diff_new
                    FieldChange.REMOVED -> R.string.notification_detail_diff_removed
                },
            ),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun NoLongerMatchingHeader() {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(
            text = stringResource(R.string.notification_detail_preview_no_longer_matching),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.notification_detail_preview_no_longer_matching_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun UnmatchedRow(unmatched: UnmatchedExecution) {
    val name = unmatched.ruleName
    Text(
        text = name ?: stringResource(R.string.notification_detail_deleted_rule),
        style = MaterialTheme.typography.bodyMedium,
        fontStyle = if (name == null) FontStyle.Italic else null,
        modifier = Modifier.heightIn(min = 24.dp),
    )
}
