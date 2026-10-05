package dev.gaferneira.notificapp.features.ruleeditor.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.BetaBadge
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.RuleField
import dev.gaferneira.notificapp.domain.model.RuleField.ExtractionMethod
import dev.gaferneira.notificapp.domain.model.getSendReplyTemplate
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.TemplateFieldChipRow
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.confirmLabelFor
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.util.UUID

/**
 * Type-scoped sheet for the Send reply (BETA) action. Mirrors [ReadAloudBottomSheet]'s
 * self-contained shape - no dedicated ViewModel/Contract - reusing [TemplateFieldChipRow] for the
 * same `{{token}}` placeholder syntax. Surfaces a [BetaBadge] plus a one-line notice: this action
 * only works on apps that expose an Android direct-reply action, and silently does nothing
 * otherwise (surfaced as a SKIPPED outcome in the notification detail, never a crash).
 *
 * @param initial The action being edited, or null when adding a new one
 * @param ruleFields The rule's currently-defined extraction fields, for the insert-field chips
 */
@Composable
fun SendReplyBottomSheet(
    initial: RuleAction?,
    ruleFields: ImmutableList<RuleField>,
    onSave: (RuleAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var fieldValue by remember {
        val text = initial?.getSendReplyTemplate().orEmpty()
        mutableStateOf(TextFieldValue(text = text, selection = TextRange(text.length)))
    }

    ActionConfigSheet(
        title = stringResource(R.string.action_type_send_reply_label),
        confirmLabel = confirmLabelFor(isEdit = initial != null),
        onConfirm = if (fieldValue.text.isNotBlank()) {
            {
                onSave(
                    RuleAction.createSendReply(
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
        SendReplyHeader()

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.rule_editor_insert_field),
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
                label = { Text(stringResource(R.string.send_reply_text)) },
                placeholder = { Text(stringResource(R.string.send_reply_text_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }
}

/** Beta badge, description, and best-effort notice shown at the top of the sheet. */
@Composable
private fun SendReplyHeader() {
    BetaBadge()
    Spacer(modifier = Modifier.height(8.dp))

    ActionSheetDescription(stringResource(ActionType.SEND_REPLY.ui().descriptionRes))

    Text(
        text = stringResource(R.string.send_reply_best_effort_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(16.dp))
}

@Preview(showBackground = true, name = "Send reply sheet")
@Preview(showBackground = true, name = "Send reply sheet - Font 2x", fontScale = 2f)
@Preview(showBackground = true, name = "Send reply sheet - Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SendReplyBottomSheetPreview() {
    NotificappTheme {
        ActionConfigSheet(
            title = "Send reply",
            confirmLabel = confirmLabelFor(isEdit = false),
            onConfirm = {},
            onDismiss = {},
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BetaBadge()
            }
            Spacer(modifier = Modifier.height(8.dp))
            ActionSheetDescription(stringResource(ActionType.SEND_REPLY.ui().descriptionRes))
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
