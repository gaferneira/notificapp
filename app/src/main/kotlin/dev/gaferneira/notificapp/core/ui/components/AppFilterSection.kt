package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
 * Reusable "filter by apps" block: a summary of the selected apps as removable chips (app icon +
 * name + remove) and a button that the caller wires to [AppPickerSheet]. Stateless; the caller owns
 * selection and the section heading. Intended for any screen that filters by monitored apps
 * (Rules today; the Inbox filter sheet can adopt it).
 *
 * @param selectedApps Currently selected apps, in display order
 * @param emptyText Shown when nothing is selected (e.g. "Any app")
 * @param onRemoveApp Called with the package name of the chip the user dismissed
 * @param onChooseApps Called when the user taps the "Choose apps" button
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AppFilterSection(
    selectedApps: List<AppPickerOption>,
    emptyText: String,
    onRemoveApp: (String) -> Unit,
    onChooseApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedApps.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedApps.forEach { app ->
                    InputChip(
                        selected = true,
                        onClick = { onRemoveApp(app.packageName) },
                        label = { Text(text = app.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        avatar = { AppIcon(packageName = app.packageName, appName = app.name, size = 24.dp) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.app_picker_remove_cd, app.name),
                                modifier = Modifier.size(InputChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
        OutlinedButton(onClick = onChooseApps) {
            Text(stringResource(R.string.app_picker_choose))
        }
    }
}

private val previewApps = listOf(
    AppPickerOption("com.example.bank", "Bank"),
    AppPickerOption("com.example.delivery", "A very long delivery application name that wraps"),
)

@Preview(showBackground = true)
@Composable
private fun AppFilterSectionPreview() {
    NotificappTheme(dynamicColor = false) {
        AppFilterSection(selectedApps = previewApps, emptyText = "Any app", onRemoveApp = {}, onChooseApps = {})
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppFilterSectionEmptyDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        AppFilterSection(selectedApps = emptyList(), emptyText = "Any app", onRemoveApp = {}, onChooseApps = {})
    }
}
