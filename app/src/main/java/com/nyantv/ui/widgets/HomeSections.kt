package com.nyantv.ui.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nyantv.data.Media
import com.nyantv.data.ServiceType
import com.nyantv.data.TrackedMedia
import com.nyantv.ui.SectionRow
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.AppViewModel

// "Seasonal Anime   ◀ Summer 2026 ▶" — D-pad onto an arrow and press to change season.
@Composable
private fun SeasonSwitcherHeader(label: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier              = Modifier.padding(horizontal = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text       = "Seasonal Anime",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        SeasonArrow(Icons.Filled.KeyboardArrowLeft, "Previous season", onPrev)
        Text(
            text  = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        SeasonArrow(Icons.Filled.KeyboardArrowRight, "Next season", onNext)
    }
}

@Composable
private fun SeasonArrow(icon: ImageVector, description: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .size(32.dp)
            .focusBorder(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onClick,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            modifier           = Modifier.size(22.dp),
            tint               = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
fun HomeSections(vm: AppViewModel, navController: NavController, onDetailClick: (String) -> Unit) {
    val service         by vm.serviceType.collectAsStateWithLifecycle()
    val animeList       by vm.animeList.collectAsStateWithLifecycle()
    val trending        by vm.trending.collectAsStateWithLifecycle()
    val popular         by vm.popular.collectAsStateWithLifecycle()
    val upcoming        by vm.upcoming.collectAsStateWithLifecycle()
    val trendingMovies  by vm.trendingMovies.collectAsStateWithLifecycle()
    val trendingShows   by vm.trendingShows.collectAsStateWithLifecycle()

    val seasonal        by vm.seasonal.collectAsStateWithLifecycle()
    val seasonLabel     by vm.seasonLabel.collectAsStateWithLifecycle()

    val anilistContinue by vm.anilistShowContinue.collectAsStateWithLifecycle()
    val anilistPlanned  by vm.anilistShowPlanned.collectAsStateWithLifecycle()
    val anilistTrending by vm.anilistShowTrending.collectAsStateWithLifecycle()
    val anilistPopular  by vm.anilistShowPopular.collectAsStateWithLifecycle()
    val malContinue     by vm.malShowContinue.collectAsStateWithLifecycle()
    val malPlanned      by vm.malShowPlanned.collectAsStateWithLifecycle()
    val malTrending     by vm.malShowTrending.collectAsStateWithLifecycle()
    val malPopular      by vm.malShowPopular.collectAsStateWithLifecycle()
    val malSeasonal     by vm.malShowSeasonal.collectAsStateWithLifecycle()
    val malUpcoming     by vm.malShowUpcoming.collectAsStateWithLifecycle()
    val malOrder        by vm.malHomeOrder.collectAsStateWithLifecycle()
    val simklContMovies by vm.simklShowContMovies.collectAsStateWithLifecycle()
    val simklPlanMovies by vm.simklShowPlanMovies.collectAsStateWithLifecycle()
    val simklContSeries by vm.simklShowContSeries.collectAsStateWithLifecycle()
    val simklPlanSeries by vm.simklShowPlanSeries.collectAsStateWithLifecycle()

    val localContinue by vm.localContinue.collectAsStateWithLifecycle()
    var showManage by remember { mutableStateOf(false) }

    val trackedMap = animeList.associateBy { it.id }

    fun TrackedMedia.toMedia() = Media(
        id           = id,
        title        = title,
        poster       = poster,
        episodes     = totalEpisodes,
        averageScore = averageScore?.takeIf { it > 0f },
        status       = status,
        serviceType  = service,
        idMal        = if (service == ServiceType.MAL) id else null,
    )
    fun List<TrackedMedia>.toMedia() = map { it.toMedia() }
    fun navigate(id: String) = onDetailClick(id)

    when (service) {
        ServiceType.ANILIST -> {
            val watching = animeList.filter { it.watchingStatus == "CURRENT" }
            val planned  = animeList.filter { it.watchingStatus == "PLANNING" }
            if (localContinue.isNotEmpty()) {
                SectionRow(
                    title       = "Continue Watching",
                    items       = localContinue.take(30),
                    onItemClick = { navigate(it.id) },
                    count       = localContinue.size,
                    header      = { ContinueWatchingHeader(localContinue.size) { showManage = true } },
                )
            }
            if (anilistContinue && watching.isNotEmpty()) {
                SectionRow(title = "Watching Anime", items = watching.toMedia(), onItemClick = { navigate(it.id) }, trackedMap = trackedMap, count = watching.size)
            }
            if (anilistPlanned && planned.isNotEmpty()) {
                SectionRow(title = "Planned Anime", items = planned.toMedia(), onItemClick = { navigate(it.id) }, trackedMap = trackedMap, count = planned.size)
            }
            if (anilistTrending) SectionRow(title = "Trending Now",  items = trending, onItemClick = { navigate(it.id) })
            if (anilistPopular)  SectionRow(title = "Popular Anime", items = popular,  onItemClick = { navigate(it.id) })
        }

        ServiceType.MAL -> {
            val watching = animeList.filter { it.watchingStatus == "CURRENT" }
            val planned  = animeList.filter { it.watchingStatus == "PLANNING" }
            if (localContinue.isNotEmpty()) {
                SectionRow(
                    title       = "Continue Watching",
                    items       = localContinue.take(30),
                    onItemClick = { navigate(it.id) },
                    count       = localContinue.size,
                    header      = { ContinueWatchingHeader(localContinue.size) { showManage = true } },
                )
            }
            // Render rows in the user-configured order (Settings → Manage MyAnimeList Homescreen).
            malOrder.forEach { key ->
                when (key) {
                    "continue" -> if (malContinue && watching.isNotEmpty()) {
                        SectionRow(title = "Watching Anime", items = watching.toMedia(), onItemClick = { navigate(it.id) }, trackedMap = trackedMap, count = watching.size)
                    }
                    "planned"  -> if (malPlanned && planned.isNotEmpty()) {
                        SectionRow(title = "Planned Anime", items = planned.toMedia(), onItemClick = { navigate(it.id) }, trackedMap = trackedMap, count = planned.size)
                    }
                    "trending" -> if (malTrending) SectionRow(title = "Trending Now",  items = trending, onItemClick = { navigate(it.id) })
                    "popular"  -> if (malPopular)  SectionRow(title = "Popular Anime", items = popular,  onItemClick = { navigate(it.id) })
                    "upcoming" -> if (malUpcoming) SectionRow(title = "Upcoming",      items = upcoming, onItemClick = { navigate(it.id) })
                    "seasonal" -> if (malSeasonal) {
                        SectionRow(
                            title       = "Seasonal Anime",
                            items       = seasonal,
                            onItemClick = { navigate(it.id) },
                            header      = {
                                SeasonSwitcherHeader(
                                    label  = seasonLabel.ifBlank { "Seasonal Anime" },
                                    onPrev = { vm.seasonShift(-1) },
                                    onNext = { vm.seasonShift(1) },
                                )
                            },
                        )
                    }
                }
            }
        }

        ServiceType.SIMKL -> {
            val contMovies = animeList.filter { it.watchingStatus == "CURRENT"  && it.isMovie == true }
            val planMovies = animeList.filter { it.watchingStatus == "PLANNING" && it.isMovie == true }
            val contSeries = animeList.filter { it.watchingStatus == "CURRENT"  && it.isMovie != true }
            val planSeries = animeList.filter { it.watchingStatus == "PLANNING" && it.isMovie != true }

            if (simklContMovies && contMovies.isNotEmpty()) {
                SectionRow(title = "Continue Watching (Movies)", items = contMovies.toMedia(), onItemClick = { navigate(it.id) }, trackedMap = trackedMap, count = contMovies.size)
            }
            if (simklPlanMovies && planMovies.isNotEmpty()) {
                SectionRow(title = "Planned Movies", items = planMovies.toMedia(), onItemClick = { navigate(it.id) }, trackedMap = trackedMap, count = planMovies.size)
            }
            if (simklContSeries && contSeries.isNotEmpty()) {
                SectionRow(title = "Continue Watching (Series)", items = contSeries.toMedia(), onItemClick = { navigate(it.id) }, trackedMap = trackedMap, count = contSeries.size)
            }
            if (simklPlanSeries && planSeries.isNotEmpty()) {
                SectionRow(title = "Planned Series", items = planSeries.toMedia(), onItemClick = { navigate(it.id) }, trackedMap = trackedMap, count = planSeries.size)
            }
            if (trendingMovies.isNotEmpty()) {
                SectionRow(title = "Trending Movies", items = trendingMovies, onItemClick = { navigate(it.id) })
            }
            if (trendingShows.isNotEmpty()) {
                SectionRow(title = "Trending Shows", items = trendingShows, onItemClick = { navigate(it.id) })
            }
        }
    }

    if (showManage) {
        ManageWatchListDialog(
            items     = localContinue,
            onRemove  = { vm.removeFromLocalWatch(it) },
            onDismiss = { showManage = false },
        )
    }
}

// "Continue Watching   N        ✎" — the pencil opens the manage dialog for quick removal.
@Composable
private fun ContinueWatchingHeader(count: Int, onManage: () -> Unit) {
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text       = "Continue Watching",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color      = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text  = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .size(32.dp)
                .focusBorder(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onManage,
                ),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Outlined.Edit,
                contentDescription = "Manage continue watching",
                modifier           = Modifier.size(20.dp),
                tint               = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun ManageWatchListDialog(
    items:     List<Media>,
    onRemove:  (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Continue Watching") },
        text = {
            if (items.isEmpty()) {
                Text("Nothing here yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    items.forEachIndexed { index, media ->
                        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        Row(
                            modifier              = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                media.title,
                                style    = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick  = { onRemove(media.id) },
                                modifier = Modifier.focusBorder(CircleShape, inset = true),
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Remove ${media.title}",
                                    tint               = MaterialTheme.colorScheme.error,
                                    modifier           = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
