package com.example.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.Dashboard, Icons.Outlined.Dashboard)
    data object Files : Screen("files", "Files", Icons.Filled.Folder, Icons.Outlined.Folder)
    data object Clipboard : Screen("clipboard", "Clipboard", Icons.Filled.ContentPaste, Icons.Outlined.ContentPaste)
    data object Notifications : Screen("notifications", "Alerts", Icons.Filled.Notifications, Icons.Outlined.Notifications)
    data object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)

    // Sub-screens
    data object Pairing : Screen("pairing", "Pair Device", Icons.Filled.Dashboard, Icons.Outlined.Dashboard)
    data object History : Screen("history", "Transfer History", Icons.Filled.Folder, Icons.Outlined.Folder)

    companion object {
        val bottomNavItems = listOf(Dashboard, Files, Clipboard, Notifications, Settings)
    }
}
