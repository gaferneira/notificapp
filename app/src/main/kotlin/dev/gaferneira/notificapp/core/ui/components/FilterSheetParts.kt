package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/**
 * Building blocks shared by the "Filter" bottom sheets (Rules, Inbox): a heading-semantics section
 * title, a single-choice control that degrades to a radio list at large font scales, and the pinned
 * footer with "Clear all" and the live-count apply button.
 */

/** Font scale above which [SegmentedChoiceGroup] switches to a radio list. */
private const val LARGE_FONT_SCALE = 1.3f

/** One option of a [SegmentedChoiceGroup]; [label] is already localized by the caller. */
data class ChoiceOption<T>(val value: T, val label: String)

/** Small section heading of a filter sheet, exposed to accessibility services as a heading. */
@Composable
fun FilterSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.semantics { heading() },
    )
}

/**
 * Single-choice control. Equal-width segments cannot hold large-font labels, so above a 1.3 font
 * scale it falls back to a radio list with 48dp rows.
 */
@Composable
fun <T> SegmentedChoiceGroup(
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (LocalDensity.current.fontScale > LARGE_FONT_SCALE) {
        Column(modifier = modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(
                            selected = selected == option.value,
                            role = Role.RadioButton,
                            onClick = { onSelect(option.value) },
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = selected == option.value, onClick = null)
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    } else {
        SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = selected == option.value,
                    onClick = { onSelect(option.value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                ) {
                    Text(option.label, maxLines = 1)
                }
            }
        }
    }
}

/**
 * Pinned footer of a filter sheet: "Clear all" (enabled only while a filter is active) and an
 * apply button whose [applyLabel] carries the live match count (e.g. "Show 12 rules").
 */
@Composable
fun FilterSheetFooter(
    clearEnabled: Boolean,
    onClearAll: () -> Unit,
    applyLabel: String,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.navigationBarsPadding()) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onClearAll,
                modifier = Modifier.weight(1f),
                enabled = clearEnabled,
            ) {
                Icon(
                    imageVector = Icons.Default.ClearAll,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(stringResource(R.string.clear_all))
            }
            Button(onClick = onApply, modifier = Modifier.weight(1f)) {
                Text(applyLabel)
            }
        }
    }
}

private val previewOptions = listOf(
    ChoiceOption("all", "All"),
    ChoiceOption("processed", "Processed"),
    ChoiceOption("unprocessed", "Unprocessed"),
)

@Preview(showBackground = true)
@Composable
private fun FilterSheetPartsPreview() {
    NotificappTheme(dynamicColor = false) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterSectionTitle("Status")
            SegmentedChoiceGroup(options = previewOptions, selected = "all", onSelect = {})
            FilterSheetFooter(clearEnabled = true, onClearAll = {}, applyLabel = "Show 12 items", onApply = {})
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun FilterSheetPartsLargeFontDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterSectionTitle("Status")
            SegmentedChoiceGroup(options = previewOptions, selected = "processed", onSelect = {})
            FilterSheetFooter(clearEnabled = false, onClearAll = {}, applyLabel = "Show 12 items", onApply = {})
        }
    }
}
