package dev.gaferneira.notificapp.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale shared by `core/ui` components, replacing ad-hoc dp literals. */
@Immutable
data class AppSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 24.dp,
    val xxl: Dp = 32.dp,
)

/**
 * Design-system tokens exposed to [androidx.compose.foundation.style.Style] blocks, which run
 * outside a `@Composable` context and can only read values via `CompositionLocal.currentValue`
 * (Material's own `LocalColorScheme` isn't public API, hence this small wrapper).
 */
@Immutable
data class AppTokens(
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val spacing: AppSpacing,
) {
    /** Tonal container used by cards (`HighlightCard`, `PermissionToggleCard`). */
    val cardContainer: Color get() = colorScheme.surfaceVariant.copy(alpha = 0.3f)

    /** Tonal container used by the "Privacy First" badge. */
    val badgeContainer: Color get() = colorScheme.surfaceVariant.copy(alpha = 0.5f)

    /** Strong icon-badge container, e.g. carousel highlight icons. */
    val iconBadgeContainerStrong: Color get() = colorScheme.primary.copy(alpha = 0.2f)

    /** Subtle icon-badge container, e.g. permission detail rows. */
    val iconBadgeContainerSubtle: Color get() = colorScheme.primary.copy(alpha = 0.1f)

    /** Muted body text on a background surface. */
    val mutedOnBackground: Color get() = colorScheme.onBackground.copy(alpha = 0.7f)

    /** Inactive pager-dot color. */
    val inactiveIndicator: Color get() = colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
}

val LocalAppTokens = staticCompositionLocalOf<AppTokens> {
    error("NotificappTokens not provided — wrap content in NotificappTheme")
}
