package dev.gaferneira.notificapp.core.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
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
    INBOX("Inbox", Icons.Default.Notifications),
    RULES("Rules", Icons.Default.Home),
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
 *     selectedDestination = AppDestinations.INBOX,
 *     navigateTo = { route, navOptions ->
 *         navigator.navigate(route, navOptions)
 *     }
 * )
 * ```
 *
 * @param selectedDestination The currently selected destination
 * @param navigateTo Navigation callback that accepts a route and optional NavOptions
 */
@Composable
fun MainBottomNav(
    selectedDestination: AppDestinations,
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
                        AppDestinations.INBOX -> navigateTo(
                            Screen.Inbox,
                            navOptions { clearStack() },
                        )
                        AppDestinations.RULES -> navigateTo(
                            Screen.Rules,
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
