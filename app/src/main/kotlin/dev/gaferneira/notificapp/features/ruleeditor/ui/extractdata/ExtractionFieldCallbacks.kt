package dev.gaferneira.notificapp.features.ruleeditor.ui.extractdata

import androidx.compose.runtime.Stable

/**
 * Callbacks for the extraction field manager, grouped so the hosting composables stay within a
 * reasonable parameter count.
 */
@Stable
class ExtractionFieldCallbacks(
    val onAutoGenerate: () -> Unit,
    val onAddField: () -> Unit,
    val onEditField: (String) -> Unit,
    val onRemoveField: (String) -> Unit,
)
