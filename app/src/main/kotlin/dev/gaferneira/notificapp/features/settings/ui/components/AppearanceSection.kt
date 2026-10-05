package dev.gaferneira.notificapp.features.settings.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.gaferneira.notificapp.R
import dev.gaferneira.notificapp.core.ui.theme.NotificappTheme
import dev.gaferneira.notificapp.domain.model.preferences.AppLanguage
import dev.gaferneira.notificapp.domain.model.preferences.ThemePreference
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiEvent
import dev.gaferneira.notificapp.features.settings.contract.SettingsContract.UiState

/** Appearance section: language and theme pickers. */
@Composable
internal fun AppearanceSection(
    uiState: UiState,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }

    SettingsSection(
        title = stringResource(R.string.settings_section_appearance),
        modifier = modifier,
    ) {
        SettingsValueRow(
            icon = Icons.Default.Language,
            title = stringResource(R.string.settings_language_title),
            value = uiState.appLanguage.label(),
            onClick = { showLanguageDialog = true },
        )
        SettingsDivider()
        SettingsValueRow(
            icon = Icons.Default.DarkMode,
            title = stringResource(R.string.settings_theme_title),
            value = uiState.themePreference.label(),
            onClick = { showThemeDialog = true },
        )
    }

    if (showLanguageDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_language_title),
            options = AppLanguage.entries,
            selected = uiState.appLanguage,
            optionLabel = { it.label() },
            onSelect = {
                onEvent(UiEvent.AppLanguageChanged(it))
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
    if (showThemeDialog) {
        SingleChoiceDialog(
            title = stringResource(R.string.settings_theme_title),
            options = ThemePreference.entries,
            selected = uiState.themePreference,
            optionLabel = { it.label() },
            onSelect = {
                onEvent(UiEvent.OnThemeChanged(it))
                showThemeDialog = false
            },
            onDismiss = { showThemeDialog = false },
        )
    }
}

@Composable
private fun AppLanguage.label(): String = stringResource(
    when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.EN -> R.string.settings_language_english
        AppLanguage.ES -> R.string.settings_language_spanish
    },
)

@Composable
private fun ThemePreference.label(): String = stringResource(
    when (this) {
        ThemePreference.SYSTEM -> R.string.settings_theme_system
        ThemePreference.LIGHT -> R.string.settings_theme_light
        ThemePreference.DARK -> R.string.settings_theme_dark
    },
)

@Preview(showBackground = true)
@Composable
private fun AppearanceSectionPreview() {
    NotificappTheme(dynamicColor = false) {
        AppearanceSection(uiState = UiState(isLoading = false), onEvent = {}, modifier = Modifier.padding(16.dp))
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppearanceSectionPreviewDark() {
    NotificappTheme(dynamicColor = false) {
        AppearanceSection(
            uiState = UiState(themePreference = ThemePreference.DARK, isLoading = false),
            onEvent = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
