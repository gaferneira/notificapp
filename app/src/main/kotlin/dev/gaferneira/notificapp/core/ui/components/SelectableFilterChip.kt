package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/**
 * A multi-select filter chip whose selected state is conveyed by a check icon in addition to
 * colour (so it does not rely on colour alone). Uses the default Material 3 colours; Material
 * chips do not support the Styles API yet.
 */
@Composable
fun SelectableFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text = label, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        modifier = modifier,
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Default.Done,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true)
@Composable
private fun SelectableFilterChipPreview() {
    NotificappTheme(dynamicColor = false) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableFilterChip(selected = true, onClick = {}, label = "Finance · 3")
            SelectableFilterChip(selected = false, onClick = {}, label = "Deliveries · 1")
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SelectableFilterChipDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectableFilterChip(selected = true, onClick = {}, label = "Finance · 3")
            SelectableFilterChip(selected = false, onClick = {}, label = "Deliveries · 1")
        }
    }
}
