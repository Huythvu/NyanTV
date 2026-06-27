package com.nyantv.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nyantv.AniZipEpisodeMeta
import com.nyantv.AniZipService
import com.nyantv.AniskipService
import com.nyantv.EpisodeSkipTimes
import com.nyantv.IntroDbService
import com.nyantv.JikanService
import com.nyantv.data.ServiceType
import com.nyantv.extensions.AniyomiExtensions
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val PROBE_CONCURRENCY = 5
private const val PROBE_TIMEOUT_MS  = 8_000L
private const val PROBE_TTL_MS      = 7L * 24 * 60 * 60 * 1000  // re-check matches after 7 days
private const val MATCH_THRESHOLD   = 0.60   // fuzzy title-match score needed to accept a source
private const val MATCH_CONFIDENT   = 0.97   // near-exact match → stop probing more title variants

data class PlayerTabUiState(
    val sources:        List<SearchableSource>          = emptyList(),
    val matchedSources: Set<Long>                       = emptySet(),   // sources confirmed to have this anime
    val matchScores:    Map<Long, Double>               = emptyMap(),   // sourceId → fuzzy title-match score
    val probing:        Boolean                         = false,        // a source check is still in flight
    val selectedSource: SearchableSource?               = null,
    val searchQuery:    String                          = "",
    val isEditingQuery: Boolean                         = false,
    val searchState:    SearchState                     = SearchState.Idle,
    val selectedAnime:  SAnime?                         = null,
    val episodeState:   EpisodeState                    = EpisodeState.Idle,
    val selectedEpisode: SEpisode?                      = null,
    val streamState:    StreamState                     = StreamState.Idle,
    val skipTimes:      EpisodeSkipTimes?               = null,
    val episodeMeta:    Map<String, AniZipEpisodeMeta>  = emptyMap(),
)

