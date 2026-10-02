package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.foundation.style.then
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappStyles
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/** A tonal-container card, replacing the `Card` + ad-hoc tonal-color pattern in onboarding. */
@Composable
fun TonalCard(
    modifier: Modifier = Modifier,
    style: Style = Style,
    content: @Composable ColumnScope.() -> Unit,
) {
    val styleState = remember { MutableStyleState(null) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .styleable(styleState, NotificappStyles.tonalCardStyle then style),
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun TonalCardPreview() {
    NotificappTheme(dynamicColor = false) {
        TonalCard {
            Text(
                text = "Tonal card content",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun TonalCardPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        TonalCard {
            Text(
                text = "Tonal card content",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
