package com.nyantv.player

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video

sealed interface SearchState {
    data object Idle    : SearchState
    data object Loading : SearchState
    data class  Results(val items: List<SAnime>) : SearchState
    data class  Error  (val message: String)     : SearchState
}

sealed interface EpisodeState {
    data object Idle    : EpisodeState
    data object Loading : EpisodeState
    data class  Success (val episodes: List<SEpisode>) : EpisodeState
    data class  Error   (val message: String)          : EpisodeState
}

sealed interface StreamState {
    data object Idle    : StreamState
    data object Loading : StreamState
    data class  Ready   (val videos: List<Video>) : StreamState
    data class  Error   (val message: String)     : StreamState
}