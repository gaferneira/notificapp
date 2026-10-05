package dev.gaferneira.notificapp.core.ui.components

/**
 * One app entry of [AppFilterSection] / [AppPickerSheet].
 *
 * @property supportingText Optional secondary line (e.g. "3 rules"), already localized by the caller
 */
data class AppPickerOption(
    val packageName: String,
    val name: String,
    val supportingText: String? = null,
)

/** A titled group of apps in [AppPickerSheet] (e.g. "Used by rules"). Empty groups are not shown. */
data class AppPickerGroup(
    val title: String,
    val apps: List<AppPickerOption>,
)
