package com.nyantv.player

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

interface SearchableSource {
    val id:      Long
    val name:    String
    val lang:    String
    val iconUrl: String?
    val type:    SourceType

    suspend fun search(query: String, page: Int = 1): AnimesPage
    suspend fun getEpisodes(anime: SAnime): List<SEpisode>
    suspend fun getVideoList(episode: SEpisode): List<Video>

    enum class SourceType { ANIYOMI }
}