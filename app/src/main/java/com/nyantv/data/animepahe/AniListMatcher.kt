package com.nyantv.data.animepahe

import com.nyantv.data.Media
import com.nyantv.data.toMedia
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Minimal, self-contained AniList lookup used to turn AnimePahe watchlist entries into real AniList
 * media (canonical title, poster, id). Independent of the active tracker service so it works even
 * when the user is signed into MyAnimeList. Anonymous public GraphQL — no auth needed.
 */
class AniListMatcher {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val jsonType = "application/json".toMediaType()

    /** Fuzzy match by title — AniList's single best hit, or null. */
    suspend fun searchByTitle(title: String): Media? = withContext(Dispatchers.IO) {
        val vars = buildJsonObject { put("search", title) }
        request(SEARCH_QUERY, vars)
            ?.get("Page")?.jsonObject?.get("media")?.jsonArray
            ?.firstOrNull()?.jsonObject?.toMedia()
    }

    /** Resolve several AniList ids in one query. Returns id → media for those found. */
    suspend fun byIds(ids: List<Int>): Map<String, Media> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val vars = buildJsonObject {
            put("ids", buildJsonArray { ids.forEach { add(it) } })
        }
        request(BY_IDS_QUERY, vars)
            ?.get("Page")?.jsonObject?.get("media")?.jsonArray
            ?.mapNotNull { it.jsonObject.toMedia() }
            ?.associateBy { it.id }
            ?: emptyMap()
    }

    /** POST a GraphQL query; returns the `data` object or null on any failure. */
    private fun request(query: String, variables: JsonObject): JsonObject? = try {
        val payload = buildJsonObject {
            put("query", query)
            put("variables", variables)
        }.toString()
        val req = Request.Builder()
            .url("https://graphql.anilist.co")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .post(payload.toRequestBody(jsonType))
            .build()
        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            json.parseToJsonElement(resp.body?.string().orEmpty())
                .jsonObject["data"]?.jsonObject
        }
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MEDIA_FIELDS =
            "id title { romaji english native } coverImage { large color } format episodes"
        val SEARCH_QUERY =
            "query(\$search: String) { Page(perPage: 1) { media(search: \$search, type: ANIME) { $MEDIA_FIELDS } } }"
        val BY_IDS_QUERY =
            "query(\$ids: [Int]) { Page(perPage: 50) { media(id_in: \$ids, type: ANIME) { $MEDIA_FIELDS } } }"
    }
}
