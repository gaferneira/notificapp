package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.DEFAULT_SNOOZE_DURATION_MINUTES
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.actionconfig.SnoozeDurationSelector
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.confirmLabelFor
import java.util.UUID

/**
 * Type-scoped sheet for the Snooze action. Owns its own duration state; on confirm it builds a
 * `SNOOZE_NOTIFICATION` [RuleAction] and hands it back. No shared action ViewModel is involved.
 *
 * @param initial The action being edited, or null when adding a new one
 */
@Composable
fun SnoozeBottomSheet(
    initial: RuleAction?,
    onSave: (RuleAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember {
        mutableIntStateOf(initial?.getSnoozeDurationMinutes() ?: DEFAULT_SNOOZE_DURATION_MINUTES)
    }

    ActionConfigSheet(
        title = "Snooze notification",
        confirmLabel = confirmLabelFor(isEdit = initial != null),
        onConfirm = {
            onSave(
                RuleAction.createSnooze(
                    id = initial?.id ?: UUID.randomUUID().toString(),
                    durationMinutes = minutes,
                    isEnabled = initial?.isEnabled ?: true,
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        ActionSheetDescription(ActionType.SNOOZE_NOTIFICATION.ui().description)
        SnoozeDurationSelector(
            selectedMinutes = minutes,
            onDurationChange = { minutes = it },
        )
    }
}
