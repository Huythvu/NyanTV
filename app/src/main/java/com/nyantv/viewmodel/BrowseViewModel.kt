package com.nyantv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nyantv.data.AnilistService
import com.nyantv.data.EXTERNAL_MEDIA_PREFIX
import com.nyantv.data.Media
import com.nyantv.data.ServiceType
import com.nyantv.extensions.AniyomiExtensions
import eu.kanade.tachiyomi.animesource.model.AnimeFilter
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BrowseFilters(
    val genres: Set<String> = emptySet(),
    val format: String? = null,
    val status: String? = null,
    val season: String? = null,
    val year: Int? = null,
    val sort: String = "POPULARITY_DESC",
)

/** A browsable extension source shown in the source dropdown. */
data class ExtSourceInfo(val id: Long, val name: String, val lang: String)

data class BrowseState(
    val filters: BrowseFilters = BrowseFilters(),
    val extSources: List<ExtSourceInfo> = emptyList(),
    /** null = AniList browse; otherwise the selected extension source id. */
    val selectedSourceId: Long? = null,
    /** The selected extension's own (live, mutable) filter controls. */
    val extFilters: List<AnimeFilter<*>> = emptyList(),
    /** Free-text search within the selected extension (blank = its popular catalog). */
    val extQuery: String = "",
    /** Browse the extension's "Latest" feed instead of its popular catalog. */
    val extLatest: Boolean = false,
    /** Whether the selected extension actually offers a Latest feed. */
    val extSupportsLatest: Boolean = false,
    /** Bumped whenever an extension filter's state is mutated, to force a UI refresh. */
    val filterVersion: Int = 0,
    val results: List<Media> = emptyList(),
    val loading: Boolean = true,        // filter-change / initial load
    val loadingMore: Boolean = false,   // pagination
    val endReached: Boolean = false,
    val resolving: Boolean = false,     // resolving an extension result → AniList detail
)

/** AniList-powered filtered discovery (plus per-extension catalog browse) for the Anime tab. */
class BrowseViewModel(app: Application) : AndroidViewModel(app) {

    private val anilist = AnilistService(app)
    private val aniyomi = AniyomiExtensions(app)
    private val orderStore = com.nyantv.extensions.ExtensionOrderStore(app)

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    /** id → raw extension source, for catalog/browse calls. */
    private val extSourceMap = mutableMapOf<Long, AnimeHttpSource>()

    /** Live filter list for the selected extension (its `state`s are mutated in place). */
    private var currentFilterList: AnimeFilterList? = null
    /** Once the user touches any extension filter, browse via search instead of popular. */
    private var extFiltersModified = false
    /** Current free-text query within the selected extension. */
    private var extQuery = ""
    /** Browse the selected extension's Latest feed instead of Popular. */
    private var extLatest = false

    private var page = 1
    private var loadJob: Job? = null

    private data class FetchResult(val items: List<Media>, val hasNext: Boolean)

    init {
        applyFilters(BrowseFilters())
        viewModelScope.launch {
            aniyomi.installedExtensions.collect { exts ->
                extSourceMap.clear()
                val infos = orderStore.sort(exts).flatMap { ext ->
                    ext.sources.filterIsInstance<AnimeHttpSource>().map { src ->
                        extSourceMap[src.id] = src
                        ExtSourceInfo(id = src.id, name = src.name, lang = src.lang)
                    }
                }.distinctBy { it.id }   // keep the user's extension order from orderStore.sort
                _state.update { it.copy(extSources = infos) }
            }
        }
    }

    // ── Filter setters (AniList mode) ──────────────────────────────────────────
    fun toggleGenre(genre: String) {
        val cur = _state.value.filters.genres
        applyFilters(_state.value.filters.copy(genres = if (genre in cur) cur - genre else cur + genre))
    }

    fun setFormat(format: String?) = applyFilters(_state.value.filters.copy(format = format))
    fun setStatus(status: String?) = applyFilters(_state.value.filters.copy(status = status))
    fun setSeason(season: String?) = applyFilters(_state.value.filters.copy(season = season))
    fun setYear(year: Int?)        = applyFilters(_state.value.filters.copy(year = year))
    fun setSort(sort: String)      = applyFilters(_state.value.filters.copy(sort = sort))

