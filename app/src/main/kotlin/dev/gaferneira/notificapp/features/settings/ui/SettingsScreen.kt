package dev.gaferneira.notificapp.features.settings.ui

import android.content.res.Configuration
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.BuildConfig
import dev.gaferneira.notificapp.core.ui.mvi.CollectOneOffEffects
import dev.gaferneira.notificapp.core.ui.navigation.AppDestinations
import dev.gaferneira.notificapp.core.ui.navigation.MainBottomNav
import dev.gaferneira.notificapp.core.ui.navigation.NavOptions
import dev.gaferneira.notificapp.core.ui.navigation.Routes
import dev.gaferneira.notificapp.core.ui.navigation.Screen
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.core.ui.utils.OnResumeEffect
import dev.gaferneira.notificapp.domain.model.SelectedApp
import dev.gaferneira.notificapp.domain.model.StorageStats
import dev.gaferneira.notificapp.domain.model.preferences.RetentionPeriod
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEffect
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEvent
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiState
import dev.gaferneira.notificapp.features.settings.viewmodel.SettingsViewModel
import dev.gaferneira.notificapp.util.openNotificationListenerSettings
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/**
 * Settings screen for app configuration.
 *
 * Provides options to manage monitored apps, notification settings,
 * and app preferences.
 *
 * @param navigateTo Navigation callback that accepts a route and optional NavOptions
 * @param modifier Modifier for the screen
 * @param viewModel ViewModel for state management
 */
