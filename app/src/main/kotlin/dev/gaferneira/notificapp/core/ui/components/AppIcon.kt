package dev.gaferneira.notificapp.core.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/**
 * Shows the real launcher icon of [packageName]. When the package is not installed (or its icon
 * cannot be loaded) it falls back to a circular letter avatar built from [appName], or a bell icon
 * when [appName] is blank.
 *
 * Decorative: the app name is expected to be rendered next to it, so it is hidden from semantics.
 */
@Composable
fun AppIcon(packageName: String, appName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val icon: ImageBitmap? = remember(packageName) {
        try {
            context.packageManager.getApplicationIcon(packageName).toBitmap().asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clearAndSetSemantics {},
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            if (appName.isBlank()) {
                Icon(
                    imageVector = Icons.Outlined.Notifications,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            } else {
                Text(
                    text = appName.firstCodePointString().uppercase(Locale.current.platformLocale),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

/** First user-perceived char by code point, so emoji/surrogate pairs are not split. */
private fun String.firstCodePointString(): String = if (isEmpty()) "" else substring(0, offsetByCodePoints(0, 1))

@Preview(showBackground = true)
@Composable
private fun AppIconPreview() {
    NotificappTheme {
        Box {
            AppIcon(packageName = "com.example.missing", appName = "Bank")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppIconBlankPreview() {
    NotificappTheme(darkTheme = true) {
        Box {
            AppIcon(packageName = "com.example.missing", appName = "")
        }
    }
}
