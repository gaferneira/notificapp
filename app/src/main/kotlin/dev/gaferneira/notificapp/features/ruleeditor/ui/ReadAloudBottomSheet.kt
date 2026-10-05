package dev.gaferneira.notificapp.features.ruleeditor.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.domain.model.getReadAloudTemplate
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.TemplateFieldChipRow
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.confirmLabelFor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

/**
 * Type-scoped sheet for the Read aloud action. Owns its own template text state (mirroring
 * [FlashBottomSheet]'s self-contained shape - no dedicated ViewModel/Contract, since there's no
 * webhook-picker-style external state to load); on confirm it builds a `READ_ALOUD` [RuleAction]
 * and hands it back. Reuses [TemplateFieldChipRow] so the `{{token}}` placeholder syntax is
 * identical to `SEND_WEBHOOK`'s TEMPLATE mode.
 *
 * @param initial The action being edited, or null when adding a new one
 * @param ruleFields The rule's currently-defined extraction fields, for the insert-field chips
 */
@Composable
fun ReadAloudBottomSheet(
    initial: RuleAction?,
    ruleFields: ImmutableList<RuleField>,
    onSave: (RuleAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldValue by remember {
        val text = initial?.getReadAloudTemplate().orEmpty()
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }

    ActionConfigSheet(
        title = "Read aloud",
        confirmLabel = confirmLabelFor(isEdit = initial != null),
        onConfirm = if (fieldValue.text.isNotBlank()) {
            {
                onSave(
                    RuleAction.createReadAloud(
                        id = initial?.id ?: UUID.randomUUID().toString(),
                        template = fieldValue.text,
                        isEnabled = initial?.isEnabled ?: true,
                    ),
                )
            }
        } else {
            null
        },
        onDismiss = onDismiss,
    ) {
        ActionSheetDescription(stringResource(ActionType.READ_ALOUD.ui().descriptionRes))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Insert field",
                style = MaterialTheme.typography.labelLarge,
            )

            TemplateFieldChipRow(
                ruleFields = ruleFields,
                onTokenSelected = { token ->
                    val insertion = "{{$token}}"
                    val selection = fieldValue.selection
                    val newText = fieldValue.text.replaceRange(selection.start, selection.end, insertion)
                    fieldValue = TextFieldValue(text = newText, selection = TextRange(selection.start + insertion.length))
                },
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedTextField(
                value = fieldValue,
                onValueChange = { fieldValue = it },
                label = { Text("Spoken text") },
                placeholder = { Text("Received {{field.<id>}} from {{field.<id>}}") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }
}

@Preview(showBackground = true, name = "Read aloud sheet")
@Preview(showBackground = true, name = "Read aloud sheet - Font 2x", fontScale = 2f)
@Preview(showBackground = true, name = "Read aloud sheet - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ReadAloudBottomSheetPreview() {
    NotificappTheme {
        ActionConfigSheet(
            title = "Read aloud",
            confirmLabel = confirmLabelFor(isEdit = false),
            onConfirm = {},
            onDismiss = {},
        ) {
            ActionSheetDescription(stringResource(ActionType.READ_ALOUD.ui().descriptionRes))
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "Insert field", style = MaterialTheme.typography.labelLarge)
                TemplateFieldChipRow(
                    ruleFields = persistentListOf(
                        RuleField(id = "1", name = "Amount", method = ExtractionMethod.RegexPattern("\\d+(\\.\\d+)?")),
                        RuleField(id = "2", name = "Sender", method = ExtractionMethod.LineExtraction(10)),
                    ),
                    onTokenSelected = {},
                )
            }
        }
    }
}
