package com.nyantv.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.nyantv.data.Media
import com.nyantv.data.TrackedMedia
import com.nyantv.ui.utils.ScoreBadge

// ─── Airing status badge ──────────────────────────────────────────────────────

/** Airing states across services, each with the icon + colour shown on cards. */
enum class AiringState(val key: String, val label: String, val icon: ImageVector, val color: Color) {
    AIRING   ("airing",    "Currently Airing", Icons.Filled.FiberManualRecord, Color(0xFF4CAF50)),
    FINISHED ("finished",  "Finished Airing",  Icons.Filled.CheckCircle,       Color(0xFF42A5F5)),
    NOT_YET  ("not_yet",   "Not Yet Aired",    Icons.Filled.Schedule,          Color(0xFFFFB300)),
    CANCELLED("cancelled", "Cancelled",        Icons.Filled.Cancel,            Color(0xFFEF5350)),
    HIATUS   ("hiatus",    "Hiatus",           Icons.Filled.PauseCircle,       Color(0xFF9E9E9E)),
}

/** Maps a service's status string (AniList / MAL / Simkl vocabularies) to an [AiringState]. */
fun airingStateOf(status: String?): AiringState? {
    val s = status?.uppercase() ?: return null
    return when {
        "CANCEL" in s                                          -> AiringState.CANCELLED
        "HIATUS" in s                                          -> AiringState.HIATUS
        "NOT YET" in s || "NOT_YET" in s || "UPCOMING" in s    -> AiringState.NOT_YET
        // Check FINISHED before AIRING — MAL's "FINISHED AIRING" contains "AIRING".
        "FINISHED" in s || "ENDED" in s || "RELEASED" in s     -> AiringState.FINISHED
        "RELEASING" in s || "AIRING" in s || "CONTINUING" in s || "ONGOING" in s -> AiringState.AIRING
        else -> null
    }
}

/** Which status badges to render on cards; provided app-wide so cards needn't take params. */
data class CardStatusConfig(
    val show:   Boolean     = true,
    val states: Set<String> = AiringState.entries.map { it.key }.toSet(),
)
val LocalCardStatusConfig = androidx.compose.runtime.compositionLocalOf { CardStatusConfig() }

// ─── Media card (poster + title + score) ──────────────────────────────────────

@Composable
fun MediaCard(
    media: Media,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 120.dp,
    trackedMap: Map<String, TrackedMedia> = emptyMap()
) {
    val tracked = trackedMap[media.id]
    val hasProgress = tracked != null
    val hasScore    = (media.averageScore ?: 0) > 0

    Box(
        modifier = modifier
            .width(width)
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model              = media.poster,
            contentDescription = media.title,
            contentScale       = ContentScale.Crop,
            modifier           = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.45f)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))
                )
        )

        if (hasProgress || hasScore) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                if (hasProgress) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(9.dp)
                    )
                    Text(
                        text  = "${tracked.episodeCount ?: 0}/${tracked.totalEpisodes?.takeIf { it > 0 } ?: "?"}",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White
                    )
                }
                if (hasProgress && hasScore) {
                    Text("|", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.5f))
                }
                if (hasScore) {
                    Text(
                        text  = "%.1f".format(media.averageScore!! / 10f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White
                    )
                    Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(9.dp))
                } else {
                    Text("N/A", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = Color.White.copy(alpha = 0.5f))
                    Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), modifier = Modifier.size(9.dp))
                }
            }
        }

        Text(
            text     = media.title,
            style    = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color    = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 6.dp, vertical = 6.dp)
        )

        // ── Airing-status badge (top-left) ────────────────────────────────
        val statusCfg = LocalCardStatusConfig.current
        val airing = if (statusCfg.show) airingStateOf(media.status)?.takeIf { it.key in statusCfg.states } else null
        airing?.let { st ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .clip(RoundedCornerShape(bottomEnd = 4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .padding(4.dp),
            ) {
                Icon(
                    st.icon,
                    contentDescription = st.label,
                    tint     = st.color,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

// ─── Tracked media card (for Library) ─────────────────────────────────────────

@Composable
fun TrackedCard(
    item: TrackedMedia,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasAvgScore = (item.averageScore ?: 0) > 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // ── Poster with avg-score badge ──────────────────────────────────
        Box(
            modifier = Modifier
                .size(60.dp, 85.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            AsyncImage(
                model              = item.poster,
                contentDescription = item.title,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier.fillMaxSize()
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(RoundedCornerShape(bottomStart = 4.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.75f))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (hasAvgScore) {
                    Text(
                        text  = "%.1f".format(item.averageScore!! / 10f),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White
                    )
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(8.dp)
                    )
                } else {
                    Text(
                        text  = "N/A",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = Color.White.copy(alpha = 0.5f)
                    )
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(8.dp)
                    )
                }
            }
        }

        // ── Text info ────────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                item.title,
                style      = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines   = 2,
                overflow   = TextOverflow.Ellipsis,
                color      = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "${item.episodeCount ?: 0} / ${item.totalEpisodes?.takeIf { it > 0 } ?: "?"} ep",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            item.watchingStatus?.let {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        it.lowercase().replaceFirstChar { c -> c.uppercase() },
                        style    = MaterialTheme.typography.labelSmall,
                        color    = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // ── User score ────────────────────────────────────────
        item.score?.takeIf { it > 0 }?.let {
            Text(
                "★ ${"%.1f".format(it)}",
                style      = MaterialTheme.typography.labelMedium,
                color      = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ─── Horizontal section row ────────────────────────────────────────────────────

@Composable
fun SectionRow(
    modifier: Modifier = Modifier,
    title: String,
    items: List<Media>,
    onItemClick: (Media) -> Unit,
    trackedMap: Map<String, TrackedMedia> = emptyMap(),
    cardWidth: Dp = 120.dp,
    header: (@Composable () -> Unit)? = null,
) {
    // Sections with a custom header (e.g. the season switcher) stay visible even when empty
    // so their controls remain reachable; plain rows collapse when there's nothing to show.
    if (items.isEmpty() && header == null) return
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (header != null) {
            header()
        } else {
            Text(
                text       = title,
                style      = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color      = MaterialTheme.colorScheme.onBackground,
                modifier   = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (items.isEmpty()) {
            Text(
                text     = "No premieres found for this season",
                style    = MaterialTheme.typography.bodySmall,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            LazyRow(
                contentPadding        = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items.distinctBy { it.id }, key = { it.id }) { media ->
                    MediaCard(media = media, onClick = { onItemClick(media) }, width = cardWidth, trackedMap = trackedMap)
                }
            }
        }
    }
}

// ─── Progress bar (ep progress) ───────────────────────────────────────────────

@Composable
fun EpisodeProgressBar(current: Int, total: Int?) {
    val progress = if (total != null && total > 0) current.toFloat() / total else 0f
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        LinearProgressIndicator(
            progress   = { progress },
            modifier   = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)),
            color      = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainer
        )
        Text(
            "$current / ${total ?: "?"} episodes",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}