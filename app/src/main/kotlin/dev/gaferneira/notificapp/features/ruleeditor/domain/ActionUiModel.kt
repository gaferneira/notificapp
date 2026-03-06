package dev.gaferneira.notificapp.features.ruleeditor.domain

/**
 * UI model for an action in the list.
 */
data class ActionUiModel(val id: String, val type: ActionType, val isEnabled: Boolean = true) {
    val displayName: String
        get() = when (type) {
            ActionType.SAVE_DATA -> "Save to Data tab"
            ActionType.DELETE_NOTIFICATION -> "Delete notification"
            ActionType.CREATE_ALARM -> "Create alarm"
        }
}
