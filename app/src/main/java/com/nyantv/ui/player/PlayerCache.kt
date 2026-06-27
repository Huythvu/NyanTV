package com.nyantv.player

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.playerDataStore by preferencesDataStore(name = "player_prefs")

@Serializable
data class CachedAnimeResult(
    val url: String,
    val title: String,
    val thumbnail: String? = null,
)

/** Result of probing whether a source has a given anime, with the time it was checked. */
@Serializable
data class ProbeCacheEntry(
    val matched: Boolean,
    val result:  CachedAnimeResult? = null,
    val ts:      Long = 0L,
    val score:   Double = 0.0,   // fuzzy title-match score of [result] (0..1)
)

class PlayerCache(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sourceKey(serviceKey: String) = stringPreferencesKey("source_$serviceKey")

    // "result_{sourceId}_{mediaId}" → CachedAnimeResult JSON
    private fun resultKey(sourceId: Long, mediaId: String) =
        stringPreferencesKey("result_${sourceId}_$mediaId")

    // "query_{mediaId}" → user-overridden search query
    private fun queryKey(mediaId: String) = stringPreferencesKey("query_$mediaId")

    suspend fun saveSelectedSource(serviceKey: String, sourceId: Long) {
        context.playerDataStore.edit { it[sourceKey(serviceKey)] = sourceId.toString() }
    }

    suspend fun loadSelectedSourceId(serviceKey: String): Long? =
        context.playerDataStore.data.first()[sourceKey(serviceKey)]?.toLongOrNull()

    fun observeSelectedSourceId(serviceKey: String): kotlinx.coroutines.flow.Flow<Long?> =
        context.playerDataStore.data.map { it[sourceKey(serviceKey)]?.toLongOrNull() }

    suspend fun saveSelectedResult(sourceId: Long, mediaId: String, anime: SAnime) {
        val cached = CachedAnimeResult(
            url       = anime.url,
            title     = anime.title,
            thumbnail = anime.thumbnail_url,
        )
        context.playerDataStore.edit {
            it[resultKey(sourceId, mediaId)] = json.encodeToString(cached)
        }
    }

    suspend fun loadSelectedResult(sourceId: Long, mediaId: String): CachedAnimeResult? {
        val raw = context.playerDataStore.data.first()[resultKey(sourceId, mediaId)]
            ?: return null
        return runCatching { json.decodeFromString<CachedAnimeResult>(raw) }.getOrNull()
    }

    /**
     * Best-effort title/thumbnail for an anime we've played before, from any cached source result
     * or probe entry. Used to back-fill the watch-history index from old resume data. Returns the
     * result plus a probe timestamp (0 if unknown).
     */
    suspend fun findCachedResult(mediaId: String): Pair<CachedAnimeResult, Long>? {
        val entries = context.playerDataStore.data.first().asMap()
        // Prefer the explicitly selected result, then any probe match.
        entries.forEach { (k, v) ->
            if (k.name.startsWith("result_") && k.name.endsWith("_$mediaId") && v is String) {
                runCatching { json.decodeFromString<CachedAnimeResult>(v) }.getOrNull()?.let { return it to 0L }
            }
        }
        entries.forEach { (k, v) ->
            if (k.name.startsWith("probe_") && k.name.endsWith("_$mediaId") && v is String) {
                runCatching { json.decodeFromString<ProbeCacheEntry>(v) }.getOrNull()?.result?.let { return it to 0L }
            }
        }
        return null
    }

    suspend fun saveQueryOverride(mediaId: String, query: String) {
        context.playerDataStore.edit { it[queryKey(mediaId)] = query }
    }

    suspend fun loadQueryOverride(mediaId: String): String? =
        context.playerDataStore.data.first()[queryKey(mediaId)]

    suspend fun clearResult(sourceId: Long, mediaId: String) {
        context.playerDataStore.edit { it.remove(resultKey(sourceId, mediaId)) }
    }

    // ── Source probe cache ("does source X have anime Y?") ──────────────────────
    // "probe_{sourceId}_{mediaId}" → ProbeCacheEntry JSON
    private fun probeKey(sourceId: Long, mediaId: String) =
        stringPreferencesKey("probe_${sourceId}_$mediaId")

    suspend fun saveProbe(sourceId: Long, mediaId: String, matched: Boolean, anime: SAnime?, score: Double = 0.0) {
        val entry = ProbeCacheEntry(
            matched = matched,
            result  = anime?.let { CachedAnimeResult(it.url, it.title, it.thumbnail_url) },
            ts      = System.currentTimeMillis(),
            score   = score,
        )
        context.playerDataStore.edit { it[probeKey(sourceId, mediaId)] = json.encodeToString(entry) }
    }

    suspend fun loadProbe(sourceId: Long, mediaId: String): ProbeCacheEntry? {
        val raw = context.playerDataStore.data.first()[probeKey(sourceId, mediaId)] ?: return null
        return runCatching { json.decodeFromString<ProbeCacheEntry>(raw) }.getOrNull()
    }

    suspend fun clearProbe(sourceId: Long, mediaId: String) {
        context.playerDataStore.edit { it.remove(probeKey(sourceId, mediaId)) }
    }
}