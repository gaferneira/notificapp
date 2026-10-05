package dev.gaferneira.notificapp.features.settings.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme

/** Integrations section: entry point to webhook management. */
@Composable
internal fun IntegrationsSection(
    onWebhooksClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsSection(
        title = stringResource(R.string.settings_section_integrations),
        modifier = modifier,
    ) {
        SettingsNavigationRow(
            icon = Icons.Default.Webhook,
            title = stringResource(R.string.settings_webhooks_title),
            subtitle = stringResource(R.string.settings_webhooks_subtitle),
            onClick = onWebhooksClick,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IntegrationsSectionPreview() {
    NotificappTheme(dynamicColor = false) {
        IntegrationsSection(onWebhooksClick = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IntegrationsSectionPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        IntegrationsSection(onWebhooksClick = {}, modifier = Modifier.padding(16.dp))
    }
}
