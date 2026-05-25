package com.nyantv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.OkHttpClient
import okhttp3.Request

// ── Data classes ───────────────────────────────────────────────────────────────

data class SkipInterval(val startSec: Int, val endSec: Int)

data class EpisodeSkipTimes(
    val op:      SkipInterval? = null,
    val ed:      SkipInterval? = null,
    val recap:   SkipInterval? = null,
    val mixedOp: SkipInterval? = null,
    val mixedEd: SkipInterval? = null,
)

// ── AniSkip ────────────────────────────────────────────────────────────────────

object AniskipService {

    private const val TAG     = "AniskipService"
    private const val API_URL = "https://api.aniskip.com/v2/skip-times"

    private val client = OkHttpClient()
    private val json   = Json { ignoreUnknownKeys = true }

    /**
     * @param malId          MAL ID of the anime (as String)
     * @param episodeNumber  Episode number string (e.g. "1", "12")
     * @param episodeLength  Episode length in seconds — pass 0 if unknown
     */
    suspend fun getSkipTimes(
        malId:         String,
        episodeNumber: String,
        episodeLength: Int = 0,
    ): EpisodeSkipTimes? = withContext(Dispatchers.IO) {
        val url = "$API_URL/$malId/$episodeNumber" +
                "?types[]=op&types[]=ed&types[]=recap" +
                "&types[]=mixed-op&types[]=mixed-ed" +
                "&episodeLength=$episodeLength"
        try {
            val response = client.newCall(Request.Builder().url(url).build()).execute()
            if (response.code != 200) return@withContext null

            val body = json.parseToJsonElement(response.body.string()).jsonObject
            if (body["found"]?.jsonPrimitive?.boolean != true) return@withContext null

            val results = body["results"]?.jsonArray ?: return@withContext null

            var op:      SkipInterval? = null
            var ed:      SkipInterval? = null
            var recap:   SkipInterval? = null
            var mixedOp: SkipInterval? = null
            var mixedEd: SkipInterval? = null

            for (element in results) {
                val obj      = element.jsonObject
                val type     = obj["skipType"]?.jsonPrimitive?.content ?: continue
                val interval = obj["interval"]?.jsonObject ?: continue
                val start    = interval["startTime"]?.jsonPrimitive?.double?.toInt() ?: continue
                val end      = interval["endTime"]?.jsonPrimitive?.double?.toInt()   ?: continue
                val seg      = SkipInterval(start, end)

                when (type) {
                    "op"       -> op      = seg
                    "ed"       -> ed      = seg
                    "recap"    -> recap   = seg
                    "mixed-op" -> mixedOp = seg
                    "mixed-ed" -> mixedEd = seg
                }
            }

            if (op == null && ed == null && recap == null && mixedOp == null && mixedEd == null)
                return@withContext null

            EpisodeSkipTimes(op = op, ed = ed, recap = recap, mixedOp = mixedOp, mixedEd = mixedEd)

        } catch (e: Exception) {
            Log.e(TAG, "getSkipTimes failed", e)
            null
        }
    }
}