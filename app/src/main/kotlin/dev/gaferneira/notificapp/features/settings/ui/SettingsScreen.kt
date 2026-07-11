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
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEffect
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEvent
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiState
import dev.gaferneira.notificapp.features.settings.viewmodel.SettingsViewModel
import dev.gaferneira.notificapp.util.openNotificationListenerSettings

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

        // Monitored Apps Section - Simplified
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
    }
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
        // Icon
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
