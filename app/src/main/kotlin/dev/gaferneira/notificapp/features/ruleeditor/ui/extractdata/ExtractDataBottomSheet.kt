package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.Notification
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.PreviewResult
import dev.gaferneira.notificapp.features.ruleeditor.contract.ExtractDataContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.AddButton
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ExtractedField
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.NotificationPreviewCard
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.confirmLabelFor
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.ExtractDataViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

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
    initialFields: ImmutableList<RuleField>,
    isEditingAction: Boolean,
    targetPackages: List<String>?,
    notification: Notification?,
    onCommitted: (ImmutableList<RuleField>) -> Unit,
    onDismiss: () -> Unit,
) {
    val viewModel: ExtractDataViewModel = hiltViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val effectiveNotification = notification ?: uiState.overrideNotification

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
        ActionSheetDescription(ActionType.SAVE_DATA.ui().description)
        DataExtractionSection(
            entryNotification = notification,
            effectiveNotification = effectiveNotification,
            targetPackages = targetPackages,
            uiState = uiState,
            onEvent = viewModel::onEvent,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    // Nested add/edit-field sheet, driven by the same ViewModel's draft.
    if (uiState.isFieldSheetVisible) {
        AddFieldBottomSheet(
            fieldToEdit = uiState.editingField,
            notification = effectiveNotification,
            onFieldSaved = { viewModel.onEvent(UiEvent.OnFieldSaved(it)) },
            onDismiss = { viewModel.onEvent(UiEvent.OnDismissFieldSheet) },
        )
    }
}

/**
 * The data-extraction field manager, hosted inside the Extract-data sheet. Shows the extraction
 * fields with add/edit/remove, an auto-generate shortcut, and (when there is no entry-flow sample)
 * a browse-history affordance that lets the user pick a preview-only override notification. Field
 * and history actions are routed to the sheet's ViewModel via [onEvent].
 */
@Composable
private fun DataExtractionSection(
    entryNotification: Notification?,
    effectiveNotification: Notification?,
    targetPackages: List<String>? = null,
    uiState: ExtractDataContract.UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DataExtractionHeaderRow(effectiveNotification = effectiveNotification, targetPackages, onEvent = onEvent)

        PreviewOrHistorySlot(
            entryNotification = entryNotification,
            effectiveNotification = effectiveNotification,
            uiState = uiState,
            onEvent = onEvent,
        )

        // Field cards
        uiState.fields.forEach { field ->
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

/** Description text plus the mutually-exclusive auto-generate / browse-history shortcut. */
@Composable
private fun DataExtractionHeaderRow(
    effectiveNotification: Notification?,
    targetPackages: List<String>? = null,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
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

        if (effectiveNotification != null) {
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
        } else {
            BrowseHistoryIconButton(onClick = {
                onEvent(UiEvent.OnBrowseHistoryOpened(targetPackages))
            })
        }
    }
}

/**
 * Preview / history slot: entry-sample-or-override preview card, the inline history list, or
 * nothing when there's no sample and the list is collapsed.
 */
@Composable
private fun PreviewOrHistorySlot(
    entryNotification: Notification?,
    effectiveNotification: Notification?,
    uiState: ExtractDataContract.UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        effectiveNotification != null && !uiState.isBrowsingHistory -> {
            val showClear = entryNotification == null && uiState.overrideNotification != null
            SampleNotificationPreview(
                notification = effectiveNotification,
                fields = uiState.fields,
                previewResults = uiState.previewResults,
                onClear = if (showClear) ({ onEvent(UiEvent.OnOverrideCleared) }) else null,
                modifier = modifier,
            )
        }
        uiState.isBrowsingHistory -> {
            HistoryNotificationList(
                isLoading = uiState.isLoadingHistory,
                results = uiState.historyResults,
                onNotificationSelected = { onEvent(UiEvent.OnHistoryNotificationSelected(it)) },
                modifier = modifier,
            )
        }
        else -> Unit
    }
}

/**
 * Resolves each field's extraction result against [notification] and renders the shared preview
 * card. When [onClear] is non-null (an override notification is selected), an "X" icon is overlaid
 * in the card's top-right corner; [NotificationPreviewCard] itself is never modified since it's
 * shared with [dev.gaferneira.notificapp.features.ruleeditor.ui.BacktestResultsBottomSheet].
 */
@Composable
private fun SampleNotificationPreview(
    notification: Notification,
    fields: ImmutableList<RuleField>,
    previewResults: Map<String, PreviewResult>,
    onClear: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val noMatchLabel = stringResource(R.string.extract_preview_no_match)
    val extractedFields = remember(fields, previewResults, noMatchLabel) {
        fields.mapNotNull { field ->
            when (val result = previewResults[field.id]) {
                is PreviewResult.Success -> ExtractedField(field.name, result.value)
                is PreviewResult.Failure -> ExtractedField(field.name, noMatchLabel)
                null -> null
            }
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        NotificationPreviewCard(
            appName = notification.appName,
            title = notification.title,
            content = notification.content,
            extractedFields = extractedFields,
            modifier = Modifier.fillMaxWidth(),
        )
        if (onClear != null) {
            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.extract_history_clear_override_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Icon button shown in the auto-generate slot when there is no entry sample and no override. */
@Composable
private fun BrowseHistoryIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.primaryContainer),
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = stringResource(R.string.extract_history_open_cd),
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * Bounded, unfiltered recent-notification picker shown inline in the preview slot. No query field:
 * [isLoading] drives a spinner, an empty [results] list (once loaded) shows an empty-history
 * message, otherwise each row reuses [NotificationPreviewCard] and is selectable.
 */
@Composable
private fun HistoryNotificationList(
    isLoading: Boolean,
    results: List<Notification>,
    onNotificationSelected: (Notification) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        isLoading -> {
            Box(modifier = modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }
        results.isEmpty() -> {
            Text(
                text = stringResource(R.string.extract_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = modifier.fillMaxWidth().padding(16.dp),
            )
        }
        else -> {
            LazyColumn(
                modifier = modifier.fillMaxWidth().heightIn(max = 240.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(results) { result ->
                    NotificationPreviewCard(
                        appName = result.appName,
                        title = result.title,
                        content = result.content,
                        extractedFields = emptyList(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNotificationSelected(result) },
                    )
                }
            }
        }
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
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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

private val previewNotification = Notification(
    id = "1",
    packageName = "com.example.bank",
    appName = "Bank App",
    title = "Payment received",
    content = "ICA Kvantum charged you 153.50 kr",
    rawContent = "ICA Kvantum charged you 153.50 kr",
    timestamp = 0L,
)

@Preview(showBackground = true)
@Composable
private fun DataExtractionSectionPreview() {
    NotificappTheme {
        DataExtractionSection(
            entryNotification = previewNotification,
            effectiveNotification = previewNotification,
            uiState = ExtractDataContract.UiState(
                fields = persistentListOf(
                    RuleField(id = "1", name = "Merchant", method = ExtractionMethod.LineExtraction(10)),
                    RuleField(id = "2", name = "Amount", method = ExtractionMethod.RegexPattern("\\d+(\\.\\d+)?")),
                ),
                previewResults = mapOf(
                    "1" to PreviewResult.Success(value = "ICA Kvantum"),
                    "2" to PreviewResult.Failure("Pattern did not match"),
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
            entryNotification = null,
            effectiveNotification = null,
            uiState = ExtractDataContract.UiState(),
            onEvent = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Override selected - light", showBackground = true)
@Composable
private fun DataExtractionSectionOverridePreview() {
    NotificappTheme {
        DataExtractionSection(
            entryNotification = null,
            effectiveNotification = previewNotification,
            uiState = ExtractDataContract.UiState(overrideNotification = previewNotification),
            onEvent = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Override selected - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DataExtractionSectionOverrideDarkPreview() {
    NotificappTheme {
        DataExtractionSection(
            entryNotification = null,
            effectiveNotification = previewNotification,
            uiState = ExtractDataContract.UiState(overrideNotification = previewNotification),
            onEvent = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "History loading - light", showBackground = true)
@Composable
private fun HistoryNotificationListLoadingPreview() {
    NotificappTheme {
        HistoryNotificationList(
            isLoading = true,
            results = emptyList(),
            onNotificationSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "History loading - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryNotificationListLoadingDarkPreview() {
    NotificappTheme {
        HistoryNotificationList(
            isLoading = true,
            results = emptyList(),
            onNotificationSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "History results - light", showBackground = true)
@Composable
private fun HistoryNotificationListResultsPreview() {
    NotificappTheme {
        HistoryNotificationList(
            isLoading = false,
            results = listOf(previewNotification, previewNotification.copy(id = "2", title = "Refund issued")),
            onNotificationSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "History results - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryNotificationListResultsDarkPreview() {
    NotificappTheme {
        HistoryNotificationList(
            isLoading = false,
            results = listOf(previewNotification, previewNotification.copy(id = "2", title = "Refund issued")),
            onNotificationSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "History empty - light", showBackground = true)
@Composable
private fun HistoryNotificationListEmptyPreview() {
    NotificappTheme {
        HistoryNotificationList(
            isLoading = false,
            results = emptyList(),
            onNotificationSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "History empty - dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HistoryNotificationListEmptyDarkPreview() {
    NotificappTheme {
        HistoryNotificationList(
            isLoading = false,
            results = emptyList(),
            onNotificationSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
