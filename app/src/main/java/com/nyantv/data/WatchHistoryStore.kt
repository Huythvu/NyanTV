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

    fun saveResume(key: String, p: EpisodeProgress)  = saveByKey("resume_$key", p)
    fun loadResume(key: String): EpisodeProgress?    = loadByKey("resume_$key")
    fun clearResume(key: String)                     = clearByKey("resume_$key")

    fun saveEpisodeProgress(key: String, p: EpisodeProgress) =
        saveByKey("ep_${key}_${p.episodeNumber.toInt()}", p)

    fun loadEpisodeProgress(key: String, episodeNumber: Float): EpisodeProgress? =
        loadByKey("ep_${key}_${episodeNumber.toInt()}")

    fun clearEpisodeProgress(key: String, episodeNumber: Float) =
        clearByKey("ep_${key}_${episodeNumber.toInt()}")

    // ── Watched Set ───────────────────────────────────────────────────────────

    fun markWatched(key: String, episodeNumber: Int) {
        val current = getWatchedEpisodes(key).toMutableSet()
        current.add(episodeNumber)
        prefs.edit { putString("watched_$key", current.joinToString(",")) }
    }

    fun getWatchedEpisodes(key: String): Set<Int> {
        val raw = prefs.getString("watched_$key", "") ?: return emptySet()
        if (raw.isBlank()) return emptySet()
        return raw.split(",").mapNotNull { it.toIntOrNull() }.toSet()
    }

    fun clearWatched(key: String) {
        prefs.edit { remove("watched_$key") }
    }

    // ── AniList + MAL ────────────────────────────────────────

    fun saveAnilistMal(anilistId: String?, malId: String?, progress: EpisodeProgress) {
        val key = resumeKey(anilistId, malId) ?: return
        saveResume(key, progress)
        saveEpisodeProgress(key, progress)
    }

    fun loadAnilistMal(anilistId: String?, malId: String?): EpisodeProgress? {
        val key = resumeKey(anilistId, malId) ?: return null
        return loadResume(key)
    }

    fun clearAnilistMal(anilistId: String?, malId: String?) {
        val key = resumeKey(anilistId, malId) ?: return
        clearResume(key)
    }

    fun markWatchedAnilistMal(anilistId: String?, malId: String?, episodeNumber: Int) {
        val key = resumeKey(anilistId, malId) ?: return
        markWatched(key, episodeNumber)
        clearEpisodeProgress(key, episodeNumber.toFloat())
    }

    fun getWatchedAnilistMal(anilistId: String?, malId: String?): Set<Int> {
        val key = resumeKey(anilistId, malId) ?: return emptySet()
        return getWatchedEpisodes(key)
    }

    fun loadEpisodeProgressAnilistMal(anilistId: String?, malId: String?, episodeNumber: Float): EpisodeProgress? {
        val key = resumeKey(anilistId, malId) ?: return null
        return loadEpisodeProgress(key, episodeNumber)
    }

    private fun resumeKey(anilistId: String?, malId: String?): String? =
        anilistId?.let { "al_$it" } ?: malId?.let { "mal_$it" }

    // ── Simkl ─────────────────────────────────────────────────────────────────

    fun saveSimkl(simklId: String, progress: EpisodeProgress) {
        saveResume("simkl_$simklId", progress)
        saveEpisodeProgress("simkl_$simklId", progress)
    }

    fun loadSimkl(simklId: String): EpisodeProgress? = loadResume("simkl_$simklId")
    fun clearSimkl(simklId: String)                  = clearResume("simkl_$simklId")

    fun markWatchedSimkl(simklId: String, episodeNumber: Int) {
        markWatched("simkl_$simklId", episodeNumber)
        clearEpisodeProgress("simkl_$simklId", episodeNumber.toFloat())
    }

    fun getWatchedSimkl(simklId: String): Set<Int> = getWatchedEpisodes("simkl_$simklId")

    fun loadEpisodeProgressSimkl(simklId: String, episodeNumber: Float): EpisodeProgress? =
        loadEpisodeProgress("simkl_$simklId", episodeNumber)
}