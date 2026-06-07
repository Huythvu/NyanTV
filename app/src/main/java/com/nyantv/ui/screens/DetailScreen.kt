package com.nyantv.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.nyantv.data.Media
import com.nyantv.data.ServiceType
import com.nyantv.data.TrackedMedia
import com.nyantv.player.PlayerTabViewModel
import com.nyantv.ui.*
import com.nyantv.ui.theme.FocusIndication
import com.nyantv.ui.utils.NetworkState
import com.nyantv.ui.utils.NetworkStatusContent
import com.nyantv.ui.utils.focusBorder
import com.nyantv.viewmodel.AppViewModel
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class DetailTab { INFO, PLAYER }

@Composable
fun DetailScreen(
    id:                 String,
    vm:                 AppViewModel,
    modifier:           Modifier = Modifier,
    returnFocusReq:     FocusRequester,
    playerReturnCount:  Int = 0,
    autoOpenPlayerTab: Boolean = false,
    onAutoTabConsumed: () -> Unit = {},
    onBack:             () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToPlayer: () -> Unit,
) {
    val context = LocalContext.current
    val backFocusReq      = remember { FocusRequester() }
    val containerFocusReq = remember { FocusRequester() }
    val playerTabFocusReq = remember { FocusRequester() }

    var media    by remember { mutableStateOf<Media?>(null) }
    var netState by remember { mutableStateOf(NetworkState.LOADING) }
    var showEdit by remember { mutableStateOf(false) }
    var retryKey by remember { mutableIntStateOf(0) }
    var activeTab by rememberSaveable { mutableStateOf(DetailTab.INFO) }

    val loggedIn     by vm.isLoggedIn.collectAsStateWithLifecycle()
    val currentEntry by vm.currentMedia.collectAsStateWithLifecycle()
    val serviceType  by vm.serviceType.collectAsStateWithLifecycle()
    val serviceName  = serviceType.name.lowercase().replaceFirstChar { it.uppercase() }
    var bannerUrl by remember { mutableStateOf<String?>(null) }

    val serviceKey = when (serviceType) {
        ServiceType.ANILIST, ServiceType.MAL -> "anilist_mal"
        ServiceType.SIMKL                    -> "simkl"
    }

    val malId = when (serviceType) {
        ServiceType.MAL     -> id
        ServiceType.ANILIST -> media?.idMal
        ServiceType.SIMKL   -> null
    }

    val playerVm: PlayerTabViewModel = viewModel(
        key     = "player_tab_$id",
        factory = PlayerTabViewModel.Factory(
            app         = context.applicationContext as android.app.Application,
            mediaId     = id,
            mediaTitle  = media?.title ?: "",
            serviceKey  = serviceKey,
            serviceType = serviceType,
            malId       = malId,
        )
    )

    val scope = rememberCoroutineScope()

    suspend fun tryFetch(): Boolean {
        val result = runCatching { vm.fetchDetails(id) }
        return if (result.isSuccess && result.getOrNull()?.title != "?") {
            media    = result.getOrNull()
            netState = NetworkState.SUCCESS
            true
        } else false
    }

    LaunchedEffect(id, retryKey) {
        vm.setCurrentMedia(id)
        netState = NetworkState.LOADING
        media    = null
        if (!tryFetch()) {
            delay(2_000)
            if (!tryFetch()) netState = NetworkState.ERROR
        }
    }

    LaunchedEffect(Unit)  { runCatching { containerFocusReq.requestFocus() } }
    LaunchedEffect(media) {
        val m = media ?: return@LaunchedEffect
        delay(150)
        runCatching { backFocusReq.requestFocus() }
        playerVm.updateMediaTitle(m.title)
        playerVm.setMediaImages(m.cover, m.poster)
        m.idMal?.let { playerVm.updateMalId(it) }
        if (serviceType == ServiceType.SIMKL) {
            m.imdbId?.let { playerVm.setImdbId(it) }
        }
        bannerUrl = vm.resolveAnilistBanner(m)
    }

    val listState = rememberLazyListState()
    LaunchedEffect(id)       { listState.scrollToItem(0) }
    LaunchedEffect(showEdit) { if (!showEdit) listState.animateScrollToItem(0) }
    LaunchedEffect(autoOpenPlayerTab) {
        if (!autoOpenPlayerTab) return@LaunchedEffect
        delay(800)
        activeTab = DetailTab.PLAYER
        onAutoTabConsumed()
    }

    val primary         = MaterialTheme.colorScheme.primary
    val backIndication  = remember(primary) { FocusIndication(primary, cornerRadiusDp = 8.dp) }
    val backInteraction = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .focusRequester(containerFocusReq)
            .focusable()
            .background(MaterialTheme.colorScheme.background)
            .onKeyEvent { keyEvent ->
                if (keyEvent.key == Key.Back && keyEvent.type == KeyEventType.KeyUp && !showEdit) {
                    onBack(); true
                } else false
            }
    ) {
        BackHandler(enabled = !showEdit) { onBack() }
        BackHandler(enabled = showEdit)  { showEdit = false }

        NetworkStatusContent(state = netState, serviceName = serviceName, onRetry = { retryKey++ }) {
            val m = media ?: return@NetworkStatusContent

            LazyColumn(
                state               = listState,
                modifier            = Modifier.fillMaxSize(),
                contentPadding      = PaddingValues(bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ── Banner ────────────────────────────────────────────────────
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                        AsyncImage(
                            model              = bannerUrl ?: m.cover ?: m.poster,
                            contentDescription = null,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier.fillMaxSize(),
                        )
                        Box(
                            modifier = Modifier
                                .padding(12.dp)
                                .size(36.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                                .focusRequester(backFocusReq)
                                .focusProperties {
                                    up    = FocusRequester.Cancel
                                    left  = FocusRequester.Cancel
                                    right = FocusRequester.Cancel
                                }
                                .clickable(interactionSource = backInteraction, indication = backIndication) { onBack() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint               = MaterialTheme.colorScheme.onSurface,
                                modifier           = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                // ── Tabs ────────────────────────────
                item {
                    TabRow(
                        selectedTabIndex = activeTab.ordinal,
                        modifier         = Modifier
                            .fillMaxWidth()
                            .offset(y = (-20).dp)
                            .focusRequester(returnFocusReq)
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                                    scope.launch {
                                        runCatching { backFocusReq.requestFocus() }
                                    }
                                    true
                                } else false
                            },
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor   = MaterialTheme.colorScheme.primary,
                    ) {
                        Tab(
                            selected = activeTab == DetailTab.INFO,
                            onClick  = { activeTab = DetailTab.INFO },
                            icon     = { Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp)) },
                            text     = { Text("Info") },
                        )
                        Tab(
                            selected = activeTab == DetailTab.PLAYER,
                            onClick  = { activeTab = DetailTab.PLAYER },
                            modifier = Modifier.focusRequester(playerTabFocusReq),
                            icon     = { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp)) },
                            text     = { Text("Player") },
                        )
                    }
                }

                when (activeTab) {
                    DetailTab.INFO -> infoItems(
                        media              = m,
                        loggedIn           = loggedIn,
                        currentEntry       = currentEntry,
                        onShowEdit         = { showEdit = true },
                        onNavigateToDetail = onNavigateToDetail,
                    )
                    DetailTab.PLAYER -> item {
                        PlayerTabScreen(
                            vm                  = playerVm,
                            watchedEpisodeCount = currentEntry?.episodeCount ?: 0,
                            playerReturnCount   = playerReturnCount,
                            onEpisodeSelected   = { onNavigateToPlayer() },
                            onOverlayDismiss    = {
                                scope.launch {
                                    listState.scrollToItem(1)
                                    delay(50)
                                    runCatching { playerTabFocusReq.requestFocus() }
                                }
                            },
                            modifier = Modifier.fillParentMaxHeight(),
                        )
                    }
                }
            }
        }

        if (showEdit) {
            ListEditorDialog(
                currentStatus   = currentEntry?.watchingStatus,
                currentProgress = currentEntry?.episodeCount,
                currentScore    = currentEntry?.score,
                totalEpisodes   = media?.episodes,
                onDismiss       = { showEdit = false },
                onSave          = { status, progress, score ->
                    vm.updateEntry(id, status, progress, score)
                    showEdit = false
                },
                onDelete = if (currentEntry != null) {
                    { vm.deleteEntry(id); showEdit = false }
                } else null,
            )
        }
    }
}

