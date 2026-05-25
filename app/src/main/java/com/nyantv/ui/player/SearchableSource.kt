package com.nyantv.player

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * Abstraction over different extension types.
 * To add a new type later (CloudStream, Jellyfin, etc.):
 * implement this interface and register in PlayerTabViewModel.buildSources()
 */
interface SearchableSource {
    val id: Long
    val name: String
    val lang: String
    val iconUrl: String?
    val type: SourceType

    suspend fun search(query: String, page: Int = 1): AnimesPage
    suspend fun getEpisodes(anime: SAnime): List<SEpisode>

    suspend fun getVideoList(episode: SEpisode): List<eu.kanade.tachiyomi.animesource.model.Video>
    enum class SourceType { ANIYOMI }
}