package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    title: String,
    confirmLabel: String,
    onConfirm: (() -> Unit)?,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        modifier = Modifier.statusBarsPadding(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(scrollState),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(16.dp))

            content()

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = { onConfirm?.invoke() },
                    enabled = onConfirm != null,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(confirmLabel)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
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
internal fun confirmLabelFor(isEdit: Boolean): String = if (isEdit) "Update" else "Add action"
