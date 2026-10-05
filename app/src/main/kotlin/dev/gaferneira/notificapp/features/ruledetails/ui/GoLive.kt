package dev.gaferneira.notificapp.features.ruledetails.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.mapping.ui
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.Rule
import dev.gaferneira.notificapp.domain.model.RuleAction
import kotlinx.collections.immutable.persistentListOf

/**
 * Prominent banner shown while a rule is in dry-run (test) mode: explains what that means and
 * offers the "Go live" entry point. Turning dry run back on stays in the editor.
 */
@Composable
internal fun GoLiveBanner(
    onGoLive: () -> Unit,
    modifier: Modifier = Modifier,
    testModeMatches: Int = 0,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.rule_details_go_live_banner_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.rule_details_go_live_banner_message),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (testModeMatches > 0) {
                Text(
                    text = pluralStringResource(
                        R.plurals.rule_details_go_live_banner_matches,
                        testModeMatches,
                        testModeMatches,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Button(
                onClick = onGoLive,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                ),
            ) {
                Text(stringResource(R.string.rule_details_go_live))
            }
        }
    }
}

/** Confirmation dialog for going live; lists what will start running. */
@Composable
internal fun GoLiveDialog(
    rule: Rule,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_details_go_live_dialog_title)) },
        text = { GoLiveDialogContent(rule = rule) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.rule_details_go_live))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rule_details_cancel))
            }
        },
    )
}

@Composable
internal fun GoLiveDialogContent(
    rule: Rule,
    modifier: Modifier = Modifier,
) {
    val enabledActions = rule.actions.filter { it.isEnabled }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (enabledActions.isEmpty()) {
            Text(stringResource(R.string.rule_details_go_live_dialog_no_actions))
        } else {
            Text(
                pluralStringResource(
                    R.plurals.rule_details_go_live_dialog_actions,
                    enabledActions.size,
                    enabledActions.size,
                ),
            )
            enabledActions.forEach { action ->
                val ui = action.type.ui()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = ui.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(text = stringResource(ui.labelRes), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (rule.deleteRawContentAfterExtraction) {
            Text(
                text = stringResource(R.string.rule_details_go_live_dialog_redaction),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// region Previews

@Preview(showBackground = true)
@Composable
private fun GoLiveBannerPreview() {
    NotificappTheme(dynamicColor = false) { GoLiveBanner(onGoLive = {}, modifier = Modifier.padding(16.dp)) }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GoLiveBannerPreviewDark() {
    NotificappTheme(dynamicColor = false) { GoLiveBanner(onGoLive = {}, modifier = Modifier.padding(16.dp)) }
}

private fun previewDialogRule(): Rule = previewRule().copy(
    deleteRawContentAfterExtraction = true,
    actions = persistentListOf(
        RuleAction.createFlashAlert(id = "a1"),
        RuleAction.createAlarm(id = "a2"),
    ),
)

@Preview(showBackground = true)
@Composable
private fun GoLiveDialogContentPreview() {
    NotificappTheme(dynamicColor = false) {
        GoLiveDialogContent(rule = previewDialogRule(), modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GoLiveDialogContentPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        GoLiveDialogContent(rule = previewDialogRule(), modifier = Modifier.padding(16.dp))
    }
}

// endregion
