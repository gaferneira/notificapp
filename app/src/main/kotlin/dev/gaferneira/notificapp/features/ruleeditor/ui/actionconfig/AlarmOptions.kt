package dev.gaferneira.notificapp.features.ruleeditor.ui.actionconfig

/**
 * Current alarm configuration shown in [AlarmOptionsSelector].
 *
 * @property soundUri Selected alarm sound URI, or null for the device default
 * @property vibrationEnabled Whether the alarm vibrates
 * @property fullScreenEnabled Whether the alarm shows the full-screen, call-style UI
 */
data class AlarmOptions(
    val soundUri: String?,
    val vibrationEnabled: Boolean,
    val fullScreenEnabled: Boolean,
)
