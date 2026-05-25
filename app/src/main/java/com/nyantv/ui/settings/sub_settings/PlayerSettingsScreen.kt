package com.nyantv.ui.settings.sub_settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.nyantv.ui.utils.SectionCard
import com.nyantv.ui.utils.SubScreenHeader
import com.nyantv.ui.utils.focusBorder
import androidx.core.content.edit

@Composable
fun PlayerSettingsScreen(navController: NavController) {
    val context = LocalContext.current
    val prefs   = remember { context.getSharedPreferences("nyantv_player_prefs", Context.MODE_PRIVATE) }

    // ── State ──────────────────────────────────────────────────────────────────
    var qualityMode   by remember { mutableStateOf(prefs.getString("quality_mode",  "highest") ?: "highest") }
    var subEnabled    by remember { mutableStateOf(prefs.getBoolean("sub_enabled",  true)) }
    var fontSize      by remember { mutableFloatStateOf(prefs.getFloat("sub_size",  18f)) }
    var bold          by remember { mutableStateOf(prefs.getBoolean("sub_bold",     false)) }
    var translateTo   by remember { mutableStateOf<String?>(prefs.getString("sub_translate", null)) }
    var bigSkipSec by remember { mutableIntStateOf(prefs.getInt("big_skip_sec", 75)) }
    var watchedThreshold by remember { mutableIntStateOf(prefs.getInt("watched_threshold", 80)) }

    // Auto-save whenever any value changes
    LaunchedEffect(qualityMode, subEnabled, fontSize, bold, translateTo, bigSkipSec, watchedThreshold) {
        prefs.edit {
            putString("quality_mode",      qualityMode)
            putBoolean("sub_enabled",      subEnabled)
            putFloat("sub_size",           fontSize)
            putBoolean("sub_bold",         bold)
            putString("sub_translate",     translateTo)
            putInt("big_skip_sec",         bigSkipSec)
            putInt("watched_threshold",    watchedThreshold)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        SubScreenHeader(title = "Player Settings", navController = navController)

        // ── Video Quality ──────────────────────────────────────────────────────
        SectionCard(title = "Video Quality") {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "abr" to Triple(
                        "Adaptive (ABR)",
                        "Automatically adjusts quality based on network conditions",
                        Icons.Filled.NetworkCheck
                    ),
                    "highest" to Triple(
                        "Highest Available",
                        "Always plays the best quality — may rebuffer on slow networks",
                        Icons.Filled.Hd
                    ),
                ).forEach { (mode, triple) ->
                    val (label, description, icon) = triple
                    val selected = qualityMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent
                            )
                            .clickable { qualityMode = mode }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { qualityMode = mode })
                        Icon(
                            icon, null,
                            modifier = Modifier.size(20.dp),
                            tint     = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label,
                                style      = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(
                                description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }

        SectionCard(title = "Auto-Tracking") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Watched threshold",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Episode counts as watched after this much playback",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        "$watchedThreshold%",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value         = watchedThreshold.toFloat(),
                    onValueChange = { watchedThreshold = it.toInt() },
                    valueRange    = 50f..95f,
                    steps         = 8,   // 50 55 60 65 70 75 80 85 90 95
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        }

        SectionCard(title = "Big Skip") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Skip duration",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "Duration of the +Xs button when no segment skip is available",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        "${bigSkipSec}s",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value         = bigSkipSec.toFloat(),
                    onValueChange = { bigSkipSec = it.toInt() },
                    valueRange    = 30f..120f,
                    steps         = 5,   // 30 / 45 / 60 / 75 / 90 / 105 / 120
                    modifier      = Modifier.fillMaxWidth(),
                )
            }
        }

        // ── Subtitles ──────────────────────────────────────────────────────────
        SectionCard(title = "Subtitles") {

            // Show subtitles toggle
            SettingsToggleRow(
                label    = "Show subtitles",
                subtitle = "Display subtitles during playback",
                checked  = subEnabled,
                onToggle = { subEnabled = it }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            // Font size slider
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Font size",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            "Subtitle text size",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Text(
                        "${fontSize.toInt()} sp",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Slider(
                    value         = fontSize,
                    onValueChange = { fontSize = it },
                    valueRange    = 12f..32f,
                    steps         = 19, // integer steps: (32-12)/1 − 1
                    modifier      = Modifier.fillMaxWidth()
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            // Bold toggle
            SettingsToggleRow(
                label    = "Bold text",
                subtitle = "Make subtitle text bold",
                checked  = bold,
                onToggle = { bold = it }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            // Auto-translate dropdown
            var translateExpanded by remember { mutableStateOf(false) }
            val translateOptions = listOf(null, "en", "de", "fr", "es", "ja", "ko", "zh")

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Auto-translate",
                        style      = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "Translate subtitles automatically via Lingva",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Box {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        OutlinedButton(
                            onClick = { translateExpanded = true },
                            modifier = Modifier.focusBorder(RoundedCornerShape(50))
                        ) {
                            Text(translateTo ?: "Off")
                            Icon(Icons.Filled.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                        }
                    }
                    DropdownMenu(
                        expanded         = translateExpanded,
                        onDismissRequest = { translateExpanded = false }
                    ) {
                        translateOptions.forEach { lang ->
                            DropdownMenuItem(
                                text    = { Text(lang ?: "Off") },
                                onClick = { translateTo = lang; translateExpanded = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Shared helper ──────────────────────

@Composable
private fun SettingsToggleRow(
    label:    String,
    subtitle: String,
    checked:  Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label,    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(
                checked         = checked,
                onCheckedChange = onToggle,
                modifier        = Modifier.focusBorder(RoundedCornerShape(50))
            )
        }
    }
}