package com.nyantv.ui.settings.sub_settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nyantv.ui.utils.SectionCard
import com.nyantv.ui.utils.SubScreenHeader
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.AppViewModel
import com.nyantv.viewmodel.ExtensionViewModel

@Composable
fun TrackingScreen(
    vm: AppViewModel,
    navController: NavController,
    extVm: ExtensionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val playerPrefs = remember { context.getSharedPreferences("nyantv_player_prefs", Context.MODE_PRIVATE) }

    val trackingMode  by vm.trackingMode.collectAsStateWithLifecycle()
    val autoComplete  by vm.autoCompleteTracking.collectAsStateWithLifecycle()
    val askOnce       by vm.askOncePerSeries.collectAsStateWithLifecycle()
    val excluded      by vm.excludedTrackingExts.collectAsStateWithLifecycle()
    val installed     by extVm.installedExtensions.collectAsStateWithLifecycle()

    var markEarlier by remember { mutableStateOf(playerPrefs.getBoolean("track_mark_earlier", false)) }
    var threshold   by remember { mutableIntStateOf(playerPrefs.getInt("watched_threshold", 80)) }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SubScreenHeader(title = "Tracking", navController = navController)

        // ── Automation ───────────────────────────────────────────────────────
        SectionCard(title = "Tracking Automation") {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(
                    AppViewModel.TrackingMode.ALWAYS_ASK  to "Always ask for tracking permission",
                    AppViewModel.TrackingMode.ALWAYS_AUTO to "Always track automatically",
                    AppViewModel.TrackingMode.NEVER_AUTO  to "Never track automatically",
                ).forEach { (mode, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (trackingMode == mode) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else Color.Transparent)
                            .clickable { vm.setTrackingMode(mode) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = trackingMode == mode, onClick = { vm.setTrackingMode(mode) })
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        // ── Options ──────────────────────────────────────────────────────────
        SectionCard(title = "Options") {
            Column(modifier = Modifier.padding(8.dp)) {
                ToggleRow(
                    "Mark earlier episodes watched",
                    "Starting an episode marks all earlier ones watched too",
                    markEarlier,
                ) { markEarlier = it; playerPrefs.edit { putBoolean("track_mark_earlier", it) } }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ToggleRow(
                    "Auto-complete series",
                    "Set status to Completed when you finish the last episode",
                    autoComplete,
                ) { vm.setAutoCompleteTracking(it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                ToggleRow(
                    "Ask once per series",
                    "In ‘Always ask’ mode, remember your answer per anime",
                    askOnce,
                ) { vm.setAskOncePerSeries(it) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                StepperRow(
                    label     = "Watched threshold",
                    valueText = "$threshold%",
                    onMinus   = { threshold = (threshold - 5).coerceAtLeast(50); playerPrefs.edit { putInt("watched_threshold", threshold) } },
                    onPlus    = { threshold = (threshold + 5).coerceAtMost(100); playerPrefs.edit { putInt("watched_threshold", threshold) } },
                )
            }
        }

        // ── Per-extension exclusion ──────────────────────────────────────────
        SectionCard(title = "Exclude extensions from tracking") {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    "Entries played from a toggled-on extension won't be tracked, even with auto-tracking on.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (installed.isEmpty()) {
                    Text(
                        "No extensions installed.",
                        style    = MaterialTheme.typography.bodySmall,
                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                } else {
                    installed.forEachIndexed { index, ext ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ToggleRow(ext.name, null, ext.pkgName in excluded) {
                            vm.setExtensionTrackingExcluded(ext.pkgName, it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            Switch(checked = checked, onCheckedChange = onToggle, modifier = Modifier.focusBorder(RoundedCornerShape(50)))
        }
    }
}

@Composable
private fun StepperRow(label: String, valueText: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onMinus, modifier = Modifier.focusBorder(RoundedCornerShape(50))) { Text("–") }
            Text(valueText, style = MaterialTheme.typography.titleMedium, modifier = Modifier.widthIn(min = 48.dp))
            OutlinedButton(onClick = onPlus, modifier = Modifier.focusBorder(RoundedCornerShape(50))) { Text("+") }
        }
    }
}
