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
    var playerEngine by remember { mutableStateOf(prefs.getString("player_engine", "exoplayer") ?: "exoplayer") }
    var subEnabled    by remember { mutableStateOf(prefs.getBoolean("sub_enabled",  true)) }
    var fontSize      by remember { mutableFloatStateOf(prefs.getFloat("sub_size",  18f)) }
    var bold          by remember { mutableStateOf(prefs.getBoolean("sub_bold",     false)) }
    var translateTo   by remember { mutableStateOf<String?>(prefs.getString("sub_translate", null)) }
    var bigSkipSec by remember { mutableIntStateOf(prefs.getInt("big_skip_sec", 75)) }
    var seekStepSec by remember { mutableIntStateOf(prefs.getInt("seek_step_sec", 10)) }
    var watchedThreshold by remember { mutableIntStateOf(prefs.getInt("watched_threshold", 80)) }
    var advancedGrouping by remember { mutableStateOf(prefs.getBoolean("subtitle_advanced_grouping", false)) }
    var autoSelectServer by remember { mutableStateOf(prefs.getBoolean("auto_select_server", false)) }
    var autoFallback by remember { mutableStateOf(prefs.getBoolean("auto_fallback", false)) }

    // Auto-save whenever any value changes
    LaunchedEffect(qualityMode, subEnabled, fontSize, bold, translateTo, bigSkipSec, seekStepSec, watchedThreshold, playerEngine, advancedGrouping, autoSelectServer, autoFallback) {
        prefs.edit {
            putString("quality_mode",      qualityMode)
            putBoolean("sub_enabled",      subEnabled)
            putFloat("sub_size",           fontSize)
            putBoolean("sub_bold",         bold)
            putString("sub_translate",     translateTo)
            putInt("big_skip_sec",         bigSkipSec)
            putInt("seek_step_sec",        seekStepSec)
            putInt("watched_threshold",    watchedThreshold)
            putString("player_engine", playerEngine)
            putBoolean("subtitle_advanced_grouping", advancedGrouping)
            putBoolean("auto_select_server", autoSelectServer)
            putBoolean("auto_fallback", autoFallback)
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
                        "Always plays the best quality, may rebuffer on slow networks",
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

        SectionCard(title = "Player Engine") {
            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "exoplayer" to Triple(
                        "ExoPlayer",
                        "Recommended for most streams",
                        Icons.Filled.PlayCircle
                    ),
                    "libmpv" to Triple(
                        "libmpv",
                        "Use when HLS streams stutter or get stuck on seek",
                        Icons.Filled.Tune
                    ),
                ).forEach { (engine, triple) ->
                    val (label, description, icon) = triple
                    val selected = playerEngine == engine
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                else Color.Transparent
                            )
                            .clickable { playerEngine = engine }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = selected, onClick = { playerEngine = engine })
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

        SectionCard(title = "Playback") {
            SettingsToggleRow(
                label    = "Auto-select server",
                subtitle = "Skip the stream picker and play the first 1080p stream (or the first available)",
                checked  = autoSelectServer,
                onToggle = { autoSelectServer = it }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            SettingsToggleRow(
                label    = "Auto-fallback on error",
                subtitle = "If a stream fails, silently try the next server before showing an error",
                checked  = autoFallback,
                onToggle = { autoFallback = it }
            )
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))

            // Small seek step (the ±X-second buttons)
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Seek step",
                            style      = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            "How far the ± skip buttons jump",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    Text(
                        "${seekStepSec}s",
                        style      = MaterialTheme.typography.labelMedium,
                        color      = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Slider(
                    value         = seekStepSec.toFloat(),
                    onValueChange = { seekStepSec = it.toInt() },
                    valueRange    = 5f..30f,
                    steps         = 4,   // 5 / 10 / 15 / 20 / 25 / 30
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

            var advancedGrouping by remember {
                mutableStateOf(prefs.getBoolean("subtitle_advanced_grouping", false))
            }
            SettingsToggleRow(
                label    = "Advanced subtitle grouping",
                subtitle = "Separate subtitle files by content (e.g. signs vs full dialogue) when available",
                checked  = advancedGrouping,
                onToggle = {
                    advancedGrouping = it
                    prefs.edit { putBoolean("subtitle_advanced_grouping", it) }
                }
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