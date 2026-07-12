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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
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
        title = { Text("Add action") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                Text("Cancel")
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
            .clickable(onClick = onClick)
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
            Text(
                text = meta.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = meta.description,
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