@Composable
fun SettingsScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle effects
    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.NavigateToAppSelection ->
                navigateTo(Routes.appSelection(isInitialSetup = false), null)
            is UiEffect.NavigateToWebhookList ->
                navigateTo(Routes.webhookList(), null)
            else -> {}
        }
    }

    // Re-check listener status when returning to Settings (e.g. from system settings)
    OnResumeEffect { viewModel.onEvent(UiEvent.OnResume) }

    SettingsScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        navigateTo = navigateTo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Manage your app preferences",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            MainBottomNav(
                selectedDestination = AppDestinations.SETTINGS,
                navigateTo = navigateTo,
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error!!,
                        onRetry = { onEvent(UiEvent.OnRefresh) },
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    SettingsList(
                        uiState = uiState,
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Monitored Apps + Webhooks sections, split out of [SettingsList] to keep it within the LongMethod budget. */
private fun LazyListScope.monitoredAppsAndWebhooksSections(uiState: UiState, onEvent: (UiEvent) -> Unit) {
    item {
        SectionHeader(title = "Monitored Apps")
    }

    item {
        MonitoredAppsCard(
            appsCount = uiState.monitoredAppsCount,
            onSelectApps = {
                onEvent(UiEvent.OnSelectAppsClicked)
            },
        )
    }

    item {
        Spacer(modifier = Modifier.height(8.dp))
    }

    item {
        SectionHeader(title = "Webhooks")
    }

    item {
        WebhooksCard(onClick = { onEvent(UiEvent.OnWebhooksClicked) })
    }

    item {
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun SettingsList(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Listener health - the most important status on this screen, since the
        // entire app is inert if this permission is revoked or killed by the OS.
        item {
            ListenerStatusCard(
                isActive = uiState.isNotificationListenerActive,
                onEnableClick = { openNotificationListenerSettings(context) },
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        monitoredAppsAndWebhooksSections(uiState = uiState, onEvent = onEvent)

        // General Settings Section
        item {
            SectionHeader(title = "General")
        }

        item {
            GeneralSettingsCard(uiState = uiState, onEvent = onEvent)
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Storage Section
        item {
            SectionHeader(title = "Storage")
        }

        item {
            StorageUsageCard(storageStats = uiState.storageStats)
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // About Section
        item {
            SectionHeader(title = "About")
        }

        item {
            AboutCard()
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Data Collection + Show App Icons toggles, grouped under "General". */
@Composable
private fun GeneralSettingsCard(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
) {
    SettingsCard {
        ToggleSettingItem(
            icon = Icons.Default.Notifications,
            iconTint = MaterialTheme.colorScheme.primary,
            title = "Data Collection",
            subtitle = if (uiState.isCollectionEnabled) "Active - monitoring notifications" else "Paused - not collecting data",
            checked = uiState.isCollectionEnabled,
            onCheckedChange = { onEvent(UiEvent.OnCollectionToggled(it)) },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        ToggleSettingItem(
            icon = Icons.Default.Apps,
            iconTint = MaterialTheme.colorScheme.secondary,
            title = "Show App Icons",
            subtitle = "Display app icons in lists",
            checked = uiState.showAppIcons,
            onCheckedChange = { onEvent(UiEvent.OnShowAppIconsToggled(it)) },
        )

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        SelectableSettingItem(
            icon = Icons.Default.Storage,
            iconTint = MaterialTheme.colorScheme.tertiary,
            title = "Notification Retention",
            subtitle = "Auto-delete notifications after",
            selectedValueLabel = uiState.retentionPeriod.label(),
            onValueSelected = { onEvent(UiEvent.RetentionPeriodChanged(it)) },
            currentValue = uiState.retentionPeriod,
        )
    }
}

/** Human-readable label for a [RetentionPeriod], used both in the row and the dialog. */
private fun RetentionPeriod.label(): String = when (this) {
    RetentionPeriod.DAYS_30 -> "30 days"
    RetentionPeriod.DAYS_90 -> "90 days"
    RetentionPeriod.NEVER -> "Never"
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
    )
}

@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column {
            content()
        }
    }
}

/**
 * Notification listener health - surfaced as its own card since the entire app
 * is inert when this permission is revoked or killed by the OS.
 */
@Composable
private fun ListenerStatusCard(
    isActive: Boolean,
    onEnableClick: () -> Unit,
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isActive) "Notification access active" else "Notification access disabled",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                )
                if (!isActive) {
                    Text(
                        text = "Notificapp can't see notifications until this is re-enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor,
                    )
                }
            }
            if (!isActive) {
                Button(onClick = onEnableClick) {
                    Text("Enable")
                }
            }
        }
    }
}

@Composable
private fun MonitoredAppsCard(
    appsCount: Int,
    onSelectApps: () -> Unit,
) {
    val hasNoMonitoredApps = appsCount == 0
    val containerColor = if (hasNoMonitoredApps) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelectApps),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Icon
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Column {
                    Text(
                        text = if (hasNoMonitoredApps) {
                            "No apps monitored"
                        } else {
                            "$appsCount app${if (appsCount == 1) "" else "s"} monitored"
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (hasNoMonitoredApps) "Select apps to start" else "Tap to manage apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Chevron and Add button
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Entry point to the webhook list, styled like [MonitoredAppsCard]. */
@Composable
private fun WebhooksCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }

                Column {
                    Text(
                        text = "Manage webhooks",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Send extracted data to your own services",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ToggleSettingItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingIcon(icon = icon, iconTint = iconTint)

        // Text
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Toggle - onCheckedChange is null so the row's toggleable modifier
        // is the single source of truth for taps; avoids double-firing when
        // both the row and the switch handle the same touch.
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

/**
 * Row styled like [ToggleSettingItem] (icon + title/subtitle) but clickable instead of a toggle,
 * showing the currently selected value and opening a 3-option [AlertDialog] with [RadioButton]
 * rows to pick a new one.
 */
@Composable
private fun SelectableSettingItem(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    selectedValueLabel: String,
    currentValue: RetentionPeriod,
    onValueSelected: (RetentionPeriod) -> Unit,
) {
    var isDialogVisible by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isDialogVisible = true }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SettingIcon(icon = icon, iconTint = iconTint)

        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = selectedValueLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }

    if (isDialogVisible) {
        RetentionPeriodDialog(
            currentValue = currentValue,
            onValueSelected = {
                onValueSelected(it)
                isDialogVisible = false
            },
            onDismiss = { isDialogVisible = false },
        )
    }
}

/** Icon-in-circle used by [ToggleSettingItem] and [SelectableSettingItem]. */
@Composable
private fun SettingIcon(icon: ImageVector, iconTint: Color) {
    Surface(
        shape = CircleShape,
        color = iconTint.copy(alpha = 0.1f),
        modifier = Modifier.size(40.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RetentionPeriodDialog(
    currentValue: RetentionPeriod,
    onValueSelected: (RetentionPeriod) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Notification Retention") },
        text = {
            Column {
                RetentionPeriod.entries.forEach { period ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onValueSelected(period) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = period == currentValue,
                            onClick = { onValueSelected(period) },
                        )
                        Text(
                            text = period.label(),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

/** Database size + row counts, mirroring [MonitoredAppsCard]'s card shape. */
@Composable
private fun StorageUsageCard(storageStats: StorageStats?) {
    SettingsCard {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            StorageStatRow(label = "Database size", value = storageStats?.databaseSizeBytes?.toHumanReadableSize() ?: "—")
            StorageStatRow(label = "Notifications", value = storageStats?.notificationCount?.toString() ?: "—")
            StorageStatRow(label = "Rules", value = storageStats?.ruleCount?.toString() ?: "—")
            StorageStatRow(label = "Rule executions", value = storageStats?.ruleExecutionCount?.toString() ?: "—")
            StorageStatRow(label = "Extracted values", value = storageStats?.extractedFieldValueCount?.toString() ?: "—")
            StorageStatRow(label = "Monitored apps", value = storageStats?.selectedAppCount?.toString() ?: "—", showDivider = false)
        }
    }
}

@Composable
private fun StorageStatRow(label: String, value: String, showDivider: Boolean = true) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
    if (showDivider) {
        HorizontalDivider()
    }
}

/** Formats a byte count as a human-readable size (e.g. "2.3 MB"). */
private fun Long.toHumanReadableSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (ln(this.toDouble()) / ln(BYTES_UNIT)).toInt().coerceIn(0, units.size - 1)
    val size = this / BYTES_UNIT.pow(digitGroups)
    return if (digitGroups == 0) {
        "$this ${units[0]}"
    } else {
        String.format(Locale.ROOT, "%.1f %s", size, units[digitGroups])
    }
}

private const val BYTES_UNIT = 1024.0

@Composable
private fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }

                Column {
                    Text(
                        text = "Notificapp",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Extract structured data from your notifications. All processing happens locally on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Open Source • Privacy First • Local Processing",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text("Retry")
        }
    }
}

// Previews
@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun SettingsScreenPreview() {
    NotificappTheme {
        SettingsScreenContent(
            uiState = UiState(
                monitoredApps = listOf(
                    SelectedApp("com.test", "Test App", true),
                    SelectedApp("com.test2", "Test App 2", true),
                    SelectedApp("com.test3", "Test App 3", true),
                ),
                isNotificationListenerActive = true,
                isCollectionEnabled = true,
                showAppIcons = true,
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun SettingsScreenEmptyPreview() {
    NotificappTheme {
        SettingsScreenContent(
            uiState = UiState(
                monitoredApps = emptyList(),
                isNotificationListenerActive = true,
                isCollectionEnabled = true,
                showAppIcons = true,
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun SettingsScreenListenerDisabledPreview() {
    NotificappTheme {
        SettingsScreenContent(
            uiState = UiState(
                monitoredApps = listOf(SelectedApp("com.test", "Test App", true)),
                isNotificationListenerActive = false,
                isCollectionEnabled = true,
                showAppIcons = true,
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
        )
    }
}

@Preview(showBackground = true, device = "id:pixel_5", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenListenerDisabledPreviewDark() {
    NotificappTheme {
        SettingsScreenContent(
            uiState = UiState(
                monitoredApps = listOf(SelectedApp("com.test", "Test App", true)),
                isNotificationListenerActive = false,
                isCollectionEnabled = true,
                showAppIcons = true,
                isLoading = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
        )
    }
}
