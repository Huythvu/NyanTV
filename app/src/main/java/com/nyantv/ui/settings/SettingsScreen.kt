package com.nyantv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.navigation.NavController
import com.nyantv.ui.theme.FocusIndication
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.AppViewModel
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.clip

@Composable
fun SettingsScreen(vm: AppViewModel, navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsTile(
                icon        = Icons.Filled.Person,
                title       = "Accounts & Services",
                description = "Manage your account and tracking service",
                onClick     = { navController.navigate("settings/accounts") }
            )
            SettingsTile(
                icon        = Icons.Filled.TrackChanges,
                title       = "Tracking",
                description = "Automation, exclusions and tracking options",
                onClick     = { navController.navigate("settings/tracking") }
            )
            SettingsTile(
                icon        = Icons.Filled.Palette,
                title       = "Theme",
                description = "Personalize the look and make it yours",
                onClick     = { navController.navigate("settings/theme") }
            )
            SettingsTile(
                icon     = Icons.Filled.VideoSettings,
                title    = "Player",
                description = "Streaming quality & subtitles",
                onClick  = { navController.navigate("settings/player") }
            )
            SettingsTile(
                icon        = Icons.Filled.Extension,
                title       = "Extensions",
                description = "Manage your extensions",
                onClick     = { navController.navigate("settings/extensions") }
            )
            SettingsTile(
                icon        = Icons.Filled.History,
                title       = "Watch History",
                description = "Review and remove locally tracked videos",
                onClick     = { navController.navigate("settings/watch_history") }
            )
            SettingsTile(
                icon        = Icons.Filled.Share,
                title       = "Share Logs",
                description = "Share logs of the app",
                onClick     = { navController.navigate("settings/logs") }
            )
            SettingsTile(
                icon        = Icons.Filled.Info,
                title       = "About",
                description = "About the app",
                onClick     = { navController.navigate("settings/about") }
            )
            SettingsTile(
                icon        = Icons.Filled.DeveloperMode,
                title       = "Experimental",
                description = "Playground for developers",
                onClick     = { navController.navigate("settings/experimental") }
            )
        }
    }
}

@Composable
fun SettingsTile(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().focusBorder(MaterialTheme.shapes.large, 1.dp, MaterialTheme.colorScheme.primary ),
        shape    = MaterialTheme.shapes.large,
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier              = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
}