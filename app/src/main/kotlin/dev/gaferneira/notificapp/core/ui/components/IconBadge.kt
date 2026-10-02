package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.then
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappStyles
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/**
 * A circular tonal container around an icon, replacing the repeated
 * `Surface(shape = CircleShape, color = ...)` ad-hoc tonal-color pattern.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    style: Style = Style,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    iconSize: Dp = 20.dp,
) {
    val styleState = remember { MutableStyleState(null) }
    Box(
        modifier = modifier.styleable(styleState, NotificappStyles.iconBadgeStyle then style),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IconBadgePreview() {
    NotificappTheme(dynamicColor = false) {
        IconBadge(icon = Icons.Default.Notifications)
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IconBadgePreviewDark() {
    NotificappTheme(dynamicColor = false) {
        IconBadge(icon = Icons.Default.Notifications)
    }
}
