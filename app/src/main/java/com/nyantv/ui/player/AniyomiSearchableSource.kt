package com.nyantv.player

import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.util.lang.awaitSingle

class AniyomiSearchableSource(
    private val httpSource: AnimeHttpSource,
    override val iconUrl: String?,
) : SearchableSource {

    override val id: Long get() = httpSource.id
    override val name: String get() = httpSource.name
    override val lang: String get() = httpSource.lang
    override val type: SearchableSource.SourceType get() = SearchableSource.SourceType.ANIYOMI

    override suspend fun search(query: String, page: Int): AnimesPage =
        httpSource.getSearchAnime(page, query, httpSource.getFilterList())
        //fetchSearchAnime(page, query, httpSource.getFilterList()).awaitSingle()

    override suspend fun getEpisodes(anime: SAnime): List<SEpisode> =
        httpSource.getEpisodeList(anime)

    override suspend fun getVideoList(episode: SEpisode): List<eu.kanade.tachiyomi.animesource.model.Video> {
        android.util.Log.d("PlayerTab", "getVideoList called for ${episode.url}")
        val result = httpSource.getVideoList(episode = episode)
        android.util.Log.d("PlayerTab", "getVideoList returned ${result.size} videos")
        result.forEach { v ->
            android.util.Log.d("PlayerTab", "  quality=${v.quality} url=${v.videoUrl} headers=${v.headers}")
        }
        return result
    }

    /** Expose raw source for video fetching (needed when building PlayerArgs) */
    fun rawSource(): AnimeHttpSource = httpSource
}