package com.nyantv.ui.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.*
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nyantv.data.EXTERNAL_MEDIA_PREFIX
import com.nyantv.data.Media
import com.nyantv.data.ServiceType
import com.nyantv.ui.MediaCard
import com.nyantv.viewmodel.AppViewModel

// ─── History helpers ───────────────────────────────────────────────────────────

private fun ServiceType.historyKey() = when (this) {
    ServiceType.ANILIST, ServiceType.MAL -> "search_history_anilist_mal"
    ServiceType.SIMKL                    -> "search_history_simkl"
}

private fun SharedPreferences.loadHistory(key: String): List<String> {
    val raw = getString(key, "") ?: ""
    return if (raw.isBlank()) emptyList()
    else raw.split("|").filter { it.isNotBlank() }.reversed()
}

private fun SharedPreferences.addHistory(key: String, term: String): List<String> {
    val ordered = loadHistory(key).reversed().toMutableList()
    ordered.remove(term)
    ordered.add(term)
    val capped = ordered.takeLast(30)
    edit().putString(key, capped.joinToString("|")).apply()
    return capped.reversed()
}

private fun SharedPreferences.deleteHistory(key: String, term: String): List<String> {
    val ordered = loadHistory(key).reversed().toMutableList()
    ordered.remove(term)
    edit().putString(key, ordered.joinToString("|")).apply()
    return ordered.reversed()
}

private fun SharedPreferences.clearHistory(key: String) =
    edit().putString(key, "").apply()

// ─── Helper ────────────────────────────────────────────────────────────────────

private fun FocusRequester.safeRequest() = runCatching { requestFocus() }

