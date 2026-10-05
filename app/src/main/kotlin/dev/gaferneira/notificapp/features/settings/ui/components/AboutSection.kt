package dev.gaferneira.notificapp.features.settings.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.components.IconBadge
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/** About section: app identity, version, description and legal links. */
@Composable
internal fun AboutSection(
    versionName: String,
    onOpenPrivacyPolicy: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_about),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconBadge(icon = Icons.Default.Notifications, modifier = Modifier.size(44.dp))
                Column {
                    Text(
                        text = stringResource(R.string.settings_about_app_name),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(R.string.settings_about_version, versionName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.settings_about_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.settings_about_tagline),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        }
        SettingsDivider()
        SettingsNavigationRow(
            icon = Icons.Default.PrivacyTip,
            title = stringResource(R.string.settings_privacy_policy),
            onClick = onOpenPrivacyPolicy,
        )
        SettingsDivider()
        SettingsNavigationRow(
            icon = Icons.Default.Description,
            title = stringResource(R.string.settings_open_source_licenses),
            onClick = onOpenLicenses,
        )
    }
}

/** Opens [url] with the platform URI handler. */
@Composable
internal fun rememberUrlOpener(): (String) -> Unit {
    val uriHandler = LocalUriHandler.current
    return { url -> uriHandler.openUri(url) }
}

@Preview(showBackground = true)
@Composable
private fun AboutSectionPreview() {
    NotificappTheme(dynamicColor = false) {
        AboutSection(
            versionName = "1.0.0",
            onOpenPrivacyPolicy = {},
            onOpenLicenses = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AboutSectionPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        AboutSection(
            versionName = "1.0.0",
            onOpenPrivacyPolicy = {},
            onOpenLicenses = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
