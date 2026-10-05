package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.BetaBadge
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.isBeta
import dev.gaferneira.notificapp.features.ruleeditor.domain.ActionTypeUi
import dev.gaferneira.notificapp.features.ruleeditor.domain.availableActionTypes
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui

/**
 * Dialog that lets the user pick which action type to add. Only the [availableTypes] (types not yet
 * configured on the rule) are shown, enforcing one action per type. Selecting a type reports it via
 * [onTypeSelected]; the caller then opens the type-scoped configuration sheet.
 */
@Composable
fun ActionTypePickerDialog(
    availableTypes: List<ActionType>,
    onTypeSelected: (ActionType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rule_editor_add_action), modifier = Modifier.semantics { heading() }) },
        text = {
            // Scrolls inside the dialog's bounded text slot so up to 8 rows never clip (landscape, large fonts).
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                availableTypes.forEach { type ->
                    ActionTypeRow(
                        meta = type.ui(),
                        onClick = { onTypeSelected(type) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.rule_editor_action_cancel))
            }
        },
    )
}

@Composable
private fun ActionTypeRow(
    meta: ActionTypeUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = meta.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(meta.labelRes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                if (meta.type.isBeta) {
                    Spacer(modifier = Modifier.width(6.dp))
                    BetaBadge()
                }
            }
            Text(
                text = stringResource(meta.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(showBackground = true, name = "ActionTypePickerDialog Light")
@Preview(showBackground = true, name = "ActionTypePickerDialog Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActionTypePickerDialogPreview() {
    NotificappTheme(dynamicColor = false) {
        // Preview the row list directly (AlertDialog can't render standalone in previews cleanly)
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableActionTypes(configured = listOf(ActionType.SAVE_DATA)).forEach { type ->
                ActionTypeRow(meta = type.ui(), onClick = {})
            }
        }
    }
}

@Preview(showBackground = true, name = "ActionTypePickerDialog Font 2x", fontScale = 2f, heightDp = 480)
@Composable
private fun ActionTypePickerDialogLargeFontPreview() {
    NotificappTheme(dynamicColor = false) {
        Column(
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            availableActionTypes(configured = emptyList()).take(3).forEach { type ->
                ActionTypeRow(meta = type.ui(), onClick = {})
            }
        }
    }
}
