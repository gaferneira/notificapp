package dev.gaferneira.notificapp.core.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.StyleScope
import androidx.compose.ui.unit.dp

/** Reads the current [AppTokens] from within a [Style] block. */
val StyleScope.tokens: AppTokens
    get() = LocalAppTokens.currentValue

/**
 * Component-level [Style] definitions for the `core/ui/components` package, built from
 * [AppTokens] instead of the ad-hoc `dp`/`.copy(alpha = ...)` literals that used to be
 * scattered across screens.
 */
object NotificappStyles {
    val tonalCardStyle: Style = Style {
        background(tokens.cardContainer)
        shape(tokens.shapes.large)
        contentPadding(tokens.spacing.lg)
    }

    val innerPanelStyle: Style = Style {
        background(tokens.colorScheme.surface)
        shape(tokens.shapes.medium)
        contentPadding(tokens.spacing.md)
    }

    val iconBadgeStyle: Style = Style {
        background(tokens.iconBadgeContainerStrong)
        shape(CircleShape)
        size(40.dp)
    }

    val iconBadgeSubtleStyle: Style = Style {
        background(tokens.iconBadgeContainerSubtle)
        shape(CircleShape)
        size(40.dp)
    }

    val statusPillStyle: Style = Style {
        background(tokens.colorScheme.surface)
        shape(tokens.shapes.large)
        contentPadding(horizontal = tokens.spacing.sm + 2.dp, vertical = tokens.spacing.xs + 2.dp)
    }

    val pagerDotSelectedStyle: Style = Style {
        background(tokens.colorScheme.primary)
        shape(CircleShape)
        size(8.dp)
    }

    val pagerDotUnselectedStyle: Style = Style {
        background(tokens.inactiveIndicator)
        shape(CircleShape)
        size(6.dp)
    }
}
