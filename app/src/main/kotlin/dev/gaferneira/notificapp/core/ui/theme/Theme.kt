package dev.gaferneira.notificapp.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dev.gaferneira.notificapp.domain.model.preferences.ThemePreference

private val DarkColorScheme = darkColorScheme(
    primary = DeepTechPrimaryDark,
    onPrimary = DeepTechOnPrimaryDark,
    primaryContainer = DeepTechPrimaryContainerDark,
    onPrimaryContainer = DeepTechOnPrimaryContainerDark,
    secondary = DeepTechSecondaryDark,
    onSecondary = DeepTechOnSecondaryDark,
    secondaryContainer = DeepTechSecondaryContainerDark,
    onSecondaryContainer = DeepTechOnSecondaryContainerDark,
    tertiary = DeepTechTertiaryDark,
    onTertiary = DeepTechOnTertiaryDark,
    tertiaryContainer = DeepTechTertiaryContainerDark,
    onTertiaryContainer = DeepTechOnTertiaryContainerDark,
    background = DeepTechBackgroundDark,
    onBackground = DeepTechOnBackgroundDark,
    surface = DeepTechSurfaceDark,
    onSurface = DeepTechOnSurfaceDark,
    surfaceVariant = DeepTechSurfaceVariantDark,
    onSurfaceVariant = DeepTechOnSurfaceVariantDark,
    outline = DeepTechOutlineDark,
)

private val LightColorScheme = lightColorScheme(
    primary = DeepTechPrimaryLight,
    onPrimary = DeepTechOnPrimaryLight,
    primaryContainer = DeepTechPrimaryContainerLight,
    onPrimaryContainer = DeepTechOnPrimaryContainerLight,
    secondary = DeepTechSecondaryLight,
    onSecondary = DeepTechOnSecondaryLight,
    secondaryContainer = DeepTechSecondaryContainerLight,
    onSecondaryContainer = DeepTechOnSecondaryContainerLight,
    tertiary = DeepTechTertiaryLight,
    onTertiary = DeepTechOnTertiaryLight,
    tertiaryContainer = DeepTechTertiaryContainerLight,
    onTertiaryContainer = DeepTechOnTertiaryContainerLight,
    background = DeepTechBackgroundLight,
    onBackground = DeepTechOnBackgroundLight,
    surface = DeepTechSurfaceLight,
    onSurface = DeepTechOnSurfaceLight,
    surfaceVariant = DeepTechSurfaceVariantLight,
    onSurfaceVariant = DeepTechOnSurfaceVariantLight,
    outline = DeepTechOutlineLight,
)

/** Resolves whether the dark scheme applies: SYSTEM follows the device, LIGHT/DARK force it. */
@Composable
fun ThemePreference.isDarkTheme(): Boolean = when (this) {
    ThemePreference.SYSTEM -> isSystemInDarkTheme()
    ThemePreference.LIGHT -> false
    ThemePreference.DARK -> true
}

@Composable
fun NotificappTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val shapes = remember { Shapes() }
    val tokens = remember(colorScheme) {
        AppTokens(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = shapes,
            spacing = AppSpacing(),
        )
    }

    CompositionLocalProvider(LocalAppTokens provides tokens) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = shapes,
            content = content,
        )
    }
}
