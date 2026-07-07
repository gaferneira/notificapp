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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AddButton
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.confirmLabelFor
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.ExtractDataViewModel

/**
 * The "Extract data" action sheet. Unlike the other action types, `SAVE_DATA` has no scalar config
 * form - it owns the rule's extraction fields. This sheet hosts the field manager (add / edit /
 * remove / auto-generate) plus the nested add/edit-field sheet.
 *
 * The fields are edited on a draft owned by [ExtractDataViewModel]; they reach the rule only when the
 * user confirms (Add/Update). Cancelling or dismissing discards the draft. This mirrors the
 * MatchingLogic sub-sheet: the ViewModel emits [ExtractDataContract.UiEffect.Committed]/`Dismiss`,
 * which this composable maps to [onCommitted]/[onDismiss] for the parent.
 *
 * @param initialFields seed for the draft (empty for a new action, the action's fields when editing)
 * @param isEditingAction true when editing an existing Extract-data action (drives Add vs Update)
 * @param notification sample notification used for field preview and auto-generate
 * @param onCommitted called with the draft fields when the user confirms
 * @param onDismiss called when the sheet should be dismissed (discarding the draft)
 */
@Composable
fun ExtractDataBottomSheet(
    initialFields: List<RuleField>,
    isEditingAction: Boolean,
    notification: Notification?,
    onCommitted: (List<RuleField>) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: ExtractDataViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(
            UiEvent.Init(
                initialFields = initialFields,
                isEditingAction = isEditingAction,
                sampleText = notification?.let { it.content ?: it.title ?: it.rawContent },
            ),
        )
    }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is ExtractDataContract.UiEffect.Committed -> onCommitted(effect.fields)
            is ExtractDataContract.UiEffect.Dismiss -> onDismiss()
            // Non-fatal auto-generate feedback; no UI surface here, matching the prior behavior.
            is ExtractDataContract.UiEffect.ShowError -> Unit
        }
    }

    ActionConfigSheet(
        title = "Extract data",
        confirmLabel = confirmLabelFor(uiState.isEditingAction),
        // A null onConfirm disables the button while the draft has no fields.
        onConfirm = if (uiState.canConfirm) {
            { viewModel.onEvent(UiEvent.OnConfirm) }
        } else {
            null
        },
        onDismiss = { viewModel.onEvent(UiEvent.OnDismiss) },
    ) {
        ActionSheetDescription("Extract and store data fields from the notification.")
        DataExtractionSection(
            fields = uiState.fields,
            onEvent = viewModel::onEvent,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Nested add/edit-field sheet, driven by the same ViewModel's draft.
    if (uiState.isFieldSheetVisible) {
        AddFieldBottomSheet(
            fieldToEdit = uiState.editingField,
            notification = notification,
            onFieldSaved = { viewModel.onEvent(UiEvent.OnFieldSaved(it)) },
            onDismiss = { viewModel.onEvent(UiEvent.OnDismissFieldSheet) },
        )
    }
}

/**
 * The data-extraction field manager, hosted inside the Extract-data sheet. Shows the extraction
 * fields with add/edit/remove and an auto-generate shortcut. Field actions are routed to the sheet's
 * ViewModel via [onEvent].
 */
@Composable
private fun DataExtractionSection(
    fields: List<RuleField>,
    onEvent: (UiEvent) -> Unit,
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
                onClick = { onEvent(UiEvent.OnAutoGenerate) },
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
                onClick = { onEvent(UiEvent.OnEditFieldClicked(field.id)) },
                onRemove = { onEvent(UiEvent.OnRemoveFieldClicked(field.id)) },
            )
        }

        // Add field button (outlined style)
        AddButton(
            text = "Add field",
            onClick = { onEvent(UiEvent.OnAddFieldClicked) },
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
            ExtractionFieldInfo(field = field, modifier = Modifier.weight(1f))

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

/** Leading icon plus the field's name and extraction-method summary. */
@Composable
private fun ExtractionFieldInfo(
    field: RuleField,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(field.iconContainerColor()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = field.icon(),
                contentDescription = null,
                tint = field.iconTint(),
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
}

@Composable
private fun RuleField.iconContainerColor(): Color = when (name.lowercase()) {
    "amount", "price", "total" -> MaterialTheme.colorScheme.tertiaryContainer
    "merchant", "store", "seller" -> MaterialTheme.colorScheme.secondaryContainer
    else -> MaterialTheme.colorScheme.primaryContainer
}

@Composable
private fun RuleField.iconTint(): Color = when (name.lowercase()) {
    "amount", "price", "total" -> MaterialTheme.colorScheme.tertiary
    "merchant", "store", "seller" -> MaterialTheme.colorScheme.secondary
    else -> MaterialTheme.colorScheme.primary
}

private fun RuleField.icon(): ImageVector = when (name.lowercase()) {
    "merchant", "store", "seller" -> Icons.Default.Store
    else -> Icons.Default.Payments
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
            onEvent = {},
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
            onEvent = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
