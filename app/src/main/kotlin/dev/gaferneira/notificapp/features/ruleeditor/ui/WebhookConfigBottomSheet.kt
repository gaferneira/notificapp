package dev.gaferneira.notificapp.features.ruleeditor.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.HttpMethod
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.domain.model.WEBHOOK_ALL_BUILTINS
import dev.gaferneira.notificapp.domain.model.WEBHOOK_FIELD_ID_PREFIX
import dev.gaferneira.notificapp.domain.model.Webhook
import dev.gaferneira.notificapp.domain.model.WebhookPayloadMode
import dev.gaferneira.notificapp.features.ruleeditor.contract.WebhookConfigContract.UiEffect
import dev.gaferneira.notificapp.features.ruleeditor.contract.WebhookConfigContract.UiEvent
import dev.gaferneira.notificapp.features.ruleeditor.contract.WebhookConfigContract.UiState
import dev.gaferneira.notificapp.features.ruleeditor.domain.WebhookConfigUiModel
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.confirmLabelFor
import dev.gaferneira.notificapp.features.ruleeditor.viewmodel.WebhookConfigViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Type-scoped sheet for the Send Webhook action: pick (or inline-create) a target webhook, choose
 * a payload-authoring mode (field checklist or custom JSON template), and preview the resulting
 * payload. State lives in [WebhookConfigViewModel]; the action is only committed to the rule when
 * the sheet's Add/Update button is tapped (per the rule-action-authoring spec's "config committed
 * only on confirm" requirement).
 *
 * @param initial The action being edited, or null when adding a new one
 * @param ruleFields The rule's currently-defined extraction fields, for the checklist/insert-field chips
 */
@Composable
fun WebhookConfigBottomSheet(
    initial: RuleAction?,
    ruleFields: ImmutableList<RuleField>,
    onSave: (RuleAction) -> Unit,
    onDismiss: () -> Unit,
    viewModel: WebhookConfigViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.onEvent(UiEvent.Initialize(initial, ruleFields)) }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.ConfirmSave -> onSave(effect.action)
        }
    }

    ActionConfigSheet(
        modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
        title = "Send webhook",
        confirmLabel = confirmLabelFor(isEdit = initial != null),
        onConfirm = if (uiState.canConfirm) {
            { viewModel.onEvent(UiEvent.OnConfirmClicked) }
        } else {
            null
        },
        onDismiss = onDismiss,
    ) {
        WebhookConfigSheetBody(uiState = uiState, onEvent = viewModel::onEvent)
    }
}

/**
 * The sheet's scrollable body, split out of [WebhookConfigBottomSheet] so it can be rendered with
 * a fixed [UiState] - without a [WebhookConfigViewModel] - for [WebhookConfigBottomSheetPreview].
 */
@Composable
private fun WebhookConfigSheetBody(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        ActionSheetDescription(ActionType.SEND_WEBHOOK.ui().description)

        WebhookPickerSection(uiState = uiState, onEvent = onEvent)

        Spacer(modifier = Modifier.height(20.dp))

        if (uiState.selectedWebhookMethod == HttpMethod.GET) {
            Text(
                text = "This webhook uses GET — no payload is sent. Use query parameters on the webhook to pass data.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            PayloadModeToggle(mode = uiState.config.mode, onEvent = onEvent)

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState.config.mode) {
                WebhookPayloadMode.FIELDS -> FieldChecklistSection(uiState = uiState, onEvent = onEvent)
                WebhookPayloadMode.TEMPLATE -> TemplateEditorSection(uiState = uiState, onEvent = onEvent)
            }

            Spacer(modifier = Modifier.height(16.dp))

            PreviewSection(uiState = uiState, onEvent = onEvent)
        }
    }
}

/** Existing-webhook radio list plus a "New webhook" row that navigates to `WebhookEditorScreen`. */
@Composable
private fun WebhookPickerSection(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Webhook",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(8.dp))

        uiState.webhooks.forEach { webhook ->
            WebhookPickerRow(
                webhook = webhook,
                selected = webhook.id == uiState.config.webhookId,
                onClick = { onEvent(UiEvent.OnWebhookSelected(webhook.id)) },
            )
        }

        OutlinedButton(
            onClick = { onEvent(UiEvent.OnCreateWebhookClicked) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("New webhook")
        }
    }
}

