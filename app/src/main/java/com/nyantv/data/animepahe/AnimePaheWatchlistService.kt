package com.nyantv.data.animepahe

import com.nyantv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * Read-only client for the AnimePahe Watchlist Firestore document.
 *
 * Access model (verified against the live project): the security rules allow an unauthenticated
 * `get` on `watchlists/{sync_<64hex>}`, so we need only the public web API key and the phrase-derived
 * document id — no auth token, no Firebase SDK. Stage 1 never writes.
 */
class AnimePaheWatchlistService {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    /** Result of a fetch attempt, so callers can distinguish "empty list" from "couldn't read". */
    sealed interface Result {
        data class Ok(val items: List<AnimePaheEntry>) : Result
        /** The phrase-derived document does not exist yet (HTTP 404) — treat as an empty watchlist. */
        data object NotFound : Result
        data class Error(val message: String) : Result
    }

    suspend fun fetch(phrase: String): Result = withContext(Dispatchers.IO) {
        if (!AnimePaheSyncKey.isValid(phrase)) {
            return@withContext Result.Error("Sync phrase must be 5 valid words")
        }
        val docId = AnimePaheSyncKey.documentId(phrase)
        val url = "https://firestore.googleapis.com/v1/projects/" +
            "${BuildConfig.ANIMEPAHE_FIREBASE_PROJECT}/databases/(default)/documents/watchlists/" +
            "$docId?key=${BuildConfig.ANIMEPAHE_FIREBASE_API_KEY}"

        val req = Request.Builder().url(url).get().build()
        try {
            http.newCall(req).execute().use { resp ->
                when {
                    resp.code == 404 -> Result.NotFound
                    !resp.isSuccessful -> Result.Error("Firestore HTTP ${resp.code}")
                    else -> {
                        val body = resp.body?.string().orEmpty()
                        val root = json.parseToJsonElement(body).jsonObject
                        Result.Ok(parseDocument(root))
                    }
                }
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /** Walk the Firestore typed-value envelope: fields.items.arrayValue.values[].mapValue.fields.* */
    private fun parseDocument(root: JsonObject): List<AnimePaheEntry> {
        val fields = root["fields"]?.jsonObject ?: return emptyList()
        val values = fields["items"]?.jsonObject
            ?.get("arrayValue")?.jsonObject
            ?.get("values")?.jsonArray
            ?: return emptyList()

        return values.mapNotNull { value ->
            val f = value.jsonObject["mapValue"]?.jsonObject?.get("fields")?.jsonObject
                ?: return@mapNotNull null
            val title = f.str("title") ?: return@mapNotNull null
            if (title.isBlank()) return@mapNotNull null
            AnimePaheEntry(
                title    = title,
                episode  = f.str("episode") ?: "",
                playUrl  = f.str("playUrl") ?: "",
                animeUrl = f.str("animeUrl") ?: "",
                thumb    = f.str("thumb") ?: "",
                ts       = f.int("ts") ?: 0L,
                status   = f.str("status")?.takeIf { it == AnimePaheEntry.STATUS_PLAN }
                    ?: AnimePaheEntry.STATUS_WATCHING,
                statusTs = f.int("statusTs") ?: 0L,
                animeId  = f.int("animeId")?.toInt(),
                anilistId = f.int("anilistId")?.toInt(),
            )
        }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonObject?.get("stringValue")?.jsonPrimitive?.contentOrNull

    /** Firestore encodes integers as decimal strings under "integerValue". */
    private fun JsonObject.int(key: String): Long? =
        this[key]?.jsonObject?.get("integerValue")?.jsonPrimitive?.contentOrNull?.toLongOrNull()
}
