package dev.gaferneira.notificapp.core.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/**
 * Searchable multi-select app list shown as a modal bottom sheet (open it from
 * [AppFilterSection]'s "Choose apps" button). Stateless: the caller owns the selection, the search
 * text and the (already filtered and sorted) groups, so search behaviour stays unit-testable in a
 * ViewModel. Toggles apply immediately; "Done" only closes the sheet.
 *
 * The whole sheet is a single [LazyColumn] with a pinned header (title + Done + search field), so
 * it scrolls at any font scale and with any number of apps.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    groups: List<AppPickerGroup>,
    selectedPackages: Set<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = modifier,
    ) {
        AppPickerContent(
            groups = groups,
            selectedPackages = selectedPackages,
            searchQuery = searchQuery,
            onSearchQueryChange = onSearchQueryChange,
            onToggleApp = onToggleApp,
            onDone = onDismiss,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun AppPickerContent(
    groups: List<AppPickerGroup>,
    selectedPackages: Set<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleApp: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visibleGroups = groups.filter { it.apps.isNotEmpty() }
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
    ) {
        stickyHeader(key = "header") {
            AppPickerHeader(
                searchQuery = searchQuery,
                onSearchQueryChange = onSearchQueryChange,
                onDone = onDone,
            )
        }
        if (visibleGroups.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = stringResource(R.string.app_picker_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                )
            }
        }
        visibleGroups.forEach { group ->
            item(key = "group_${group.title}") {
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
                        .semantics { heading() },
                )
            }
            items(items = group.apps, key = { "${group.title}_${it.packageName}" }) { app ->
                AppPickerRow(
                    app = app,
                    selected = app.packageName in selectedPackages,
                    onToggle = { onToggleApp(app.packageName) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BottomSheetDefaults.ContainerColor)
            .padding(horizontal = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.app_picker_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() },
            )
            TextButton(onClick = onDone) { Text(stringResource(R.string.app_picker_done)) }
        }
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            placeholder = { Text(stringResource(R.string.app_picker_search)) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = stringResource(R.string.app_picker_search_clear),
                        )
                    }
                }
            },
            singleLine = true,
        )
    }
}

@Composable
private fun AppPickerRow(
    app: AppPickerOption,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .toggleable(value = selected, role = Role.Checkbox, onValueChange = { onToggle() })
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppIcon(packageName = app.packageName, appName = app.name, size = 40.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            app.supportingText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The row is the single toggleable target; the checkbox only mirrors its state.
        Checkbox(checked = selected, onCheckedChange = null)
    }
}

private val previewGroups = listOf(
    AppPickerGroup(
        title = "Used by rules",
        apps = listOf(
            AppPickerOption("com.example.bank", "Bank", "3 rules"),
            AppPickerOption("com.example.delivery", "A very long delivery application name that wraps", "1 rule"),
        ),
    ),
    AppPickerGroup(
        title = "Other monitored apps",
        apps = listOf(AppPickerOption("com.example.chat", "Chat", "0 rules")),
    ),
)

@Preview(showBackground = true)
@Composable
private fun AppPickerPreview() {
    NotificappTheme(dynamicColor = false) {
        AppPickerContent(
            groups = previewGroups,
            selectedPackages = setOf("com.example.bank"),
            searchQuery = "",
            onSearchQueryChange = {},
            onToggleApp = {},
            onDone = {},
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f)
@Composable
private fun AppPickerLargeFontDarkPreview() {
    NotificappTheme(darkTheme = true, dynamicColor = false) {
        AppPickerContent(
            groups = previewGroups,
            selectedPackages = setOf("com.example.chat"),
            searchQuery = "",
            onSearchQueryChange = {},
            onToggleApp = {},
            onDone = {},
        )
    }
}

@Preview(showBackground = true, name = "Many apps")
@Composable
private fun AppPickerManyAppsPreview() {
    NotificappTheme(dynamicColor = false) {
        AppPickerContent(
            groups = listOf(
                AppPickerGroup(
                    "Used by rules",
                    List(40) { AppPickerOption("com.example.app$it", "App number $it", "${it % 5} rules") },
                ),
            ),
            selectedPackages = emptySet(),
            searchQuery = "app",
            onSearchQueryChange = {},
            onToggleApp = {},
            onDone = {},
        )
    }
}

@Preview(showBackground = true, name = "No results")
@Composable
private fun AppPickerEmptyPreview() {
    NotificappTheme(dynamicColor = false) {
        AppPickerContent(
            groups = emptyList(),
            selectedPackages = emptySet(),
            searchQuery = "zzz",
            onSearchQueryChange = {},
            onToggleApp = {},
            onDone = {},
        )
    }
}