class PlayerTabViewModel(
    app: Application,
    val mediaId:      String,
    mediaTitle:   String,
    val serviceKey:   String,
    private val serviceType:  ServiceType,
    private val malId:        String?,
) : AndroidViewModel(app) {

    private val cache             = PlayerCache(app)
    private val aniyomi           = AniyomiExtensions(app)
    private val orderStore        = com.nyantv.extensions.ExtensionOrderStore(app)
    private val watchHistoryStore = WatchHistoryStore(app)

    // ── Watch progress ─────────────────────────────────────────────────────────

    private val _resumeProgress = MutableStateFlow<EpisodeProgress?>(null)
    val resumeProgress: StateFlow<EpisodeProgress?> = _resumeProgress.asStateFlow()

    private val _episodeProgressMap = MutableStateFlow<Map<Int, EpisodeProgress>>(emptyMap())
    val episodeProgressMap: StateFlow<Map<Int, EpisodeProgress>> = _episodeProgressMap.asStateFlow()

    private val _watchedEpisodes = MutableStateFlow<Set<Int>>(emptySet())
    val watchedEpisodes: StateFlow<Set<Int>> = _watchedEpisodes.asStateFlow()

    val anilistId:    String? get() = if (serviceType == ServiceType.ANILIST) mediaId else null
    val currentMalId: String? get() = _malId

    val mediaBannerUrl: String get() = _mediaBannerUrl
    val mediaPosterUrl: String get() = _mediaPosterUrl
    private var _mediaBannerUrl: String = ""
    private var _mediaPosterUrl: String = ""

    var mediaTitle: String = mediaTitle
        private set

    fun setMediaImages(banner: String?, poster: String?) {
        _mediaBannerUrl = banner ?: ""
        _mediaPosterUrl = poster ?: ""
    }

    fun refreshWatchProgress() {
        _episodeProgressMap.value = emptyMap()

        _resumeProgress.value = if (serviceKey == "simkl") {
            watchHistoryStore.loadSimkl(mediaId)
        } else {
            watchHistoryStore.loadAnilistMal(anilistId = anilistId, malId = _malId)
        }
        _watchedEpisodes.value = if (serviceKey == "simkl") {
            watchHistoryStore.getWatchedSimkl(mediaId)
        } else {
            watchHistoryStore.getWatchedAnilistMal(anilistId = anilistId, malId = _malId)
        }
    }

    fun episodeProgressFor(episodeNumber: Float): EpisodeProgress? {
        val cached = _episodeProgressMap.value[episodeNumber.toInt()]
        if (cached != null) return cached
        val loaded = if (serviceKey == "simkl") {
            watchHistoryStore.loadEpisodeProgressSimkl(mediaId, episodeNumber)
        } else {
            watchHistoryStore.loadEpisodeProgressAnilistMal(anilistId, _malId, episodeNumber)
        }
        if (loaded != null) {
            _episodeProgressMap.update { it + (episodeNumber.toInt() to loaded) }
        }
        return loaded
    }

    private val _state = MutableStateFlow(PlayerTabUiState())
    val state: StateFlow<PlayerTabUiState> = _state.asStateFlow()
    private var _malId: String? = malId
    private var imdbId: String? = null

    private val _fillerEpisodes = MutableStateFlow<Set<Int>>(emptySet())
    val fillerEpisodes: StateFlow<Set<Int>> = _fillerEpisodes

    private var searchJob:      Job? = null
    private var episodeMetaJob: Job? = null
    private var episodeMetaKey: String? = null
    private var probeJob:       Job? = null
    private var probeStartedQuery: String? = null
    private var searchTitles: List<String> = emptyList()

    // Probe-driven source auto-selection.
    private val probeLock       = Any()
    private val probedSourceIds = mutableSetOf<Long>()   // sources whose probe has finished (matched or not)
    private var userSelectedSource = false               // true once the user picks a source by hand

    init {
        refreshWatchProgress()
        loadEpisodeMetadata()

        viewModelScope.launch(Dispatchers.IO) {
            runCatching { aniyomi.extensionManager.refresh() }
        }

        var initialised = false

        viewModelScope.launch {
            aniyomi.installedExtensions.collect { _ ->
                val newSources      = buildSources()
                val currentSourceId = _state.value.selectedSource?.id

                if (!initialised && newSources.isNotEmpty()) {
                    initialised = true

                    val savedQuery = cache.loadQueryOverride(mediaId)

                    // Start with NO source selected. The probe decides which extension to open:
                    // the first one (in list order) confirmed to actually have this anime. This
                    // avoids sticking on a previously-used extension that has no entry for the
                    // current title.
                    _state.update {
                        it.copy(
                            sources        = newSources,
                            searchQuery    = savedQuery ?: mediaTitle,
                            selectedSource = null,
                        )
                    }

                    startProbe()

                } else if (initialised) {
                    _state.update { s ->
                        s.copy(
                            sources        = newSources,
                            selectedSource = newSources.firstOrNull { it.id == currentSourceId }
                                ?: s.selectedSource,
                        )
                    }
                    maybeAutoSelectMatched()
                }
            }
        }

        if (serviceKey != "simkl" && malId != null) {
            viewModelScope.launch {
                _fillerEpisodes.value = JikanService.getFillerEpisodes(malId)
            }
        }
        if (malId != null) {
            loadEpisodeMetadata()
        }
    }

    private fun applySource(source: SearchableSource) {
        _state.update { it.copy(selectedSource = source) }
        viewModelScope.launch {
            val cached = cache.loadSelectedResult(source.id, mediaId)
                ?: cache.loadProbe(source.id, mediaId)?.takeIf { it.matched }?.result
            if (cached != null) {
                val anime = SAnime.create().apply {
                    url           = cached.url
                    title         = cached.title
                    thumbnail_url = cached.thumbnail
                }
                _state.update { it.copy(selectedAnime = anime) }
                loadEpisodes(source, anime)
            } else {
                _state.update { it.copy(selectedAnime = null, episodeState = EpisodeState.Idle) }
                val q = _state.value.searchQuery
                if (q.isNotBlank()) autoSearch(source, q)
            }
        }
    }

    /** True if [source]'s extension is on the user's tracking-exclusion list. */
    fun isSourceTrackingExcluded(source: SearchableSource?): Boolean {
        source ?: return false
        val excluded = getApplication<Application>()
            .getSharedPreferences("nyantv_prefs", android.content.Context.MODE_PRIVATE)
            .getString("excluded_tracking_exts", "")?.split("\n")?.filter { it.isNotBlank() }?.toSet()
            ?: emptySet()
        if (excluded.isEmpty()) return false
        val pkg = aniyomi.installedExtensions.value
            .firstOrNull { ext -> ext.sources.any { it.id == source.id } }?.pkgName ?: return false
        return pkg in excluded
    }

    private fun buildSources(): List<SearchableSource> {
        return orderStore.sort(aniyomi.installedExtensions.value)
            .flatMap { ext ->
                ext.sources.filterIsInstance<AnimeHttpSource>().map { httpSource ->
                    AniyomiSearchableSource(httpSource = httpSource, iconUrl = ext.iconUrl)
                }
            }
    }

    /** Extra title variants (romaji / English / synonyms) to widen the source probe. */
    fun setSearchTitles(titles: List<String>) {
        val cleaned = titles.map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (cleaned == searchTitles) return
        searchTitles = cleaned
        if (_state.value.sources.isNotEmpty()) startProbe()
    }

    /** Forget cached probe results for this anime and re-check every source from scratch. */
    fun recheckSources() {
        viewModelScope.launch {
            _state.value.sources.forEach { cache.clearProbe(it.id, mediaId) }
            probeStartedQuery = null
            startProbe(force = true)
        }
    }

    /** Searches every installed source for the current title and records which ones have it. */
    private fun startProbe(force: Boolean = false) {
        val sources = _state.value.sources
        val primary = _state.value.searchQuery.ifBlank { mediaTitle }.trim()
        val queries = (listOf(primary) + searchTitles)
            .map { it.trim() }.filter { it.isNotBlank() }.distinct()
        if (sources.isEmpty() || queries.isEmpty()) return
        val key = queries.joinToString("|")
        if (!force && probeStartedQuery == key) return
        probeStartedQuery = key

        synchronized(probeLock) { probedSourceIds.clear() }

        probeJob?.cancel()
        probeJob = viewModelScope.launch {
            _state.update { it.copy(probing = true, matchedSources = emptySet(), matchScores = emptyMap()) }
            val gate = Semaphore(PROBE_CONCURRENCY)
            coroutineScope {
                sources.forEach { source ->
                    launch(Dispatchers.IO) {
                        gate.withPermit {
                            val outcome = probeSource(source, queries, force)
                            if (outcome.matched) {
                                _state.update {
                                    it.copy(
                                        matchedSources = it.matchedSources + source.id,
                                        matchScores    = it.matchScores + (source.id to outcome.score),
                                    )
                                }
                            }
                            onSourceProbed(source.id)
                        }
                    }
                }
            }
            _state.update { it.copy(probing = false) }
            maybeAutoSelectMatched()
        }
    }

    private fun onSourceProbed(sourceId: Long) {
        synchronized(probeLock) { probedSourceIds.add(sourceId) }
        maybeAutoSelectMatched()
    }

    /**
     * Once every source has finished probing, auto-select the one with the closest (highest-scoring)
     * title match among all that cleared the threshold — so we compare all servers rather than
     * grabbing the first acceptable one. Extension order breaks ties. No-op once the user has picked
     * a source by hand.
     */
    private fun maybeAutoSelectMatched() {
        synchronized(probeLock) {
            if (userSelectedSource) return
            val s = _state.value
            // Wait until the probe has fully settled so the comparison covers every server.
            if (s.probing) return
            val best = s.sources
                .filter { it.id in s.matchedSources }
                .maxByOrNull { s.matchScores[it.id] ?: 0.0 }
                ?: return
            if (s.selectedSource?.id != best.id) applySource(best)
        }
    }

    private data class ProbeOutcome(val matched: Boolean, val score: Double)

    /**
     * Scores how well [source] matches the anime: across every title variant it searches the source
     * and keeps the best-scoring result. A source is accepted only if its best score clears the
     * threshold; the best result is cached for selection. Uses the cache unless forced/stale.
     */
    private suspend fun probeSource(source: SearchableSource, queries: List<String>, force: Boolean): ProbeOutcome {
        if (!force) {
            cache.loadProbe(source.id, mediaId)?.let { cached ->
                val fresh = System.currentTimeMillis() - cached.ts < PROBE_TTL_MS
                // Ignore pre-scoring cache entries (matched with score 0) so they get re-scored once.
                if (fresh && !(cached.matched && cached.score <= 0.0)) {
                    return ProbeOutcome(cached.matched, cached.score)
                }
            }
        }
        var bestAnime: SAnime? = null
        var bestScore = 0.0
        for (q in queries) {
            val page = withTimeoutOrNull(PROBE_TIMEOUT_MS) {
                try {
                    source.search(q)
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (e: Throwable) {
                    null
                }
            }
            page?.animes?.forEach { anime ->
                val score = bestTitleScore(anime.title, queries)
                if (score > bestScore) { bestScore = score; bestAnime = anime }
            }
            if (bestScore >= MATCH_CONFIDENT) break   // near-exact, no need to try more variants
        }
        val matched = bestScore >= MATCH_THRESHOLD && bestAnime != null
        cache.saveProbe(source.id, mediaId, matched = matched, anime = bestAnime, score = bestScore)
        return ProbeOutcome(matched, bestScore)
    }

    // ── Fuzzy title matching ────────────────────────────────────────────────────
    private fun normalizeTitle(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun bestTitleScore(candidate: String, knownTitles: List<String>): Double =
        knownTitles.maxOfOrNull { titleSimilarity(candidate, it) } ?: 0.0

    /** Combined similarity in 0..1 (max of edit-distance ratio, token overlap, containment). */
    private fun titleSimilarity(a: String, b: String): Double {
        val na = normalizeTitle(a)
        val nb = normalizeTitle(b)
        if (na.isEmpty() || nb.isEmpty()) return 0.0
        if (na == nb) return 1.0
        val maxLen  = maxOf(na.length, nb.length)
        val lev     = 1.0 - levenshtein(na, nb).toDouble() / maxLen
        val ta      = na.split(" ").toSet()
        val tb      = nb.split(" ").toSet()
        val jaccard = (ta intersect tb).size.toDouble() / (ta union tb).size
        val contain = if (na.contains(nb) || nb.contains(na))
            0.85 + 0.15 * (minOf(na.length, nb.length).toDouble() / maxLen) else 0.0
        return maxOf(lev, jaccard, contain)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = if (a[i - 1] == b[j - 1]) prev else 1 + minOf(prev, dp[j], dp[j - 1])
                prev = tmp
            }
        }
        return dp[b.length]
    }

    fun updateMediaTitle(title: String) {
        if (title.isBlank()) return
        if (mediaTitle.isBlank()) mediaTitle = title
        val s = _state.value
        if (s.searchQuery.isNotBlank()) return
        _state.update { it.copy(searchQuery = title) }
        startProbe()
        if (s.selectedAnime == null && s.searchState is SearchState.Idle) {
            val source = s.selectedSource ?: return
            autoSearch(source, title)
        }
    }

    fun updateMalId(id: String) {
        if (_malId != null) return
        _malId = id
        viewModelScope.launch {
            _fillerEpisodes.value = JikanService.getFillerEpisodes(id)
            state.value.selectedEpisode?.let { loadSkipTimes(it.episode_number) }
        }
        loadEpisodeMetadata()
    }

    fun setImdbId(id: String) { imdbId = id }

    private fun loadEpisodeMetadata() {
        val anilistId = anilistId
        val malId     = _malId
        val key = when (serviceType) {
            ServiceType.ANILIST -> anilistId?.let { "anilist:$it" }
            ServiceType.MAL     -> malId?.let     { "mal:$it" }
            ServiceType.SIMKL   -> null
        } ?: return

        if (episodeMetaKey == key && _state.value.episodeMeta.isNotEmpty()) return
        episodeMetaKey = key
        episodeMetaJob?.cancel()
        _state.update { it.copy(episodeMeta = emptyMap()) }
        episodeMetaJob = viewModelScope.launch {
            val result = when (serviceType) {
                ServiceType.ANILIST -> AniZipService.getEpisodesByAnilistId(anilistId!!)
                ServiceType.MAL     -> AniZipService.getEpisodesByMalId(malId!!)
                ServiceType.SIMKL   -> emptyMap()
            }
            _state.update { it.copy(episodeMeta = result) }
        }
    }

    fun loadSkipTimes(episodeNumber: Float) {
        viewModelScope.launch {
            _state.update { it.copy(skipTimes = null) }
            val result: EpisodeSkipTimes? = when {
                serviceKey == "simkl" -> {
                    val iid = imdbId ?: return@launch
                    IntroDbService.getSkipTimes(iid, season = "1", episode = episodeNumber.toInt().toString())
                }
                _malId != null -> AniskipService.getSkipTimes(
                    malId         = _malId!!,
                    episodeNumber = episodeNumber.toInt().toString(),
                )
                else -> null
            }
            _state.update { it.copy(skipTimes = result) }
        }
    }

    fun loadSkipTimesSimkl(imdbId: String, season: String, episode: String) {
        viewModelScope.launch {
            _state.update { it.copy(skipTimes = null) }
            val result = IntroDbService.getSkipTimes(imdbId, season, episode)
            _state.update { it.copy(skipTimes = result) }
        }
    }

    fun selectSource(source: SearchableSource) {
        if (source.id == _state.value.selectedSource?.id) return
        userSelectedSource = true
        _state.update {
            it.copy(
                selectedSource = source,
                selectedAnime  = null,
                episodeState   = EpisodeState.Idle,
                searchState    = SearchState.Idle,
            )
        }
        viewModelScope.launch {
            cache.saveSelectedSource(serviceKey, source.id)
            val cached = cache.loadSelectedResult(source.id, mediaId)
                ?: cache.loadProbe(source.id, mediaId)?.takeIf { it.matched }?.result
            if (cached != null) {
                val anime = SAnime.create().apply {
                    url           = cached.url
                    title         = cached.title
                    thumbnail_url = cached.thumbnail
                }
                _state.update { it.copy(selectedAnime = anime) }
                loadEpisodes(source, anime)
            } else {
                autoSearch(source, _state.value.searchQuery)
            }
        }
    }

    fun setSearchQuery(query: String)    { _state.update { it.copy(searchQuery = query) } }
    fun setEditingQuery(editing: Boolean){ _state.update { it.copy(isEditingQuery = editing) } }

    fun submitSearch() {
        val source = _state.value.selectedSource ?: return
        val query  = _state.value.searchQuery.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(isEditingQuery = false) }
        viewModelScope.launch {
            cache.saveQueryOverride(mediaId, query)
            cache.clearResult(source.id, mediaId)
            _state.update { it.copy(selectedAnime = null, episodeState = EpisodeState.Idle) }
            doSearch(source, query)
        }
    }

    private fun autoSearch(source: SearchableSource, query: String) {
        viewModelScope.launch { doSearch(source, query) }
    }

    fun ensureSearched() {
        if (_state.value.searchState !is SearchState.Idle) return
        val source = _state.value.selectedSource ?: return
        val q = _state.value.searchQuery.trim()
        if (q.isNotBlank()) autoSearch(source, q)
    }

    private suspend fun doSearch(source: SearchableSource, query: String) {
        searchJob?.cancel()
        val job = viewModelScope.launch {
            _state.update { it.copy(searchState = SearchState.Loading) }
            runCatching { withContext(Dispatchers.IO) { source.search(query) } }
                .onSuccess { page ->
                    if (page.animes.isEmpty()) {
                        _state.update { it.copy(searchState = SearchState.Error("No results found")) }
                        return@onSuccess
                    }
                    _state.update { it.copy(searchState = SearchState.Results(page.animes)) }
                    if (_state.value.selectedAnime == null) {
                        selectAnimeResult(page.animes.first(), autoSelected = true)
                    }
                }
                .onFailure { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    _state.update { it.copy(searchState = SearchState.Error(e.message ?: "Search failed")) }
                }
        }
        searchJob = job
        job.join()
    }

    fun selectAnimeResult(anime: SAnime, autoSelected: Boolean = false) {
        val source = _state.value.selectedSource ?: return
        _state.update { it.copy(selectedAnime = anime) }
        viewModelScope.launch {
            if (!autoSelected) cache.saveSelectedResult(source.id, mediaId, anime)
            loadEpisodes(source, anime)
        }
    }

    fun confirmAnimeResult(anime: SAnime) {
        val source = _state.value.selectedSource ?: return
        _state.update { it.copy(selectedAnime = anime) }
        viewModelScope.launch {
            cache.saveSelectedResult(source.id, mediaId, anime)
            loadEpisodes(source, anime)
        }
    }

    private fun loadEpisodes(source: SearchableSource, anime: SAnime, retryCount: Int = 0) {
        viewModelScope.launch {
            _state.update { it.copy(episodeState = EpisodeState.Loading) }
            runCatching { withContext(Dispatchers.IO) { source.getEpisodes(anime) } }
                .onSuccess { episodes ->
                    _state.update {
                        it.copy(episodeState = EpisodeState.Success(episodes.sortedBy { ep -> ep.episode_number }))
                    }
                }
                .onFailure { e ->
                    if (retryCount < 2) {
                        kotlinx.coroutines.delay(500L * (retryCount + 1))
                        loadEpisodes(source, anime, retryCount + 1)
                    } else {
                        _state.update { it.copy(episodeState = EpisodeState.Error(e.message ?: "Failed to load episodes")) }
                    }
                }
        }
    }

    fun retryEpisodes() {
        val source = _state.value.selectedSource ?: return
        val anime  = _state.value.selectedAnime  ?: return
        loadEpisodes(source, anime)
    }

    suspend fun getVideosForEpisode(episode: SEpisode): List<Video> {
        val source = _state.value.selectedSource
            ?: throw IllegalStateException("No source selected")
        return withContext(Dispatchers.IO) { source.getVideoList(episode) }
    }

    fun selectEpisode(episode: SEpisode) {
        val source = _state.value.selectedSource ?: return
        _state.update { it.copy(selectedEpisode = episode, streamState = StreamState.Loading) }
        loadSkipTimes(episode.episode_number)
        viewModelScope.launch {
            runCatching { withContext(Dispatchers.IO) { source.getVideoList(episode) } }
                .onSuccess { videos ->
                    _state.update { it.copy(streamState = StreamState.Ready(videos)) }
                }
                .onFailure { e ->
                    _state.update { it.copy(streamState = StreamState.Error(e.message ?: "Stream-Fehler")) }
                }
        }
    }

    fun clearStreams() = _state.update { it.copy(streamState = StreamState.Idle) }

    class Factory(
        private val app:         Application,
        private val mediaId:     String,
        private val mediaTitle:  String,
        private val serviceKey:  String,
        private val serviceType: ServiceType,
        private val malId:       String? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
            PlayerTabViewModel(app, mediaId, mediaTitle, serviceKey, serviceType, malId) as T
    }
}