package com.nyantv.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nyantv.ui.MediaCard
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.ScheduleViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Weekly airing schedule for all anime (AniList public data). Day selector + per-day grid. */
@Composable
fun ScheduleScreen(onDetailClick: (String) -> Unit) {
    val vm: ScheduleViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    var selectedDay by remember { mutableIntStateOf(0) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
        Text(
            "Schedule",
            style      = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier   = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Couldn't load the schedule.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.load() }, modifier = Modifier.focusBorder(RoundedCornerShape(50))) { Text("Retry") }
                }
            }
            else -> {
                // ── Day selector ─────────────────────────────────────────────
                LazyRow(
                    contentPadding        = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.days.size) { index ->
                        val day = state.days[index]
                        val selected = index == selectedDay
                        FilterChip(
                            selected = selected,
                            onClick  = { selectedDay = index },
                            modifier = Modifier.focusBorder(RoundedCornerShape(8.dp), inset = true),
                            label    = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(day.label, style = MaterialTheme.typography.labelLarge)
                                    Text(day.date, style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                }
                            },
                        )
                    }
                }

                val entries = state.days.getOrNull(selectedDay)?.entries.orEmpty()
                if (entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Nothing airing this day.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                } else {
                    BoxWithConstraints(Modifier.fillMaxSize()) {
                        val cols = maxOf(1, (maxWidth / 128.dp).toInt())
                        LazyVerticalGrid(
                            columns               = GridCells.Fixed(cols),
                            contentPadding        = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement   = Arrangement.spacedBy(12.dp),
                        ) {
                            itemsIndexed(entries, key = { i, e -> "${e.media.id}_${e.episode}_$i" }) { _, entry ->
                                var focused by remember { mutableStateOf(false) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    MediaCard(
                                        media    = entry.media,
                                        onClick  = { onDetailClick(entry.media.id) },
                                        modifier = Modifier
                                            .onFocusChanged { focused = it.hasFocus }
                                            .then(
                                                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                                else Modifier
                                            ),
                                    )
                                    Text(
                                        "EP ${entry.episode} · ${timeFmt.format(Date(entry.airingAtSec * 1000L))}",
                                        style    = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
