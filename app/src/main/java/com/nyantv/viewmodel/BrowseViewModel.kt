package com.nyantv.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nyantv.data.AnilistService
import com.nyantv.data.Media
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrowseFilters(
    val genres: Set<String> = emptySet(),
    val format: String? = null,
    val season: String? = null,
    val year: Int? = null,
    val sort: String = "POPULARITY_DESC",
)

data class BrowseState(
    val filters: BrowseFilters = BrowseFilters(),
    val results: List<Media> = emptyList(),
    val loading: Boolean = true,        // filter-change / initial load
    val loadingMore: Boolean = false,   // pagination
    val endReached: Boolean = false,
)

/** AniList-powered filtered discovery for the Anime browse tab (no login required). */
class BrowseViewModel(app: Application) : AndroidViewModel(app) {

    private val anilist = AnilistService(app)

    private val _state = MutableStateFlow(BrowseState())
    val state: StateFlow<BrowseState> = _state.asStateFlow()

    private var page = 1
    private var loadJob: Job? = null

    init { applyFilters(BrowseFilters()) }

    fun toggleGenre(genre: String) {
        val cur = _state.value.filters.genres
        applyFilters(_state.value.filters.copy(genres = if (genre in cur) cur - genre else cur + genre))
    }

    fun setFormat(format: String?) = applyFilters(_state.value.filters.copy(format = format))
    fun setSeason(season: String?) = applyFilters(_state.value.filters.copy(season = season))
    fun setYear(year: Int?)        = applyFilters(_state.value.filters.copy(year = year))
    fun setSort(sort: String)      = applyFilters(_state.value.filters.copy(sort = sort))

    private fun applyFilters(filters: BrowseFilters) {
        loadJob?.cancel()
        page = 1
        _state.update { it.copy(filters = filters, results = emptyList(), loading = true, endReached = false) }
        loadJob = viewModelScope.launch {
            val items = fetch(filters, 1)
            _state.update { it.copy(results = items, loading = false, endReached = items.isEmpty()) }
        }
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.loadingMore || s.endReached) return
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            val next  = page + 1
            val items = fetch(s.filters, next)
            if (items.isNotEmpty()) page = next
            _state.update {
                it.copy(
                    results     = (it.results + items).distinctBy { m -> m.id },
                    loadingMore = false,
                    endReached  = items.isEmpty(),
                )
            }
        }
    }

    private suspend fun fetch(filters: BrowseFilters, page: Int): List<Media> =
        anilist.browse(
            page       = page,
            genres     = filters.genres.toList(),
            format     = filters.format,
            season     = filters.season,
            seasonYear = filters.year,
            sort       = filters.sort,
        )
}
