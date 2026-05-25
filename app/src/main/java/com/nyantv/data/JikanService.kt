package com.nyantv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

object JikanService {

    private const val API_URL = "https://api.jikan.moe/v4"
    private val client = OkHttpClient()
    private val json   = Json { ignoreUnknownKeys = true }

    suspend fun getFillerEpisodes(malId: String?): Set<Int> = withContext(Dispatchers.IO) {
        if (malId == null) return@withContext emptySet()
        val fillerSet   = mutableSetOf<Int>()
        var page        = 1
        var hasNextPage = true

        try {
            while (hasNextPage) {
                val request = Request.Builder()
                    .url("$API_URL/anime/$malId/episodes?page=$page")
                    .build()
                val response = client.newCall(request).execute()
                when (response.code) {
                    200 -> {
                        val body = json.parseToJsonElement(response.body.string()).jsonObject
                        val data = body["data"]?.jsonArray ?: break
                        if (data.isEmpty()) break
                        for (item in data) {
                            val obj    = item.jsonObject
                            val epNum  = obj["mal_id"]?.jsonPrimitive?.content?.toIntOrNull() ?: continue
                            val filler = obj["filler"]?.jsonPrimitive?.boolean ?: false
                            if (filler) fillerSet.add(epNum)
                        }
                        hasNextPage = body["pagination"]?.jsonObject
                            ?.get("has_next_page")?.jsonPrimitive?.boolean ?: false
                        page++
                        delay(300)
                    }
                    429 -> return@withContext fillerSet
                    else -> break
                }
            }
        } catch (e: Exception) {
            Log.e("JikanService", "getFillerEpisodes failed", e)
        }
        fillerSet
    }
}