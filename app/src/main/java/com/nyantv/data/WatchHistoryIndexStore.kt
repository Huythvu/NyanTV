package com.nyantv.player

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * One locally-watched anime, with enough metadata to render a card and re-open it. This index is
 * what powers the offline "Continue Watching" home row and the Watch History settings screen; it's
 * written only when session tracking is enabled (the "track progress" master switch).
 */
@Serializable
data class WatchedEntry(
    val id:         String,            // media id: AniList/MAL/Simkl id, or "ext:<source>:<url>"
    val title:      String,
    val poster:     String?  = null,
    val episode:    Float    = 0f,
    val positionMs: Long     = 0L,
    val durationMs: Long     = 0L,
    val updatedAt:  Long     = 0L,
    val serviceKey: String   = "anilist_mal",
    val anilistId:  String?  = null,
    val malId:      String?  = null,
    val simklId:    String?  = null,
)

class WatchHistoryIndexStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("nyantv_watch_index", Context.MODE_PRIVATE)
    private val json  = Json { ignoreUnknownKeys = true }

    /** Newest first. */
    fun list(): List<WatchedEntry> {
        val raw = prefs.getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<WatchedEntry>>(raw) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.updatedAt }
    }

    /** Add or move-to-front the given anime, preserving newest-first order and the cap. */
    fun upsert(entry: WatchedEntry) {
        val rest = list().filterNot { it.id == entry.id }
        save((listOf(entry) + rest).take(CAP))
    }

    fun remove(id: String) = save(list().filterNot { it.id == id })

    fun clearAll() = prefs.edit { remove(KEY) }

    private fun save(entries: List<WatchedEntry>) {
        prefs.edit { putString(KEY, json.encodeToString(entries)) }
    }

    private companion object {
        const val KEY = "entries"
        const val CAP = 200
    }
}
