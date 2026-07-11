package dev.gaferneira.notificapp.domain.model

/**
 * How a ringing `CREATE_ALARM` action's full-screen UI background is sourced.
 */
enum class AlarmBackgroundType {
    /** Use the current app theme background (today's behavior, and the default). */
    NONE,

    /** Use a built-in [AlarmBackgroundPreset] color/gradient swatch. */
    PRESET,

    /** Use a custom image persisted via `ALARM_BACKGROUND_IMAGE_URI_KEY`. */
    IMAGE,
    ;

    companion object {
        /**
         * Resolve a persisted type [name], falling back to [NONE] when `name` is `null` or does
         * not match any known type (e.g. a stale or malformed imported rule).
         */
        fun fromName(name: String?): AlarmBackgroundType = entries.firstOrNull { it.name == name } ?: NONE
    }
}

/**
 * Built-in color/gradient swatch for the alarm full-screen background.
 *
 * Carries only flat primitive ARGB hex strings for its gradient stops — no `Brush`/`Color` or
 * other Compose type — keeping the domain layer Android-free. The UI layer is responsible for
 * converting [colorHexStops] into a `Brush` (e.g. an `AlarmBackgroundPreset.toBrush()` extension
 * local to the UI, not in domain).
 *
 * @property id Stable identifier persisted in [RuleAction] config, surviving enum reordering.
 * @property colorHexStops Gradient color stops, as `#AARRGGBB` or `#RRGGBB` hex strings, in
 * render order.
 * @property isDark Whether this gradient reads as visually dark, so the ringing alarm UI should
 * render its title/text in light/white rather than following the system light/dark theme (e.g.
 * [MIDNIGHT] is dark even when the system is in light theme, where `onSurface` would otherwise
 * resolve to dark text on a dark gradient). Hardcoded per entry rather than computed from
 * [colorHexStops] — a perceptual "is this dark" judgment doesn't reduce cleanly to an average of
 * the stops for a multi-stop gradient, so it's a deliberate per-preset call instead.
 */
enum class AlarmBackgroundPreset(
    val id: String,
    val colorHexStops: List<String>,
    val isDark: Boolean,
) {
    SUNRISE(id = "sunrise", colorHexStops = listOf("#FF6B35", "#F7C59F", "#FFF1D0"), isDark = false),
    OCEAN(id = "ocean", colorHexStops = listOf("#0B486B", "#3B8686", "#79BD9A"), isDark = true),
    MIDNIGHT(id = "midnight", colorHexStops = listOf("#0F0C29", "#302B63", "#24243E"), isDark = true),
    FOREST(id = "forest", colorHexStops = listOf("#0B3D2E", "#145C45", "#2E8B57"), isDark = true),
    LAVENDER(id = "lavender", colorHexStops = listOf("#E0BBE4", "#C9A0DC", "#D8B4E2"), isDark = false),
    CORAL(id = "coral", colorHexStops = listOf("#FF9A8B", "#FF6A88", "#FF99AC"), isDark = false),
    ;

    companion object {
        /** Fallback preset used when no other preset was selected or resolvable. */
        val DEFAULT: AlarmBackgroundPreset = SUNRISE

        /**
         * Resolve a persisted preset [id] to its [AlarmBackgroundPreset], falling back to
         * [DEFAULT] when [id] is `null` or does not match any known preset (e.g. a stale id from
         * an old export, a preset removed in a later version, or `backgroundType == PRESET` with
         * no preset ever picked).
         */
        fun fromId(id: String?): AlarmBackgroundPreset = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
