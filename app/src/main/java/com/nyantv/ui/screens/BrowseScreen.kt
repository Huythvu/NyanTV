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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.input.ImeAction
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
import com.nyantv.viewmodel.AppViewModel
import com.nyantv.viewmodel.BrowseViewModel
import eu.kanade.tachiyomi.animesource.model.AnimeFilter

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
private val STATUSES = listOf(
    null               to "Any status",
    "RELEASING"        to "Airing",
    "FINISHED"         to "Finished",
    "NOT_YET_RELEASED" to "Coming Soon",
    "HIATUS"           to "Hiatus",
    "CANCELLED"        to "Cancelled",
)
private val SEASONS = listOf(
    null     to "Any season",
    "WINTER" to "Winter",
    "SPRING" to "Spring",
    "SUMMER" to "Summer",
    "FALL"   to "Fall",
)
private val YEARS: List<Pair<Int?, String>> = buildList {
    add(null to "Any year")
    val now = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)
    for (y in now downTo 1960) add(y to y.toString())
}

@Composable
fun BrowseScreen(navController: NavController, appVm: AppViewModel, onDetailClick: (String) -> Unit) {
    val vm: BrowseViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    val extensionMode = state.selectedSourceId != null
    // Source dropdown: AniList first, then each installed extension.
    val sourceLabels = remember(state.extSources) {
        listOf("AniList") + state.extSources.map { it.name }
    }
    val selectedSourceLabel = state.extSources.firstOrNull { it.id == state.selectedSourceId }?.name ?: "AniList"

    // Load the next page as the user nears the end of the grid.
    LaunchedEffect(gridState, state.results.size, state.endReached) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { last ->
                if (last != null && last >= state.results.size - 6) vm.loadMore()
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(top = 16.dp)) {

        // ── Search ────────────────────────────────────────────────────────────
        // In extension mode, search within the selected extension's catalog;
        // otherwise jump to the global (AniList) search screen.
        if (extensionMode) {
            ExtensionSearchBar(
                query    = state.extQuery,
                onSubmit = { vm.setExtensionQuery(it) },
                onClear  = { vm.setExtensionQuery("") },
            )
        } else {
            SearchBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusBorder(MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, inset = true),
                onClick = { navController.navigate("search") },
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Sort / Season / Year / Format / Source selectors ──────────────────
        // AniList filters don't apply to an extension catalog, so hide them in
        // extension mode and keep only the Source dropdown (always last).
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!extensionMode) {
                FilterDropdown(
                    label    = SORTS.firstOrNull { it.first == state.filters.sort }?.second ?: "Sort",
                    options  = SORTS.map { it.second },
                    onSelect = { idx -> vm.setSort(SORTS[idx].first) },
                )
                FilterDropdown(
                    label    = STATUSES.firstOrNull { it.first == state.filters.status }?.second ?: "Status",
                    options  = STATUSES.map { it.second },
                    onSelect = { idx -> vm.setStatus(STATUSES[idx].first) },
                )
                FilterDropdown(
                    label    = SEASONS.firstOrNull { it.first == state.filters.season }?.second ?: "Season",
                    options  = SEASONS.map { it.second },
                    onSelect = { idx -> vm.setSeason(SEASONS[idx].first) },
                )
                FilterDropdown(
                    label    = YEARS.firstOrNull { it.first == state.filters.year }?.second ?: "Year",
                    options  = YEARS.map { it.second },
                    onSelect = { idx -> vm.setYear(YEARS[idx].first) },
                )
                FilterDropdown(
                    label    = FORMATS.firstOrNull { it.first == state.filters.format }?.second ?: "Format",
                    options  = FORMATS.map { it.second },
                    onSelect = { idx -> vm.setFormat(FORMATS[idx].first) },
                )
            }
            FilterDropdown(
                label    = selectedSourceLabel,
                options  = sourceLabels,
                onSelect = { idx -> vm.selectSource(if (idx == 0) null else state.extSources[idx - 1].id) },
            )
            // Popular vs Latest (most recent) — only for extensions that offer a Latest feed.
            if (extensionMode && state.extSupportsLatest) {
                FilterDropdown(
                    label    = if (state.extLatest) "Latest" else "Popular",
                    options  = listOf("Popular", "Latest"),
                    onSelect = { idx -> vm.setExtensionLatest(idx == 1) },
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Genre chips (AniList mode only) ───────────────────────────────────
        if (!extensionMode) {
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
        }

        // ── Extension's own filters / sort (extension mode only) ──────────────
        if (extensionMode && state.extFilters.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                state.extFilters.forEach { filter ->
                    key(filter) { ExtFilterControl(filter) { vm.onExtFilterChanged() } }
                }
            }

            Spacer(Modifier.height(12.dp))
        }

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
                        MediaCard(
                            media   = media,
                            onClick = {
                                if (extensionMode) {
                                    vm.resolveAndOpen(
                                        media      = media,
                                        // Matched on AniList → rich detail page.
                                        onResolved = { onDetailClick(it) },
                                        // No AniList match → still watchable from the extension,
                                        // just with sparse metadata.
                                        onFailed   = {
                                            appVm.registerExternalMedia(media)
                                            onDetailClick(media.id)
                                        },
                                    )
                                } else {
                                    onDetailClick(media.id)
                                }
                            },
                        )
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
            // Resolving an extension result to its AniList entry before opening detail.
            if (state.resolving) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
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

/** Free-text search box for the current extension's catalog (TV-friendly, submit-on-enter). */
@Composable
private fun ExtensionSearchBar(query: String, onSubmit: (String) -> Unit, onClear: () -> Unit) {
    var text by remember(query) { mutableStateOf(query) }
    Row(
        modifier              = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value           = text,
            onValueChange   = { text = it; if (it.isBlank()) onClear() },
            placeholder     = { Text("Search this extension…", style = MaterialTheme.typography.bodyMedium) },
            singleLine      = true,
            leadingIcon     = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit(text.trim()) }),
            shape           = MaterialTheme.shapes.large,
            modifier        = Modifier
                .weight(1f)
                .focusBorder(MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, inset = true),
        )
        if (text.isNotEmpty()) {
            IconButton(onClick = { text = ""; onClear() }) {
                Icon(Icons.Filled.Close, contentDescription = "Clear search")
            }
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

/**
 * Renders a single Aniyomi extension filter as a TV-friendly control. Each control
 * mirrors the filter's live `state` into local Compose state so toggling it doesn't
 * rebuild the whole row (preserving D-pad focus); [onChanged] re-runs the search.
 */
@Composable
private fun ExtFilterControl(filter: AnimeFilter<*>, onChanged: () -> Unit) {
    when (filter) {
        is AnimeFilter.Select<*> -> {
            var sel by remember { mutableStateOf(filter.state) }
            val values = filter.values
            FilterDropdown(
                label    = "${filter.name}: ${values.getOrNull(sel)?.toString() ?: ""}",
                options  = values.map { it.toString() },
                onSelect = { idx -> filter.state = idx; sel = idx; onChanged() },
            )
        }
        is AnimeFilter.Sort -> {
            var sel by remember { mutableStateOf(filter.state) }
            val current = sel?.let { filter.values.getOrNull(it.index) }
            val arrow   = when { sel == null -> ""; sel!!.ascending -> " ↑"; else -> " ↓" }
            FilterDropdown(
                label    = "${filter.name}: ${current ?: "—"}$arrow",
                options  = filter.values.toList(),
                onSelect = { idx ->
                    // Re-selecting the current option flips ascending/descending.
                    val asc = if (sel?.index == idx) !(sel!!.ascending) else false
                    val newSel = AnimeFilter.Sort.Selection(idx, asc)
                    filter.state = newSel; sel = newSel; onChanged()
                },
            )
        }
        is AnimeFilter.CheckBox -> {
            var checked by remember { mutableStateOf(filter.state) }
            FilterChip(
                selected = checked,
                onClick  = { val v = !checked; filter.state = v; checked = v; onChanged() },
                label    = { Text(filter.name, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.focusBorder(MaterialTheme.shapes.small, inset = true),
            )
        }
        is AnimeFilter.TriState -> {
            var st by remember { mutableStateOf(filter.state) }
            val prefix = when (st) {
                AnimeFilter.TriState.STATE_INCLUDE -> "✓ "
                AnimeFilter.TriState.STATE_EXCLUDE -> "✕ "
                else -> ""
            }
            FilterChip(
                selected = st != AnimeFilter.TriState.STATE_IGNORE,
                onClick  = { val v = (st + 1) % 3; filter.state = v; st = v; onChanged() },
                label    = { Text("$prefix${filter.name}", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.focusBorder(MaterialTheme.shapes.small, inset = true),
            )
        }
        is AnimeFilter.Group<*> -> {
            Text(
                "${filter.name}:",
                style    = MaterialTheme.typography.labelMedium,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(end = 2.dp),
            )
            filter.state.forEach { child ->
                if (child is AnimeFilter<*>) key(child) { ExtFilterControl(child, onChanged) }
            }
        }
        // Header / Separator are visual-only; Text needs a keyboard (skipped on TV).
        else -> Unit
    }
}
