package com.nyantv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nyantv.data.AnilistService
import com.nyantv.data.Media
import com.nyantv.data.ServiceType
import com.nyantv.extensions.AniyomiExtensions
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

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    /** id → raw extension source, for catalog/browse calls. */
    private val extSourceMap = mutableMapOf<Long, AnimeHttpSource>()

    private var page = 1
    private var loadJob: Job? = null

    private data class FetchResult(val items: List<Media>, val hasNext: Boolean)

    init {
        applyFilters(BrowseFilters())
        viewModelScope.launch {
            aniyomi.installedExtensions.collect { exts ->
                extSourceMap.clear()
                val infos = exts.flatMap { ext ->
                    ext.sources.filterIsInstance<AnimeHttpSource>().map { src ->
                        extSourceMap[src.id] = src
                        ExtSourceInfo(id = src.id, name = src.name, lang = src.lang)
                    }
                }.distinctBy { it.id }.sortedBy { it.name.lowercase() }
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
    fun setSeason(season: String?) = applyFilters(_state.value.filters.copy(season = season))
    fun setYear(year: Int?)        = applyFilters(_state.value.filters.copy(year = year))
    fun setSort(sort: String)      = applyFilters(_state.value.filters.copy(sort = sort))

    /** Switch between AniList browse (null) and a specific extension catalog. */
    fun selectSource(sourceId: Long?) {
        if (sourceId == _state.value.selectedSourceId) return
        loadJob?.cancel()
        page = 1
        _state.update {
            it.copy(selectedSourceId = sourceId, results = emptyList(), loading = true, endReached = false)
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
            runCatching {
                val ap = src.getPopularAnime(page)
                FetchResult(ap.animes.map { it.toBrowseMedia(sourceId) }, ap.hasNextPage)
            }.getOrElse { FetchResult(emptyList(), false) }
        }

    private fun SAnime.toBrowseMedia(sourceId: Long): Media = Media(
        id          = "ext:$sourceId:$url",
        title       = title,
        poster      = thumbnail_url,
        serviceType = ServiceType.ANILIST,
    )
}
