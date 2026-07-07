package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import androidx.compose.runtime.Stable

/**
 * Callbacks for an action card, grouped so `DoSection`/`ActionCard` stay within a reasonable
 * parameter count. Ids are supplied by the section from the action being rendered.
 */
@Stable
class ActionCardCallbacks(
    val onToggle: (String, Boolean) -> Unit,
    val onRemove: (String) -> Unit,
    val onEdit: (String) -> Unit,
)
