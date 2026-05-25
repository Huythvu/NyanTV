package com.nyantv.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nyantv.data.TrackedMedia
import androidx.compose.ui.Alignment
import com.nyantv.ui.utils.NetworkStatusContent
import com.nyantv.ui.TrackedCard
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.AppViewModel

private val STATUS_ORDER = listOf("CURRENT", "COMPLETED", "PLANNING", "PAUSED", "DROPPED")
private val STATUS_LABELS = mapOf(
    "CURRENT"   to "Watching",
    "COMPLETED" to "Completed",
    "PLANNING"  to "Planned",
    "PAUSED"    to "On Hold",
    "DROPPED"   to "Dropped"
)

enum class LibrarySortOrder(
    val label: String,
    val ascLabel: String,   // shown when currently ascending  (= what clicking will switch TO desc)
    val descLabel: String   // shown when currently descending (= what clicking will switch TO asc)
) {
    DEFAULT      ("Default",      "Default",       "Default"),
    ALPHABET     ("A – Z",        "A → Z",         "Z → A"),
    AVERAGE_SCORE("Avg. Score",   "Score ↑",       "Score ↓"),
    USER_RATING  ("My Rating",    "Rating ↑",      "Rating ↓"),
    LAST_ADDED   ("Last Added",   "Last Added",    "First Added"),
    LAST_WATCHED ("Last Watched", "Last Watched",  "First Watched")
}

private fun List<TrackedMedia>.applySortOrder(
    order: LibrarySortOrder,
    ascending: Boolean
): List<TrackedMedia> = when (order) {
    LibrarySortOrder.DEFAULT       -> this
    LibrarySortOrder.ALPHABET      ->
        if (ascending) sortedBy           { it.title.lowercase() }
        else           sortedByDescending { it.title.lowercase() }
    LibrarySortOrder.AVERAGE_SCORE ->
        if (ascending) sortedBy           { it.averageScore ?: 0 }
        else           sortedByDescending { it.averageScore ?: 0 }
    LibrarySortOrder.USER_RATING   ->
        if (ascending) sortedBy           { it.score ?: 0f }
        else           sortedByDescending { it.score ?: 0f }
    // API order = newest first; reversed = oldest first
    LibrarySortOrder.LAST_ADDED,
    LibrarySortOrder.LAST_WATCHED  ->
        if (ascending) this else reversed()
}

@Composable
fun LibraryScreen(vm: AppViewModel, navController: NavController, onDetailClick: (String) -> Unit) {
    val animeList    by vm.animeList.collectAsStateWithLifecycle()
    val loggedIn     by vm.isLoggedIn.collectAsStateWithLifecycle()
    val networkState by vm.networkState.collectAsStateWithLifecycle()
    val serviceType  by vm.serviceType.collectAsStateWithLifecycle()

    var selectedTab  by remember { mutableIntStateOf(0) }
    var sortOrder    by remember { mutableStateOf(LibrarySortOrder.DEFAULT) }
    var sortAsc      by remember { mutableStateOf(true) }
    var sortMenuOpen by remember { mutableStateOf(false) }

    val grouped = remember(animeList) {
        STATUS_ORDER.associateWith { status -> animeList.filter { it.watchingStatus == status } }
    }
    val tabs = STATUS_ORDER.filter { (grouped[it]?.size ?: 0) > 0 }

    if (!loggedIn) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Login to see your library", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Button(onClick = { navController.navigate("settings/accounts") }, modifier = Modifier.focusBorder(CircleShape)) {
                        Text("Go to Settings")
                    }
                }
            }
        }
        return
    }

    NetworkStatusContent(
        state       = networkState,
        serviceName = serviceType.name.lowercase().replaceFirstChar { it.uppercase() },
        onRetry     = { vm.retryLoad() }
    ) {
        if (animeList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Your library is empty", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
            return@NetworkStatusContent
        }

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Tab row + sort button ───────────────────────────────────────
            Row(
                modifier          = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTab.coerceAtMost(tabs.lastIndex),
                    edgePadding      = 16.dp,
                    modifier         = Modifier.weight(1f)
                ) {
                    tabs.forEachIndexed { index, status ->
                        val count = grouped[status]?.size ?: 0
                        Tab(
                            selected = selectedTab == index,
                            onClick  = { selectedTab = index },
                            text     = {
                                Text(
                                    "${STATUS_LABELS[status] ?: status} ($count)",
                                    style      = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Box(contentAlignment = Alignment.TopEnd) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        IconButton(
                            onClick  = { sortMenuOpen = true },
                            modifier = Modifier.focusBorder(CircleShape, inset = true)
                        ) {
                            Icon(
                                imageVector        = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort",
                                tint = if (sortOrder != LibrarySortOrder.DEFAULT)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    DropdownMenu(
                        expanded         = sortMenuOpen,
                        onDismissRequest = { sortMenuOpen = false }
                    ) {
                        LibrarySortOrder.entries.forEach { order ->
                            val isActive = sortOrder == order

                            // Show the *current* state label so the user sees what's active,
                            // and knows that clicking will flip it.
                            val displayLabel = when {
                                order == LibrarySortOrder.DEFAULT -> order.label
                                !isActive                        -> order.label
                                sortAsc                          -> order.ascLabel
                                else                             -> order.descLabel
                            }

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        displayLabel,
                                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                        color      = if (isActive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    if (isActive && order != LibrarySortOrder.DEFAULT) {
                                        sortAsc = !sortAsc        // same option → flip direction
                                    } else {
                                        sortOrder = order
                                        sortAsc   = true          // new option → start ascending
                                    }
                                    sortMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // ── List ────────────────────────────────────────────────────────
            val currentStatus = tabs.getOrNull(selectedTab) ?: return@NetworkStatusContent
            val rawItems      = grouped[currentStatus] ?: emptyList()
            val items         = remember(rawItems, sortOrder, sortAsc) {
                rawItems.applySortOrder(sortOrder, sortAsc)
            }

            LazyColumn(
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(items, key = TrackedMedia::id) { tracked ->
                    TrackedCard(
                        item    = tracked,
                        onClick = { onDetailClick(tracked.id) }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color    = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                    )
                }
            }
        }
    }
}