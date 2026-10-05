package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.TonalCard
import dev.gaferneira.notificapp.core.ui.theme.NotificappStyles
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.EditorMode
import dev.gaferneira.notificapp.features.ruleeditor.contract.RuleEditorContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorIssue
import dev.gaferneira.notificapp.features.ruleeditor.domain.EditorStep

/*
 * Validation presentation shared by the guided flow and the single-page editor. Every message comes
 * from the issue/warning itself, so the UI never decides what is wrong, only how to show it.
 */

/** Small inline row under a section: a warning (neutral) or a blocking issue (error colour). */
@Composable
internal fun ValidationNotice(text: String, isBlocking: Boolean, modifier: Modifier = Modifier) {
    val color = if (isBlocking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    // The icon is decorative, so severity is spoken as a state; a polite live region announces notices as they appear.
    val severity = stringResource(
        if (isBlocking) R.string.rule_editor_a11y_notice_blocking else R.string.rule_editor_a11y_notice_warning,
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                liveRegion = LiveRegionMode.Polite
                stateDescription = severity
            },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = if (isBlocking) Icons.Default.Warning else Icons.Default.Info,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

/**
 * Inline notices of [step]: its warnings always, its blocking issues only on the single page (the
 * guided flow already explains a disabled Next in the bottom bar, so they would be said twice).
 */
@Composable
internal fun StepNotices(uiState: UiState, step: EditorStep, modifier: Modifier = Modifier) {
    val issues = if (uiState.mode == EditorMode.SINGLE_PAGE) uiState.blockingIssuesFor(step).filter { it != EditorIssue.NAME_REQUIRED } else emptyList()
    val warnings = uiState.warningsFor(step)
    if (issues.isEmpty() && warnings.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        issues.forEach { ValidationNotice(text = stringResource(it.message), isBlocking = true) }
        warnings.forEach { ValidationNotice(text = stringResource(it.message), isBlocking = false) }
    }
}

/**
 * "Needs attention" card listing blocking [issues]. Tapping an issue reports it through
 * [onIssueClick]; with [showJumpButton] each row also carries an explicit "Go to step" button (guided review).
 */
@Composable
internal fun NeedsAttentionCard(
    issues: List<EditorIssue>,
    onIssueClick: (EditorIssue) -> Unit,
    modifier: Modifier = Modifier,
    showJumpButton: Boolean = false,
) {
    TonalCard(modifier = modifier, style = NotificappStyles.warningCardStyle) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.rule_editor_attention_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.semantics { heading() },
            )
            val fixLabel = stringResource(R.string.rule_editor_a11y_fix_issue)
            issues.forEach { issue ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            // With an explicit jump button the row is read-only text; otherwise the whole row is the button.
                            if (showJumpButton) {
                                Modifier
                            } else {
                                Modifier.clickable(onClickLabel = fixLabel, role = Role.Button) { onIssueClick(issue) }
                            },
                        )
                        .heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(issue.message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    if (showJumpButton) {
                        // Default text-button colour (primary) lacks contrast on the error container; match the card text.
                        TextButton(
                            onClick = { onIssueClick(issue) },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
                        ) { Text(stringResource(R.string.rule_editor_attention_jump)) }
                    }
                }
            }
        }
    }
}

/** One-line dismissible hint (template / notification prefill). */
@Composable
internal fun DismissibleHintCard(text: String, dismissLabel: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    TonalCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    }
}
