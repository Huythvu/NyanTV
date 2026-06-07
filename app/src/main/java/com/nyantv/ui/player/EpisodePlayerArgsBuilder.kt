package com.nyantv.player

import com.nyantv.ui.player.StreamTrack
import com.nyantv.ui.player.SubtitleTrack
import com.nyantv.ui.player.extractDomain
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource

/**
 * Builds PlayerArgs from a selected SEpisode.
 *
 * Call this from the navigation layer (NavHost / Activity) when the user
 * taps an episode in PlayerTabScreen.
 *
 * Usage:
 *
 *   onEpisodeSelected = { episode, allEpisodes, animeTitle ->
 *       val source = playerVm.state.value.selectedSource
 *           as? AniyomiSearchableSource ?: return@DetailScreen
 *       val anime  = playerVm.state.value.selectedAnime ?: return@DetailScreen
 *
 *       viewModelScope.launch {
 *           val args = EpisodePlayerArgsBuilder.build(
 *               source     = source,
 *               episode    = episode,
 *               animeTitle = animeTitle,
 *           )
 *           PlayerArgs.streams              = args.streams
 *           PlayerArgs.subtitleTracks       = args.subtitleTracks
 *           PlayerArgs.initialStreamIndex   = args.initialStreamIndex
 *           PlayerArgs.title                = args.title
 *           navController.navigate("player")
 *       }
 *   }
 */
object EpisodePlayerArgsBuilder {

    data class Result(
        val streams: List<StreamTrack>,
        val subtitleTracks: List<SubtitleTrack>,
        val initialStreamIndex: Int,
        val title: String,
    )

    suspend fun build(
        source: AniyomiSearchableSource,
        episode: SEpisode,
        animeTitle: String,
    ): Result {
        val raw: AnimeHttpSource = source.rawSource()
        val videos: List<Video> = raw.getVideoList(episode)

        val streams = videos.mapIndexed { i, v ->
            StreamTrack(
                name = v.videoTitle.ifBlank { "Stream ${i + 1}" },
                url  = v.videoUrl,
            )
        }

        val subtitles = videos.flatMap { v ->
            val streamDomain = extractDomain(v.videoUrl.takeIf { it.isNotBlank() && it != "null" } ?: v.videoPageUrl)
            v.subtitleTracks.map { sub -> Triple(sub.lang, sub.url, streamDomain) }
        }
            .groupBy { (lang, _, _) -> lang }
            .map { (lang, entries) ->
                SubtitleTrack(
                    name = lang,
                    urls = entries
                        .map { (_, url, domain) -> SubtitleTrack.SubtitleUrl(url = url, streamDomain = domain) }
                        .distinctBy { it.url },
                )
            }

        // Title: prefer episode name + number, fall back to anime title + number
        val epNum = episode.episode_number.let {
            if (it % 1f == 0f) it.toInt().toString() else "%.1f".format(it)
        }
        val title = when {
            episode.name.isNotBlank() && episode.name != "Episode $epNum" ->
                "Ep $epNum – ${episode.name}"
            else ->
                "$animeTitle – Episode $epNum"
        }

        return Result(
            streams             = streams,
            subtitleTracks      = subtitles,
            initialStreamIndex  = 0,
            title               = title,
        )
    }
}