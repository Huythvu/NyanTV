package com.nyantv.ui.settings.sub_settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.nyantv.player.WatchedEntry
import kotlinx.coroutines.delay
import com.nyantv.ui.utils.SubScreenHeader
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.AppViewModel

@Composable
fun WatchHistoryScreen(vm: AppViewModel, navController: NavController) {
    var entries     by remember { mutableStateOf(vm.watchHistoryEntries()) }
    var confirmClear by remember { mutableStateOf(false) }
    var importTick  by remember { mutableStateOf(0) }

    fun reload() { entries = vm.watchHistoryEntries() }

    // After triggering an import (async), refresh the list once it's had time to populate.
    LaunchedEffect(importTick) {
        if (importTick == 0) return@LaunchedEffect
        delay(1500)
        reload()
    }

    LazyColumn(
        modifier            = Modifier.fillMaxSize(),
        contentPadding      = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SubScreenHeader(title = "Watch History", navController = navController) }
        item {
            Text(
                "Anime you've watched locally (including non-syncable extension entries). " +
                    "Removing one clears its watched episodes and resume progress.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
        }
        item {
            OutlinedButton(
                onClick  = { vm.importExistingHistory(force = true); importTick++ },
                modifier = Modifier.focusBorder(MaterialTheme.shapes.small),
            ) { Text("Import past progress") }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    "No watch history yet.",
                    style    = MaterialTheme.typography.bodyMedium,
                    color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        } else {
            item {
                OutlinedButton(
                    onClick  = { confirmClear = true },
                    modifier = Modifier.focusBorder(MaterialTheme.shapes.small),
                    colors   = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Clear all history") }
            }
            items(entries, key = { it.id }) { entry ->
                WatchHistoryRow(
                    entry    = entry,
                    onRemove = { vm.removeFromLocalWatch(entry.id); reload() },
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title            = { Text("Clear all history?") },
            text             = { Text("This removes every locally tracked anime and its resume progress. Your account lists aren't affected.") },
            confirmButton    = {
                TextButton(onClick = { vm.clearAllWatchHistory(); reload(); confirmClear = false }) {
                    Text("Clear all", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton    = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun WatchHistoryRow(entry: WatchedEntry, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium) {
        Row(
            modifier              = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model              = entry.poster,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(40.dp, 56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.title,
                    style    = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Episode ${entry.episode.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconButton(
                    onClick  = onRemove,
                    modifier = Modifier.focusBorder(CircleShape, inset = true),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Remove ${entry.title}",
                        tint               = MaterialTheme.colorScheme.error,
                        modifier           = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