// ─── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    vm:              AppViewModel,
    navController:   NavController,
    sidebarFocusReq: FocusRequester,
    onDetailClick:   (String) -> Unit
) {
    val context     = LocalContext.current
    val prefs       = remember { context.getSharedPreferences("nyantv_prefs", Context.MODE_PRIVATE) }
    val serviceType by vm.serviceType.collectAsStateWithLifecycle()
    val apiResults  by vm.searchResults.collectAsStateWithLifecycle()
    val incognito   by vm.incognito.collectAsStateWithLifecycle()
    val extResults  by vm.extSearchResults.collectAsStateWithLifecycle()

    // API hits first, then extension hits that aren't already covered by a same-titled API result.
    val combinedResults = remember(apiResults, extResults) {
        apiResults + extResults.filter { e -> apiResults.none { it.title.equals(e.title, ignoreCase = true) } }
    }

    val historyKey = remember(serviceType) { serviceType.historyKey() }

    var query   by remember { mutableStateOf("") }
    var history by remember(historyKey) { mutableStateOf(prefs.loadHistory(historyKey)) }

    val showHistory = query.isBlank()

    val searchFocusReq = remember { FocusRequester() }
    val focusManager   = LocalFocusManager.current

    LaunchedEffect(Unit) { searchFocusReq.safeRequest() }

    fun doSearch(q: String = query) {
        if (q.isBlank()) return
        // In incognito mode, run the search but don't record it in search history.
        if (!incognito) history = prefs.addHistory(historyKey, q.trim())
        vm.search(q.trim())
        vm.searchExtensions(q.trim())   // no-op unless "search all extensions" is enabled
        focusManager.clearFocus()
    }

    fun clearQuery() {
        query = ""
        vm.search("")
        vm.searchExtensions("")
        searchFocusReq.safeRequest()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value         = query,
                            onValueChange = { query = it; if (it.isBlank()) { vm.search(""); vm.searchExtensions("") } },
                            placeholder   = { Text("Search anime…") },
                            singleLine      = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                            shape           = MaterialTheme.shapes.large,
                            modifier        = Modifier
                                .weight(1f)
                                .focusRequester(searchFocusReq)
                                .onPreviewKeyEvent { ev ->
                                    if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                                    when (ev.key) {
                                        Key.DirectionDown -> { focusManager.moveFocus(FocusDirection.Down); true }
                                        else -> false
                                    }
                                },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        )

                        AnimatedVisibility(
                            visible = query.isNotEmpty(),
                            enter   = fadeIn(),
                            exit    = fadeOut()
                        ) {
                            IconButton(onClick = ::clearQuery) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear")
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {

            AnimatedVisibility(showHistory, enter = fadeIn(), exit = fadeOut()) {
                SearchHistory(
                    history        = history,
                    onTermSelected = { query = it; doSearch(it) },
                    onDeleteTerm   = { history = prefs.deleteHistory(historyKey, it) },
                    onClearAll     = { prefs.clearHistory(historyKey); history = emptyList() }
                )
            }

            AnimatedVisibility(!showHistory, enter = fadeIn(), exit = fadeOut()) {
                if (combinedResults.isEmpty()) {
                    EmptyState()
                } else {
                    ResultsGrid(
                        results         = combinedResults,
                        searchFocusReq  = searchFocusReq,
                        sidebarFocusReq = sidebarFocusReq,
                        onItemClick     = { id ->
                            focusManager.clearFocus(force = true)
                            // Extension hits have no tracking id — resolve them by title first.
                            if (id.startsWith(EXTERNAL_MEDIA_PREFIX)) {
                                combinedResults.firstOrNull { it.id == id }?.let { m ->
                                    vm.resolveExtToDetail(m) { resolvedId -> onDetailClick(resolvedId) }
                                }
                            } else {
                                onDetailClick(id)
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─── Results grid ──────────────────────────────────────────────────────────────

@Composable
private fun ResultsGrid(
    results:         List<Media>,
    searchFocusReq:  FocusRequester,
    sidebarFocusReq: FocusRequester,
    onItemClick:     (String) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cols = maxOf(1, (maxWidth / 128.dp).toInt())

        LazyVerticalGrid(
            columns               = GridCells.Fixed(cols),
            contentPadding        = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(results, key = { _, m -> m.id }) { index, media ->
                var focused by remember { mutableStateOf(false) }
                val row = index / cols
                val col = index % cols

                MediaCard(
                    media    = media,
                    onClick  = { onItemClick(media.id) },
                    modifier = Modifier
                        .then(
                            if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                            else Modifier
                        )
                        .onFocusChanged { focused = it.hasFocus }
                        .onPreviewKeyEvent { ev ->
                            if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (ev.key) {
                                Key.DirectionUp -> if (row == 0) {
                                    searchFocusReq.safeRequest(); true
                                } else false

                                Key.DirectionLeft -> if (col == 0) {
                                    sidebarFocusReq.safeRequest(); true
                                } else false

                                else -> false
                            }
                        }
                )
            }
        }
    }
}

// ─── Search history ────────────────────────────────────────────────────────────

@Composable
private fun SearchHistory(
    history:        List<String>,
    onTermSelected: (String) -> Unit,
    onDeleteTerm:   (String) -> Unit,
    onClearAll:     () -> Unit
) {
    if (history.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.History, null, Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(12.dp))
                Text("No recent searches", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
        return
    }

    LazyColumn(
        contentPadding      = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                modifier              = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.History, null, Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Recent Searches",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onClearAll) {
                    Text(
                        "Clear all",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        itemsIndexed(history, key = { _, t -> t }) { _, term ->
            HistoryItem(
                term     = term,
                onSelect = { onTermSelected(term) },
                onDelete = { onDeleteTerm(term) }
            )
        }
    }
}

// ─── History item ──────────────────────────────────────────────────────────────

@Composable
private fun HistoryItem(
    term:     String,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
) {
    var textFocused   by remember { mutableStateOf(false) }
    var deleteFocused by remember { mutableStateOf(false) }

    Row(
        modifier          = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    color = when {
                        textFocused || deleteFocused -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                        else                          -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                .then(
                    if (textFocused || deleteFocused)
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                    else Modifier
                )
                .onFocusChanged { textFocused = it.isFocused }
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Filled.History, null, Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Text(
                text     = term,
                style    = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
            )
        }

        IconButton(
            onClick  = onDelete,
            modifier = Modifier.onFocusChanged { deleteFocused = it.isFocused }
        ) {
            Icon(
                Icons.Filled.Close, "Remove", Modifier.size(15.dp),
                tint = if (deleteFocused) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

// ─── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.SearchOff, null, Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "No results found",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}