    /** Switch between AniList browse (null) and a specific extension catalog. */
    fun selectSource(sourceId: Long?) {
        if (sourceId == _state.value.selectedSourceId) return
        loadJob?.cancel()
        page = 1
        extFiltersModified = false
        extQuery = ""
        extLatest = false
        currentFilterList = sourceId?.let { id -> runCatching { extSourceMap[id]?.getFilterList() }.getOrNull() }
        val extFilters = currentFilterList?.toList() ?: emptyList()
        val supportsLatest = sourceId?.let { id -> runCatching { extSourceMap[id]?.supportsLatest }.getOrNull() } ?: false
        _state.update {
            it.copy(
                selectedSourceId  = sourceId,
                extFilters        = extFilters,
                extQuery          = "",
                extLatest         = false,
                extSupportsLatest = supportsLatest,
                filterVersion     = it.filterVersion + 1,
                results           = emptyList(),
                loading           = true,
                endReached        = false,
            )
        }
        reload()
    }

    /** Toggle the selected extension's Latest feed vs its popular catalog. */
    fun setExtensionLatest(latest: Boolean) {
        if (latest == extLatest) return
        extLatest = latest
        loadJob?.cancel()
        page = 1
        _state.update { it.copy(extLatest = latest, results = emptyList(), loading = true, endReached = false) }
        reload()
    }

    /** Search within the selected extension's catalog (blank query → its popular catalog). */
    fun setExtensionQuery(query: String) {
        val q = query.trim()
        if (q == extQuery) return
        extQuery = q
        loadJob?.cancel()
        page = 1
        _state.update {
            it.copy(extQuery = q, results = emptyList(), loading = true, endReached = false)
        }
        reload()
    }

    /** Called after an extension filter's state is mutated in place; re-runs the search. */
    fun onExtFilterChanged() {
        extFiltersModified = true
        loadJob?.cancel()
        page = 1
        _state.update {
            it.copy(
                filterVersion = it.filterVersion + 1,
                results       = emptyList(),
                loading       = true,
                endReached    = false,
            )
        }
        reload()
    }

    private fun applyFilters(filters: BrowseFilters) {
        loadJob?.cancel()
        page = 1
        _state.update { it.copy(filters = filters, results = emptyList(), loading = true, endReached = false) }
        reload()
    }

    private fun reload() {
        val s = _state.value
        loadJob = viewModelScope.launch {
            val res = fetch(s, 1)
            _state.update { it.copy(results = res.items, loading = false, endReached = !res.hasNext) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val next = page + 1
            val res  = fetch(s, next)
            if (res.items.isNotEmpty()) page = next
            _state.update {
                it.copy(
                    results     = (it.results + res.items).distinctBy { m -> m.id },
                    loadingMore = false,
                    endReached  = !res.hasNext,
                )
            }
        }
    }

    /** Extension results have no AniList id, so resolve by title before opening the detail screen. */
    fun resolveAndOpen(media: Media, onResolved: (String) -> Unit, onFailed: () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(resolving = true) }
            val id = runCatching { anilist.search(media.title) }.getOrNull()?.firstOrNull()?.id
            _state.update { it.copy(resolving = false) }
            if (id != null) onResolved(id) else onFailed()
        }
    }

    private suspend fun fetch(s: BrowseState, page: Int): FetchResult {
        val sourceId = s.selectedSourceId
        return if (sourceId == null) {
            val items = anilist.browse(
                page       = page,
                genres     = s.filters.genres.toList(),
                format     = s.filters.format,
                status     = s.filters.status,
                season     = s.filters.season,
                seasonYear = s.filters.year,
                sort       = s.filters.sort,
            )
            FetchResult(items, items.isNotEmpty())
        } else {
            fetchExtension(sourceId, page)
        }
    }

    private suspend fun fetchExtension(sourceId: Long, page: Int): FetchResult =
        withContext(Dispatchers.IO) {
            val src = extSourceMap[sourceId] ?: return@withContext FetchResult(emptyList(), false)
            val filters = currentFilterList
            val query   = extQuery
            runCatching {
                // A query or touched filter searches the source; otherwise show its Latest feed
                // (if requested and supported) or its popular catalog as the default landing view.
                val ap = when {
                    query.isNotBlank() || extFiltersModified -> src.getSearchAnime(page, query, filters ?: AnimeFilterList())
                    extLatest && src.supportsLatest          -> src.getLatestUpdates(page)
                    else                                     -> src.getPopularAnime(page)
                }
                FetchResult(ap.animes.map { it.toBrowseMedia(sourceId) }, ap.hasNextPage)
            }.getOrElse { FetchResult(emptyList(), false) }
        }

    private fun SAnime.toBrowseMedia(sourceId: Long): Media = Media(
        id          = "$EXTERNAL_MEDIA_PREFIX$sourceId:$url",
        title       = title,
        poster      = thumbnail_url,
        serviceType = ServiceType.ANILIST,
    )
}
