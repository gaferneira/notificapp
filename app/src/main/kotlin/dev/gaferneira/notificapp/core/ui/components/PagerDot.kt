package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappStyles
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/** A single pager-page indicator dot, replacing the inline `Box`/`clip`/`background` pattern. */
@Composable
fun PagerDot(
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val styleState = remember { MutableStyleState(null) }
    val style = if (selected) NotificappStyles.pagerDotSelectedStyle else NotificappStyles.pagerDotUnselectedStyle
    Box(
        modifier = modifier
            .padding(horizontal = 4.dp)
            .styleable(styleState, style),
    )
}

@Preview(showBackground = true)
@Composable
private fun PagerDotPreview() {
    NotificappTheme(dynamicColor = false) {
        Row {
            PagerDot(selected = true)
            PagerDot(selected = false)
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PagerDotPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        Row {
            PagerDot(selected = true)
            PagerDot(selected = false)
        }
    }
}
