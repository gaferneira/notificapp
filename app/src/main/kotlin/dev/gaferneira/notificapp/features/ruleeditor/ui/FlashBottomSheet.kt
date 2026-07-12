package dev.gaferneira.notificapp.features.ruleeditor.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.gaferneira.notificapp.domain.model.ActionType
import dev.gaferneira.notificapp.domain.model.DEFAULT_FLASH_COUNT
import dev.gaferneira.notificapp.domain.model.DEFAULT_FLASH_DURATION_MS
import dev.gaferneira.notificapp.domain.model.RuleAction
import dev.gaferneira.notificapp.domain.model.getFlashCount
import dev.gaferneira.notificapp.domain.model.getFlashDurationMs
import dev.gaferneira.notificapp.features.ruleeditor.domain.ui
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionConfigSheet
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.ActionSheetDescription
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.FlashOptionsSelector
import dev.gaferneira.notificapp.features.ruleeditor.ui.components.confirmLabelFor
import java.util.UUID

/**
 * Type-scoped sheet for the Flash alert action. Owns its own flash-count/duration state; on confirm
 * it builds a `FLASH_ALERT` [RuleAction] and hands it back.
 *
 * @param initial The action being edited, or null when adding a new one
 */
@Composable
fun FlashBottomSheet(
    initial: RuleAction?,
    onSave: (RuleAction) -> Unit,
    onDismiss: () -> Unit,
) {
    var flashCount by remember { mutableIntStateOf(initial?.getFlashCount() ?: DEFAULT_FLASH_COUNT) }
    var flashDurationMs by remember {
        mutableLongStateOf(initial?.getFlashDurationMs() ?: DEFAULT_FLASH_DURATION_MS)
    }

    ActionConfigSheet(
        title = "Flash alert",
        confirmLabel = confirmLabelFor(isEdit = initial != null),
        onConfirm = {
            onSave(
                RuleAction.createFlashAlert(
                    id = initial?.id ?: UUID.randomUUID().toString(),
                    flashCount = flashCount,
                    flashDurationMs = flashDurationMs,
                    isEnabled = initial?.isEnabled ?: true,
                ),
            )
        },
        onDismiss = onDismiss,
    ) {
        ActionSheetDescription(ActionType.FLASH_ALERT.ui().description)
        FlashOptionsSelector(
            flashCount = flashCount,
            flashDurationMs = flashDurationMs,
            onFlashCountChange = { flashCount = it },
            onFlashDurationChange = { flashDurationMs = it },
        )
    }
}
