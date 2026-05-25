package com.nyantv.player

import android.content.Context
import androidx.core.content.edit

data class EpisodeProgress(
    val episodeNumber: Float,
    val positionMs:    Long,
    val durationMs:    Long,
)

class WatchHistoryStore(context: Context) {

    private val prefs = context.getSharedPreferences("nyantv_watch_history", Context.MODE_PRIVATE)

    private fun saveByKey(key: String, p: EpisodeProgress) {
        prefs.edit {
            putFloat("${key}_ep",  p.episodeNumber)
            putLong ("${key}_pos", p.positionMs)
            putLong ("${key}_dur", p.durationMs)
        }
    }

    private fun loadByKey(key: String): EpisodeProgress? {
        val ep  = prefs.getFloat("${key}_ep",  -1f)
        val pos = prefs.getLong ("${key}_pos", -1L)
        val dur = prefs.getLong ("${key}_dur", -1L)
        if (ep < 0f || pos < 0L || dur <= 0L) return null
        return EpisodeProgress(ep, pos, dur)
    }

    private fun clearByKey(key: String) {
        prefs.edit {
            remove("${key}_ep")
            remove("${key}_pos")
            remove("${key}_dur")
        }
    }

    // ── AniList + MAL ────────────────────

    fun saveAnilistMal(anilistId: String?, malId: String?, progress: EpisodeProgress) {
        anilistId?.let { saveByKey("al_$it",  progress) }
        malId?.let     { saveByKey("mal_$it", progress) }
    }

    fun loadAnilistMal(anilistId: String?, malId: String?): EpisodeProgress? =
        anilistId?.let { loadByKey("al_$it") }
            ?: malId?.let { loadByKey("mal_$it") }

    fun clearAnilistMal(anilistId: String?, malId: String?) {
        anilistId?.let { clearByKey("al_$it") }
        malId?.let     { clearByKey("mal_$it") }
    }

    // ── Simkl ───────────────────────────────────────────────────────

    fun saveSimkl(simklId: String, progress: EpisodeProgress) = saveByKey("simkl_$simklId", progress)
    fun loadSimkl(simklId: String): EpisodeProgress?          = loadByKey("simkl_$simklId")
    fun clearSimkl(simklId: String)                           = clearByKey("simkl_$simklId")
}