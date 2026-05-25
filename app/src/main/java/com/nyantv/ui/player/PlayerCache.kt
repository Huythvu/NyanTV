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

    suspend fun saveQueryOverride(mediaId: String, query: String) {
        context.playerDataStore.edit { it[queryKey(mediaId)] = query }
    }

    suspend fun loadQueryOverride(mediaId: String): String? =
        context.playerDataStore.data.first()[queryKey(mediaId)]

    suspend fun clearResult(sourceId: Long, mediaId: String) {
        context.playerDataStore.edit { it.remove(resultKey(sourceId, mediaId)) }
    }
}