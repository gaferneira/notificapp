package dev.gaferneira.notificapp.features.ruleeditor.ui.components

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.LocalIoDispatcher
import dev.gaferneira.notificapp.domain.model.AppInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/**
 * Bottom sheet for selecting target apps for a rule.
 *
 * @param selectedApps Currently selected apps
 * @param enabledApps List of enabled apps to show in the picker (filters the installed apps)
 * @param onConfirm Called when apps are confirmed
 * @param onDismiss Called when the sheet should be dismissed
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionPicker(
    selectedApps: ImmutableList<AppInfo>,
    enabledApps: ImmutableList<AppInfo>,
    onConfirm: (List<AppInfo>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val ioDispatcher = LocalIoDispatcher.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )

    var searchQuery by remember { mutableStateOf("") }
    var availableApps by remember { mutableStateOf<List<AppDisplayInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var tempSelectedPackages by remember { mutableStateOf(selectedApps.map { it.packageName }.toSet()) }

    // Load installed apps filtered by enabled apps
    LaunchedEffect(enabledApps) {
        isLoading = true
        val allInstalledApps = loadInstalledApps(context, ioDispatcher)
        // Filter to only show enabled apps
        availableApps = allInstalledApps.filter { installedApp ->
            enabledApps.any { it.packageName == installedApp.packageName }
        }
        // Initialize temp selection with currently selected apps
        tempSelectedPackages = selectedApps.map { it.packageName }.toSet()
        isLoading = false
    }

    // Filter apps based on search
    val filteredApps = remember(searchQuery, availableApps) {
        if (searchQuery.isBlank()) {
            availableApps
        } else {
            availableApps.filter { app ->
                app.name.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val selectedCount = tempSelectedPackages.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.statusBarsPadding().fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Select Apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Close",
                    )
                }
            }

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                ),
                singleLine = true,
            )

            // Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = filteredApps,
                            key = { it.packageName },
                        ) { app ->
                            AppListItem(
                                app = app,
                                isSelected = tempSelectedPackages.contains(app.packageName),
                                onToggle = {
                                    tempSelectedPackages = if (tempSelectedPackages.contains(app.packageName)) {
                                        tempSelectedPackages - app.packageName
                                    } else {
                                        tempSelectedPackages + app.packageName
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Bottom bar
            HorizontalDivider()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$selectedCount app${if (selectedCount == 1) "" else "s"} selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (selectedCount > 0) {
                        TextButton(
                            onClick = { tempSelectedPackages = emptySet() },
                        ) {
                            Text("CLEAR ALL")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        val selectedAppInfos = availableApps
                            .filter { tempSelectedPackages.contains(it.packageName) }
                            .map { AppInfo(it.packageName, it.name) }
                        onConfirm(selectedAppInfos)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("Confirm Selection")
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    app: AppDisplayInfo,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                } else {
                    MaterialTheme.colorScheme.surface
                },
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // App icon
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            app.icon?.let { bitmap ->
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // App info
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = app.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Selection indicator
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

private data class AppDisplayInfo(val packageName: String, val name: String, val icon: ImageBitmap? = null)

private suspend fun loadInstalledApps(
    context: Context,
    ioDispatcher: CoroutineDispatcher,
): List<AppDisplayInfo> = withContext(ioDispatcher) {
    val packageManager = context.packageManager
    val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

    installedApps
        .filter { app ->
            // Filter out system apps without launch intent (likely not notification senders)
            // But keep apps that can send notifications
            (app.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
                packageManager.getLaunchIntentForPackage(app.packageName) != null
        }
        .sortedBy { app ->
            packageManager.getApplicationLabel(app).toString()
        }
        .map { app ->
            val appName = packageManager.getApplicationLabel(app).toString()
            val icon = try {
                packageManager.getApplicationIcon(app.packageName)
                    .toBitmap(width = 128, height = 128)
                    .asImageBitmap()
            } catch (_: Exception) {
                null
            }
            AppDisplayInfo(
                packageName = app.packageName,
                name = appName,
                icon = icon,
            )
        }
}

@Preview(showBackground = true)
@Composable
private fun AppSelectionPickerPreview() {
    NotificappTheme {
        AppSelectionPicker(
            selectedApps = persistentListOf(
                AppInfo("com.whatsapp", "WhatsApp"),
                AppInfo("com.telegram", "Telegram"),
            ),
            enabledApps = persistentListOf(
                AppInfo("com.whatsapp", "WhatsApp"),
                AppInfo("com.telegram", "Telegram"),
                AppInfo("com.slack", "Slack"),
            ),
            onDismiss = {},
            onConfirm = {},
        )
    }
}