@Composable
private fun WebhookPickerRow(
    webhook: Webhook,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column {
            Text(text = webhook.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = webhook.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayloadModeToggle(
    mode: WebhookPayloadMode,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = mode == WebhookPayloadMode.FIELDS,
            onClick = { onEvent(UiEvent.OnModeChanged(WebhookPayloadMode.FIELDS)) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) {
            Text("Fields")
        }
        SegmentedButton(
            selected = mode == WebhookPayloadMode.TEMPLATE,
            onClick = { onEvent(UiEvent.OnModeChanged(WebhookPayloadMode.TEMPLATE)) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) {
            Text("Template")
        }
    }
}

/** FIELDS-mode checklist: built-in tokens plus the rule's extraction fields (or an empty-state hint). */
@Composable
private fun FieldChecklistSection(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Notification fields",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        WEBHOOK_ALL_BUILTINS.forEach { token ->
            ChecklistRow(
                label = token,
                checked = token in uiState.config.selectedBuiltins,
                onCheckedChange = { checked -> onEvent(UiEvent.OnBuiltinToggled(token, checked)) },
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Extracted fields",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (uiState.ruleFields.isEmpty()) {
            Text(
                text = "No extracted fields yet — add an Extract Data action to reference custom fields here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        } else {
            uiState.ruleFields.forEach { field ->
                ChecklistRow(
                    label = field.name,
                    checked = field.id in uiState.config.selectedFieldIds,
                    onCheckedChange = { checked -> onEvent(UiEvent.OnFieldToggled(field.id, checked)) },
                )
            }
        }
    }
}

@Composable
private fun ChecklistRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * TEMPLATE-mode JSON body editor with an "Insert field" chip row that inserts `{{token}}` at the
 * text field's current cursor position - the primary authoring path (task 5.5).
 */
@Composable
private fun TemplateEditorSection(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var fieldValue by remember(uiState.actionId, uiState.isEditing) {
        val text = uiState.config.template
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Insert field",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(4.dp))

        InsertFieldChipRow(
            ruleFields = uiState.ruleFields,
            onTokenSelected = { token ->
                val insertion = "{{$token}}"
                val selection = fieldValue.selection
                val newText = fieldValue.text.replaceRange(selection.start, selection.end, insertion)
                fieldValue = TextFieldValue(text = newText, selection = TextRange(selection.start + insertion.length))
                onEvent(UiEvent.OnTemplateChanged(newText))
            },
        )

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = fieldValue,
            onValueChange = {
                fieldValue = it
                onEvent(UiEvent.OnTemplateChanged(it.text))
            },
            label = { Text("JSON template") },
            placeholder = { Text("""{ "merchant": "{{field.<id>}}" }""") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
        )
    }
}

/** Grouped "Notification" / "Extracted fields" chip row, per task 5.5. */
@Composable
private fun InsertFieldChipRow(
    ruleFields: ImmutableList<RuleField>,
    onTokenSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = remember(ruleFields) {
        (WEBHOOK_ALL_BUILTINS.map { it to it } + ruleFields.map { "$WEBHOOK_FIELD_ID_PREFIX${it.id}" to it.name })
            .toImmutableList()
    }

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = tokens, key = { it.first }) { (token, label) ->
            SuggestionChip(
                onClick = { onTokenSelected(token) },
                label = { Text(label) },
            )
        }
    }
}

/** "Preview payload" button rendering the built JSON, surfacing unknown-token/invalid-JSON warnings. */
@Composable
private fun PreviewSection(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedButton(
            onClick = { onEvent(UiEvent.OnPreviewClicked) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Preview payload")
        }

        val previewJson = uiState.previewJson
        if (previewJson != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Payload preview",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        IconButton(
                            onClick = { onEvent(UiEvent.OnDismissPreview) },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss preview",
                            )
                        }
                    }
                    Text(text = previewJson, style = MaterialTheme.typography.bodySmall)
                    uiState.previewWarning?.let { warning ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = warning,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun WebhookPickerSectionPreview() {
    NotificappTheme {
        WebhookPickerSection(
            uiState = UiState(
                webhooks = persistentListOf(
                    Webhook(name = "Home Assistant", url = "http://homeassistant.local:8123/api/webhook/abc"),
                ),
            ),
            onEvent = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FieldChecklistSectionEmptyPreview() {
    NotificappTheme {
        FieldChecklistSection(
            uiState = UiState(),
            onEvent = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FieldChecklistSectionPreview() {
    NotificappTheme {
        FieldChecklistSection(
            uiState = UiState(
                ruleFields = persistentListOf(
                    RuleField(id = "1", name = "Merchant", method = ExtractionMethod.LineExtraction(10)),
                    RuleField(id = "2", name = "Amount", method = ExtractionMethod.RegexPattern("\\d+(\\.\\d+)?")),
                ),
            ),
            onEvent = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

private fun previewSheetUiState() = UiState(
    webhooks = persistentListOf(
        Webhook(name = "Home Assistant", url = "http://homeassistant.local:8123/api/webhook/abc"),
    ),
    config = WebhookConfigUiModel(
        webhookId = "1",
        selectedBuiltins = setOf(WEBHOOK_ALL_BUILTINS.first()),
    ),
    ruleFields = persistentListOf(
        RuleField(id = "1", name = "Merchant", method = ExtractionMethod.LineExtraction(10)),
    ),
)

@Preview(showBackground = true, name = "Send webhook sheet")
@Composable
private fun WebhookConfigBottomSheetPreview() {
    NotificappTheme {
        ActionConfigSheet(
            modifier = Modifier.fillMaxWidth(),
            title = "Send webhook",
            confirmLabel = confirmLabelFor(isEdit = false),
            onConfirm = {},
            onDismiss = {},
        ) {
            WebhookConfigSheetBody(uiState = previewSheetUiState(), onEvent = {})
        }
    }
}

@Preview(showBackground = true, name = "Send webhook sheet - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WebhookConfigBottomSheetPreviewDark() {
    NotificappTheme {
        ActionConfigSheet(
            modifier = Modifier.fillMaxWidth(),
            title = "Send webhook",
            confirmLabel = confirmLabelFor(isEdit = false),
            onConfirm = {},
            onDismiss = {},
        ) {
            WebhookConfigSheetBody(uiState = previewSheetUiState(), onEvent = {})
        }
    }
}
