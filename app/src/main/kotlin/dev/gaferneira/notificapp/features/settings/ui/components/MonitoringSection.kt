package dev.gaferneira.notificapp.features.settings.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEvent
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiState

/** Monitoring section: access status, battery optimization, pause switch and monitored apps. */
@Composable
internal fun MonitoringSection(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_monitoring),
        modifier = modifier,
    ) {
        NotificationAccessStatus(
            isActive = uiState.isNotificationListenerActive,
            onEnableClick = onOpenNotificationAccess,
        )
        SettingsDivider()
        SettingsRow(
            icon = if (uiState.isIgnoringBatteryOptimizations) Icons.Default.BatteryFull else Icons.Default.BatteryAlert,
            title = stringResource(R.string.settings_battery_title),
            subtitle = stringResource(
                if (uiState.isIgnoringBatteryOptimizations) {
                    R.string.settings_battery_subtitle_unrestricted
                } else {
                    R.string.settings_battery_subtitle_restricted
                },
            ),
            onClick = { onEvent(UiEvent.OnOpenBatterySettingsClicked) },
        )
        SettingsDivider()
        SettingsSwitchRow(
            icon = Icons.Default.PauseCircle,
            title = stringResource(R.string.settings_pause_title),
            subtitle = stringResource(R.string.settings_pause_subtitle),
            checked = uiState.monitoringPaused,
            onCheckedChange = { onEvent(UiEvent.OnMonitoringPausedChanged(it)) },
        )
        SettingsDivider()
        SettingsNavigationRow(
            icon = Icons.Default.Apps,
            title = if (uiState.hasMonitoredApps) {
                pluralStringResource(R.plurals.settings_apps_monitored, uiState.monitoredAppsCount, uiState.monitoredAppsCount)
            } else {
                stringResource(R.string.settings_apps_none)
            },
            subtitle = stringResource(
                if (uiState.hasMonitoredApps) R.string.settings_apps_manage_hint else R.string.settings_apps_select_hint,
            ),
            onClick = { onEvent(UiEvent.OnSelectAppsClicked) },
        )
    }
}

@Composable
private fun NotificationAccessStatus(
    isActive: Boolean,
    onEnableClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (isActive) {
        SettingsRow(
            icon = Icons.Default.Notifications,
            title = stringResource(R.string.settings_access_active),
            modifier = modifier,
        )
        return
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsOff,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_access_disabled),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.settings_access_disabled_hint),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onEnableClick) {
                Text(stringResource(R.string.settings_access_enable))
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MonitoringSectionPreview() {
    NotificappTheme(dynamicColor = false) {
        MonitoringSection(
            uiState = UiState(isNotificationListenerActive = false, isIgnoringBatteryOptimizations = false, isLoading = false),
            onEvent = {},
            onOpenNotificationAccess = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonitoringSectionPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        MonitoringSection(
            uiState = UiState(isNotificationListenerActive = true, monitoringPaused = true, isLoading = false),
            onEvent = {},
            onOpenNotificationAccess = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
