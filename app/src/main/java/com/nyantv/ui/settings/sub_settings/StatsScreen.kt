package com.nyantv.ui.settings.sub_settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nyantv.ui.utils.SectionCard
import com.nyantv.ui.utils.SubScreenHeader
import com.nyantv.viewmodel.AppViewModel

/** Read-only statistics: tracking-service profile + your list breakdown + local watch history. */
@Composable
fun StatsScreen(vm: AppViewModel, navController: NavController) {
    val profile   by vm.profile.collectAsStateWithLifecycle()
    val animeList by vm.animeList.collectAsStateWithLifecycle()
    val localCount = remember(vm) { vm.watchHistoryEntries().size }

    // Friendly label + display order for the statuses we know; anything else falls through as-is.
    val statusOrder = listOf(
        "CURRENT" to "Watching", "WATCHING" to "Watching",
        "REPEATING" to "Rewatching",
        "COMPLETED" to "Completed",
        "PLANNING" to "Planned", "PLAN TO WATCH" to "Planned",
        "PAUSED" to "On Hold", "ON HOLD" to "On Hold",
        "DROPPED" to "Dropped",
    )
    val counts = animeList.groupingBy { it.watchingStatus ?: "UNKNOWN" }.eachCount()
    // Merge synonymous statuses under one friendly label, preserving the order above.
    val breakdown = linkedMapOf<String, Int>()
    statusOrder.forEach { (raw, label) ->
        val c = counts[raw] ?: return@forEach
        breakdown[label] = (breakdown[label] ?: 0) + c
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SubScreenHeader(title = "Statistics", navController = navController)

        SectionCard(title = "Profile") {
            Column(Modifier.padding(8.dp)) {
                profile?.name?.let { StatRow("Account", it) }
                StatRow("Episodes watched", profile?.episodesWatched?.toString() ?: "—")
                StatRow("Mean score", profile?.meanScore?.takeIf { it > 0f }?.let { "%.1f".format(it) } ?: "—")
                StatRow("Anime on list", (profile?.animeCount ?: animeList.size).toString())
            }
        }

        SectionCard(title = "Your List") {
            Column(Modifier.padding(8.dp)) {
                if (breakdown.isEmpty()) {
                    Text(
                        "No tracked anime yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.padding(8.dp),
                    )
                } else {
                    StatRow("Total", animeList.size.toString(), emphasize = true)
                    breakdown.forEach { (label, count) -> StatRow(label, count.toString()) }
                }
            }
        }

        SectionCard(title = "Local") {
            Column(Modifier.padding(8.dp)) {
                StatRow("Shows in local history", localCount.toString())
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, emphasize: Boolean = false) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
        )
        Text(
            value,
            style      = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color      = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
