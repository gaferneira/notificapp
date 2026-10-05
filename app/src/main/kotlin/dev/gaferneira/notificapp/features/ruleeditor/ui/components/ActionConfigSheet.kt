package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R

/**
 * Shared scaffold for the type-scoped action configuration sheets (snooze, alarm, flash, extract
 * data). Each type has its own sheet composable that owns its state and validation; this scaffold
 * only provides the common chrome - title, scroll, and the Cancel/confirm buttons - so per-type logic
 * stays isolated in its own file. Sheets render their own supporting copy with [ActionSheetDescription].
 *
 * Pass a null [onConfirm] to render the confirm button disabled (e.g. while the sheet's input is
 * incomplete).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionConfigSheet(
    modifier: Modifier = Modifier,
    title: String,
    confirmLabel: String,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        modifier = modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val scrollState = rememberScrollState()
        // Title and fields scroll; the buttons stay pinned so they remain reachable with the
        // keyboard open (the body is lifted above it by sheetInsetsPadding).
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .sheetInsetsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.semantics { heading() },
                )

                Spacer(modifier = Modifier.height(16.dp))

                content()

                Spacer(modifier = Modifier.height(24.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.rule_editor_action_cancel))
                }

                Button(
                    onClick = { onConfirm?.invoke() },
                    enabled = onConfirm != null,
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

/** Supporting copy shown under an action sheet's title. */
@Composable
fun ActionSheetDescription(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(24.dp))
}

/** Confirm-button label for an add vs. edit flow. */
@Composable
internal fun confirmLabelFor(isEdit: Boolean): String = stringResource(if (isEdit) R.string.rule_editor_action_update else R.string.rule_editor_action_add_action)
