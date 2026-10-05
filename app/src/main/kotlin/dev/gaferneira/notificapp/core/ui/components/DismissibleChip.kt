package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/**
 * One dismissible chip of [DismissibleChipsRow].
 *
 * @property value The feature-owned object handed back to `onRemove`; also the item key, so it must
 * be unique within the row
 * @property label Already-localized chip text
 * @property appPackageName When set, the chip shows that app's icon as its avatar
 */
data class DismissibleChip<T : Any>(
    val value: T,
    val label: String,
    val appPackageName: String? = null,
)

/**
 * Horizontally scrollable row of dismissible chips (one per active filter). Tapping a chip calls
 * [onRemove] with its [DismissibleChip.value]. Renders nothing when [chips] is empty. Stateless and
 * feature-agnostic: the feature maps its own filter model to [DismissibleChip]s.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T : Any> DismissibleChipsRow(
    chips: List<DismissibleChip<T>>,
    onRemove: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (chips.isEmpty()) return

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items = chips, key = { it.value.toString() }) { chip ->
            InputChip(
                selected = true,
                onClick = { onRemove(chip.value) },
                label = { Text(text = chip.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                avatar = chip.appPackageName?.let { pkg ->
                    { AppIcon(packageName = pkg, appName = chip.label, size = 24.dp) }
                },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.rules_filter_chip_remove_cd, chip.label),
                        modifier = Modifier.size(InputChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

private val previewChips = listOf(
    DismissibleChip("status", "Unprocessed"),
    DismissibleChip("ica", "ICA", appPackageName = "com.ica"),
    DismissibleChip("long", "A very long application display name", appPackageName = "com.long"),
)

@Preview(showBackground = true)
@Composable
private fun DismissibleChipsRowPreview() {
    NotificappTheme(dynamicColor = false) {
        DismissibleChipsRow(chips = previewChips, onRemove = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun DismissibleChipsRowDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        DismissibleChipsRow(chips = previewChips, onRemove = {})
    }
}
