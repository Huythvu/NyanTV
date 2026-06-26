package com.nyantv.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nyantv.ui.MediaCard
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.BrowseViewModel

private val GENRES = listOf(
    "Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy", "Horror", "Mahou Shoujo",
    "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi", "Slice of Life",
    "Sports", "Supernatural", "Thriller",
)
private val SORTS = listOf(
    "TRENDING_DESC"   to "Trending",
    "POPULARITY_DESC" to "Popular",
    "SCORE_DESC"      to "Top Rated",
    "START_DATE_DESC" to "Newest",
    "TITLE_ROMAJI"    to "A – Z",
)
private val FORMATS = listOf(
    null       to "Any format",
    "TV"       to "TV",
    "MOVIE"    to "Movie",
    "OVA"      to "OVA",
    "ONA"      to "ONA",
    "SPECIAL"  to "Special",
    "TV_SHORT" to "Short",
)

@Composable
fun BrowseScreen(navController: NavController, onDetailClick: (String) -> Unit) {
    val vm: BrowseViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Load the next page as the user nears the end of the grid.
    LaunchedEffect(gridState, state.results.size, state.endReached) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && last >= state.results.size - 6) vm.loadMore()
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {

        // ── Search ────────────────────────────────────────────────────────────
        SearchBar(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusBorder(MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, inset = true),
            onClick = { navController.navigate("search") },
        )

        Spacer(Modifier.height(12.dp))

        // ── Sort + Format selectors ───────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterDropdown(
                label    = SORTS.firstOrNull { it.first == state.filters.sort }?.second ?: "Sort",
                options  = SORTS.map { it.second },
                onSelect = { idx -> vm.setSort(SORTS[idx].first) },
            )
            FilterDropdown(
                label    = FORMATS.firstOrNull { it.first == state.filters.format }?.second ?: "Format",
                options  = FORMATS.map { it.second },
                onSelect = { idx -> vm.setFormat(FORMATS[idx].first) },
            )
        }

        Spacer(Modifier.height(8.dp))

        // ── Genre chips ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GENRES.forEach { genre ->
                val selected = genre in state.filters.genres
                FilterChip(
                    selected = selected,
                    onClick  = { vm.toggleGenre(genre) },
                    label    = { Text(genre, style = MaterialTheme.typography.labelMedium) },
                    modifier = Modifier.focusBorder(MaterialTheme.shapes.small, inset = true),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Results grid ──────────────────────────────────────────────────────
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            when {
                state.loading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                state.results.isEmpty() -> Text(
                    "No anime match these filters.",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> LazyVerticalGrid(
                    state                 = gridState,
                    columns               = GridCells.Adaptive(120.dp),
                    contentPadding        = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement   = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.results, key = { it.id }) { media ->
                        MediaCard(media = media, onClick = { onDetailClick(media.id) })
                    }
                }
            }
            if (state.loadingMore) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(8.dp)
                        .size(28.dp),
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

@Composable
private fun SearchBar(modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick        = onClick,
        modifier       = modifier,
        shape          = MaterialTheme.shapes.large,
        color          = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        border         = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Text(
                "Search anime…",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun FilterDropdown(label: String, options: List<String>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.focusBorder(MaterialTheme.shapes.small)) {
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { idx, opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(idx); expanded = false })
            }
        }
    }
}
