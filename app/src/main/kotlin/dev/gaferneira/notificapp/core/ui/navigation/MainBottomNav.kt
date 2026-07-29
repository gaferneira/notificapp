package dev.gaferneira.notificapp.core.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Main app destinations for bottom navigation.
 */
enum class AppDestinations(
    val label: String,
    val icon: ImageVector,
) {
    HOME("Home", Icons.Default.Home),
    DATA("Data", Icons.Default.DataObject),
    RULES("Rules", Icons.AutoMirrored.Filled.Assignment),
    SETTINGS("Settings", Icons.Default.Settings),
}

/**
 * Bottom navigation bar for the main app screens.
 *
 * This component handles tab switching with proper back stack management:
 * - Switching to a different tab clears the back stack and navigates to that tab
 * - Tapping the current tab does nothing (already there)
 *
 * Usage:
 * ```kotlin
 * MainBottomNav(
 *     selectedDestination = AppDestinations.HOME,
 *     navigateTo = { route, navOptions ->
 *         navigator.navigate(route, navOptions)
 *     }
 * )
 * ```
 *
 * @param selectedDestination The currently selected destination, or null when the current screen
 * (e.g. Inbox) isn't one of the bottom-nav tabs and none should be highlighted
 * @param navigateTo Navigation callback that accepts a route and optional NavOptions
 */
@Composable
fun MainBottomNav(
    selectedDestination: AppDestinations?,
    navigateTo: (Screen, NavOptions?) -> Unit,
) {
    NavigationBar {
        AppDestinations.entries.forEach { destination ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = destination.label,
                    )
                },
                label = { Text(destination.label) },
                selected = destination == selectedDestination,
                onClick = {
                    if (destination == selectedDestination) {
                        // Do nothing if the same destination is selected
                        return@NavigationBarItem
                    }
                    when (destination) {
                        AppDestinations.HOME -> navigateTo(
                            Screen.Home,
                            navOptions { clearStack() },
                        )
                        AppDestinations.RULES -> navigateTo(
                            Screen.Rules,
                            navOptions { clearStack() },
                        )
                        AppDestinations.DATA -> navigateTo(
                            Screen.Data,
                            navOptions { clearStack() },
                        )
                        AppDestinations.SETTINGS -> navigateTo(
                            Screen.Settings,
                            navOptions { clearStack() },
                        )
                    }
                },
            )
        }
    }
}
