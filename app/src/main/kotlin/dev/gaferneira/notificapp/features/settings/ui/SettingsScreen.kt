package dev.gaferneira.notificapp.features.settings.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.gaferneira.notificapp.BuildConfig
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.AppLinks
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
import dev.gaferneira.notificapp.features.settings.ui.components.AboutSection
import dev.gaferneira.notificapp.features.settings.ui.components.AppearanceSection
import dev.gaferneira.notificapp.features.settings.ui.components.DataSection
import dev.gaferneira.notificapp.features.settings.ui.components.IntegrationsSection
import dev.gaferneira.notificapp.features.settings.ui.components.MonitoringSection
import dev.gaferneira.notificapp.features.settings.ui.components.rememberUrlOpener
import dev.gaferneira.notificapp.features.settings.viewmodel.SettingsViewModel
import dev.gaferneira.notificapp.util.openBatteryOptimizationSettings
import dev.gaferneira.notificapp.util.openNotificationListenerSettings

/**
 * Settings screen, grouped into Monitoring, Integrations, Appearance, Data and About sections.
 *
 * @param navigateTo Navigation callback that accepts a route and optional NavOptions
 * @param viewModel ViewModel for state management
 */
@Composable
fun SettingsScreen(
    navigateTo: (Screen, NavOptions?) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    CollectOneOffEffects(viewModel.effect) { effect ->
        when (effect) {
            is UiEffect.NavigateToAppSelection ->
                navigateTo(Routes.appSelection(isInitialSetup = false), null)
            is UiEffect.NavigateToWebhookList ->
                navigateTo(Routes.webhookList(), null)
            is UiEffect.OpenBatteryOptimizationSettings ->
                openBatteryOptimizationSettings(context)
            is UiEffect.DataCleared ->
                snackbarHostState.showSnackbar(context.getString(R.string.settings_clear_data_success))
            is UiEffect.ClearDataFailed ->
                snackbarHostState.showSnackbar(context.getString(R.string.settings_clear_data_error))
            is UiEffect.ShowError -> snackbarHostState.showSnackbar(effect.message)
        }
    }

    // Re-check listener status when returning to Settings (e.g. from system settings)
    OnResumeEffect { viewModel.onEvent(UiEvent.OnResume) }

    SettingsScreenContent(
        uiState = uiState,
        onEvent = viewModel::onEvent,
        navigateTo = navigateTo,
        snackbarHostState = snackbarHostState,
        onOpenNotificationAccess = { openNotificationListenerSettings(context) },
    )
}

@Composable
private fun SettingsScreenContent(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    navigateTo: (Screen, NavOptions?) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onOpenNotificationAccess: () -> Unit = {},
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { SettingsTopBar() },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                uiState.error != null -> ErrorState(
                    message = uiState.error,
                    onRetry = { onEvent(UiEvent.OnRefresh) },
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> SettingsSections(
                    uiState = uiState,
                    onEvent = onEvent,
                    onOpenNotificationAccess = onOpenNotificationAccess,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsTopBar(modifier: Modifier = Modifier) {
    TopAppBar(
        modifier = modifier,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.settings_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}

@Composable
private fun SettingsSections(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openUrl = rememberUrlOpener()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            MonitoringSection(
                uiState = uiState,
                onEvent = onEvent,
                onOpenNotificationAccess = onOpenNotificationAccess,
            )
        }
        item { IntegrationsSection(onWebhooksClick = { onEvent(UiEvent.OnWebhooksClicked) }) }
        item { AppearanceSection(uiState = uiState, onEvent = onEvent) }
        item { DataSection(uiState = uiState, onEvent = onEvent) }
        item {
            AboutSection(
                versionName = BuildConfig.VERSION_NAME,
                onOpenPrivacyPolicy = { openUrl(AppLinks.PRIVACY_POLICY_URL) },
                onOpenLicenses = { openUrl(AppLinks.OPEN_SOURCE_LICENSES_URL) },
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
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(16.dp))
        TextButton(onClick = onRetry) {
            Text(stringResource(R.string.settings_retry))
        }
    }
}

private val previewState = UiState(
    monitoredApps = listOf(
        SelectedApp("com.test", "Test App", true),
        SelectedApp("com.test2", "Test App 2", true),
    ),
    isNotificationListenerActive = true,
    isLoading = false,
)

@Preview(showBackground = true, device = "id:pixel_5")
@Composable
private fun SettingsScreenPreview() {
    NotificappTheme {
        SettingsScreenContent(uiState = previewState, onEvent = {}, navigateTo = { _, _ -> })
    }
}

@Preview(showBackground = true, device = "id:pixel_5", fontScale = 1.6f)
@Composable
private fun SettingsScreenLargeFontPreview() {
    NotificappTheme {
        SettingsScreenContent(uiState = previewState, onEvent = {}, navigateTo = { _, _ -> })
    }
}

@Preview(showBackground = true, device = "id:pixel_5", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenListenerDisabledPreviewDark() {
    NotificappTheme {
        SettingsScreenContent(
            uiState = previewState.copy(
                isNotificationListenerActive = false,
                monitoringPaused = true,
                isIgnoringBatteryOptimizations = false,
            ),
            onEvent = {},
            navigateTo = { _, _ -> },
        )
    }
}
