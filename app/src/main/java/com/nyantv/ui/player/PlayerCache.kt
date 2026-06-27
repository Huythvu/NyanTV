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
     * Best-effort title/thumbnail for anime we've played before, resolved for many ids in a single
     * DataStore read. Used to back-fill the watch-history index from old resume data. Prefers an
     * explicitly selected source result over a probe match. Keys are "result_{sourceId}_{mediaId}"
     * / "probe_{sourceId}_{mediaId}"; sourceId is numeric, so the id is everything after the first
     * underscore (mediaIds may themselves contain underscores).
     */
    suspend fun findCachedResults(mediaIds: Set<String>): Map<String, CachedAnimeResult> {
        if (mediaIds.isEmpty()) return emptyMap()
        val entries = context.playerDataStore.data.first().asMap()
        val out = HashMap<String, CachedAnimeResult>()
        entries.forEach { (k, v) ->
            if (v !is String || !k.name.startsWith("result_")) return@forEach
            val mediaId = k.name.removePrefix("result_").substringAfter("_")
            if (mediaId in mediaIds && mediaId !in out) {
                runCatching { json.decodeFromString<CachedAnimeResult>(v) }.getOrNull()?.let { out[mediaId] = it }
            }
        }
        entries.forEach { (k, v) ->
            if (v !is String || !k.name.startsWith("probe_")) return@forEach
            val mediaId = k.name.removePrefix("probe_").substringAfter("_")
            if (mediaId in mediaIds && mediaId !in out) {
                runCatching { json.decodeFromString<ProbeCacheEntry>(v) }.getOrNull()?.result?.let { out[mediaId] = it }
            }
        }
        return out
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

    /** One probe result, for batched persistence. */
    data class ProbeWrite(val sourceId: Long, val matched: Boolean, val anime: SAnime?, val score: Double)

    /**
     * Persist many probe results in a single transaction. DataStore rewrites the whole file per
     * edit, so writing all of a probe's results at once is one disk write instead of one per source.
     */
    suspend fun saveProbes(mediaId: String, writes: List<ProbeWrite>) {
        if (writes.isEmpty()) return
        val now = System.currentTimeMillis()
        context.playerDataStore.edit { prefs ->
            writes.forEach { w ->
                val entry = ProbeCacheEntry(
                    matched = w.matched,
                    result  = w.anime?.let { CachedAnimeResult(it.url, it.title, it.thumbnail_url) },
                    ts      = now,
                    score   = w.score,
                )
                prefs[probeKey(w.sourceId, mediaId)] = json.encodeToString(entry)
            }
        }
    }

    suspend fun loadProbe(sourceId: Long, mediaId: String): ProbeCacheEntry? {
        val raw = context.playerDataStore.data.first()[probeKey(sourceId, mediaId)] ?: return null
        return runCatching { json.decodeFromString<ProbeCacheEntry>(raw) }.getOrNull()
    }

    suspend fun clearProbe(sourceId: Long, mediaId: String) {
        context.playerDataStore.edit { it.remove(probeKey(sourceId, mediaId)) }
    }
}