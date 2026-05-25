package com.nyantv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

// ── IntroDb ────────────────────────────────────────────────────────────────────

object IntroDbService {

    private const val TAG     = "IntroDbService"
    private const val API_URL = "https://api.introdb.app/segments"

    private val client = OkHttpClient()
    private val json   = Json { ignoreUnknownKeys = true }

    /**
     * @param imdbId        IMDB ID of the show (e.g. "tt1234567")
     * @param season        Season number as string (e.g. "1")
     * @param episode       Episode number within that season (e.g. "3")
     */
    suspend fun getSkipTimes(
        imdbId:  String,
        season:  String,
        episode: String,
    ): EpisodeSkipTimes? = withContext(Dispatchers.IO) {
        val url = "$API_URL?imdb_id=$imdbId&season=$season&episode=$episode"
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.code != 200) return@withContext null

            val body = json.parseToJsonElement(response.body.string()).jsonObject

            val op    = body["intro"]?.jsonObject?.toSkipInterval()
            val ed    = body["outro"]?.jsonObject?.toSkipInterval()
            val recap = body["recap"]?.jsonObject?.toSkipInterval()

            if (op == null && ed == null && recap == null) return@withContext null

            EpisodeSkipTimes(op = op, ed = ed, recap = recap)

        } catch (e: Exception) {
            Log.e(TAG, "getSkipTimes failed", e)
            null
        }
    }

    private fun JsonObject.toSkipInterval(): SkipInterval? {
        val start = this["start_sec"]?.jsonPrimitive?.double?.toInt() ?: return null
        val end   = this["end_sec"]?.jsonPrimitive?.double?.toInt()   ?: return null
        return SkipInterval(start, end)
    }
}