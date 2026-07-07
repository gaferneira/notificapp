package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AddButton

/**
 * The "Extract data" action sheet. Unlike the other action types, `SAVE_DATA` has no scalar config
 * form - it owns the rule's extraction fields. This sheet hosts the field manager (add / edit /
 * remove / auto-generate); field values are only persisted while this action is present and enabled
 * (see the extraction-gating in `ProcessNotificationUseCase`).
 *
 * Fields live on the parent `RuleEditorViewModel` (`rule.fields`), so all field callbacks route to
 * the parent - this sheet is a presentation container, not a separate source of truth.
 */
@Composable
fun ExtractDataBottomSheet(
    fields: List<RuleField>,
    callbacks: ExtractionFieldCallbacks,
    onDismiss: () -> Unit,
) {
    ActionConfigSheet(
        title = "Extract data",
        confirmLabel = "Done",
        onConfirm = onDismiss,
        onDismiss = onDismiss,
    ) {
        ActionSheetDescription("Extract and store data fields from the notification.")
        DataExtractionSection(
            fields = fields,
            onAutoGenerate = callbacks.onAutoGenerate,
            onAddField = callbacks.onAddField,
            onEditField = callbacks.onEditField,
            onRemoveField = callbacks.onRemoveField,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * The data-extraction field manager, hosted inside the Extract-data ("Extract data") action sheet.
 * Shows the extraction fields with add/edit/remove and an auto-generate shortcut.
 */
@Composable
private fun DataExtractionSection(
    fields: List<RuleField>,
    onAutoGenerate: () -> Unit,
    onAddField: () -> Unit,
    onEditField: (String) -> Unit,
    onRemoveField: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Description with auto-generate shortcut
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Define the data fields you want to extract from the notification text using rules.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Auto-generate button
            IconButton(
                onClick = onAutoGenerate,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoFixHigh,
                    contentDescription = "Auto-generate extraction",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        // Field cards
        fields.forEach { field ->
            ExtractionFieldItem(
                field = field,
                onClick = { onEditField(field.id) },
                onRemove = { onRemoveField(field.id) },
            )
        }

        // Add field button (outlined style)
        AddButton(
            text = "Add field",
            onClick = onAddField,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ExtractionFieldItem(
    field: RuleField,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val iconColor = when (field.name.lowercase()) {
        "amount", "price", "total" -> MaterialTheme.colorScheme.tertiaryContainer
        "merchant", "store", "seller" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }

    val iconTint = when (field.name.lowercase()) {
        "amount", "price", "total" -> MaterialTheme.colorScheme.tertiary
        "merchant", "store", "seller" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    val icon = when (field.name.lowercase()) {
        "amount", "price", "total" -> Icons.Default.Payments
        "merchant", "store", "seller" -> Icons.Default.Store
        else -> Icons.Default.Payments
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                // Field icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconColor),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = field.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = field.method.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Drag handle and delete
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove field",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DataExtractionSectionPreview() {
    NotificappTheme {
        DataExtractionSection(
            fields = listOf(
                RuleField(
                    id = "1",
                    name = "Merchant",
                    method = ExtractionMethod.LineExtraction(10),
                ),
                RuleField(
                    id = "2",
                    name = "Amount",
                    method = ExtractionMethod.RegexPattern("\\d+(\\.\\d+)?"),
                ),
            ),
            onAutoGenerate = {},
            onAddField = {},
            onEditField = {},
            onRemoveField = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun DataExtractionSectionEmptyPreview() {
    NotificappTheme {
        DataExtractionSection(
            fields = emptyList(),
            onAutoGenerate = {},
            onAddField = {},
            onEditField = {},
            onRemoveField = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