// ── Info items (LazyListScope) ────────────────────────────────────────────────

private fun LazyListScope.infoItems(
    media:              Media,
    loggedIn:           Boolean,
    currentEntry:       TrackedMedia?,
    onShowEdit:         () -> Unit,
    onNavigateToDetail: (String) -> Unit,
) {
    item {
        Row(
            modifier              = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            AsyncImage(
                model              = media.poster,
                contentDescription = null,
                contentScale       = ContentScale.Crop,
                modifier           = Modifier
                    .size(100.dp, 148.dp)
                    .clip(MaterialTheme.shapes.medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    media.title,
                    style      = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onBackground
                )
                media.averageScore?.let {
                    Text(
                        "★ ${"%.1f".format(it / 10f)}",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Text(
                    listOfNotNull(media.format, media.status).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                media.episodes?.let {
                    Text(
                        "$it episodes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }

    if (loggedIn) {
        item {
            Column(
                modifier            = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Button(
                        onClick  = onShowEdit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusBorder(MaterialTheme.shapes.medium),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Text(
                            currentEntry?.watchingStatus?.let {
                                it.lowercase().replaceFirstChar { c -> c.uppercase() }
                            } ?: "Add to List"
                        )
                    }
                }
                currentEntry?.let {
                    EpisodeProgressBar(current = it.episodeCount ?: 0, total = media.episodes)
                }
            }
        }
    }

    if (media.genres.isNotEmpty()) {
        item {
            Row(
                modifier              = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                media.genres.forEach { genre ->
                    SuggestionChip(
                        onClick = {},
                        label   = { Text(genre, style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }
    }

    media.description?.let { desc ->
        item {
            Text(
                text     = desc.replace(Regex("<[^>]*>"), ""),
                style    = MaterialTheme.typography.bodyMedium,
                color    = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

    val directPrequel = media.relations.firstOrNull { it.relationType == "PREQUEL" }
    val directSequel  = media.relations.firstOrNull { it.relationType == "SEQUEL" }
    val neighborIds   = listOfNotNull(directPrequel?.id, directSequel?.id).toSet()

    if (neighborIds.isNotEmpty()) {
        item {
            Row(
                modifier              = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.Top,
            ) {
                directPrequel?.let { prequel ->
                    Column(
                        modifier            = Modifier.width(120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Prequel",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onBackground
                        )
                        MediaCard(media = prequel, onClick = { onNavigateToDetail(prequel.id) })
                    }
                }
                directSequel?.let { sequel ->
                    Column(
                        modifier            = Modifier.width(120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Sequel",
                            style      = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color      = MaterialTheme.colorScheme.onBackground
                        )
                        MediaCard(media = sequel, onClick = { onNavigateToDetail(sequel.id) })
                    }
                }
            }
        }
    }

    val otherRelations = media.relations.filter { it.id !in neighborIds }
    if (otherRelations.isNotEmpty()) {
        item {
            SectionRow(
                title      = "Relations",
                items      = otherRelations,
                onItemClick = { onNavigateToDetail(it.id) }
            )
        }
    }

    if (media.recommendations.isNotEmpty()) {
        item {
            SectionRow(
                title       = "Recommended",
                items       = media.recommendations,
                onItemClick = { onNavigateToDetail(it.id) }
            )
        }
    }
}

// ── List editor dialog ─────────────────────────────────────────────────────────

private val STATUSES = listOf("CURRENT", "COMPLETED", "PLANNING", "DROPPED", "PAUSED")

@Composable
private fun ListEditorDialog(
    currentStatus:   String?,
    currentProgress: Int?,
    currentScore:    Float?,
    totalEpisodes:   Int?,
    onDismiss:       () -> Unit,
    onSave:          (String?, Int?, Float?) -> Unit,
    onDelete:        (() -> Unit)?
) {
    var status   by remember { mutableStateOf(currentStatus ?: "PLANNING") }
    var progress by remember { mutableStateOf(currentProgress?.toString() ?: "0") }
    var score    by remember { mutableStateOf(currentScore?.toString() ?: "0") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title            = { Text("Edit List Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Status", style = MaterialTheme.typography.labelMedium)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier              = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    STATUSES.forEach { s ->
                        FilterChip(
                            selected = status == s,
                            onClick  = { status = s },
                            label    = {
                                Text(
                                    s.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        )
                    }
                }
                OutlinedTextField(
                    value         = progress,
                    onValueChange = { progress = it },
                    label         = { Text("Progress (ep)") },
                    suffix        = { Text("/ ${totalEpisodes ?: "?"}") },
                    singleLine    = true
                )
                OutlinedTextField(
                    value         = score,
                    onValueChange = { score = it },
                    label         = { Text("Score (0–10)") },
                    singleLine    = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(status, progress.toIntOrNull(), score.toFloatOrNull()) }) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                onDelete?.let { del ->
                    TextButton(onClick = del) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}