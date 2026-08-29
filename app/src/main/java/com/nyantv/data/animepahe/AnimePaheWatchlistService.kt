package com.nyantv.data.animepahe

import com.nyantv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    private val jsonType = "application/json".toMediaType()

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

    /** One anime to mark as watching in the shared list. */
    data class WatchUpdate(val anilistId: Int, val title: String, val thumb: String?)

    /** Mark one anime watching from a real watch event (bumps recency). Presence + status only. */
    suspend fun upsertWatching(phrase: String, anilistId: Int, title: String, thumb: String?): Result =
        upsert(phrase, listOf(WatchUpdate(anilistId, title, thumb)), bumpExisting = true)

    /**
     * Ensure a batch of anime are present as "watching" (e.g. syncing existing local Continue
     * Watching). Idempotent: adds missing ones and promotes plan->watching, but doesn't re-bump
     * entries already watching, so repeated calls don't churn the list order or trigger writes.
     */
    suspend fun upsertWatchingBatch(phrase: String, updates: List<WatchUpdate>): Result =
        upsert(phrase, updates, bumpExisting = false)

    /**
     * Read-merge-write, last-write-wins: pull the doc, update entries by anilistId (preserving any
     * AnimePahe link) or append minimal ones, cap at 70, and PATCH items back. No episode number is
     * pushed (Stage 2a). Skips the write entirely when nothing changed.
     */
    private suspend fun upsert(
        phrase: String,
        updates: List<WatchUpdate>,
        bumpExisting: Boolean,
    ): Result = withContext(Dispatchers.IO) {
        if (updates.isEmpty()) return@withContext Result.Ok(emptyList())
        if (!AnimePaheSyncKey.isValid(phrase)) return@withContext Result.Error("Sync phrase must be 5 valid words")

        val current = when (val r = fetch(phrase)) {
            is Result.Ok       -> r.items
            is Result.NotFound -> emptyList()
            is Result.Error    -> return@withContext r
        }

        val now  = System.currentTimeMillis()
        val list = current.toMutableList()
        var changed = false
        for (u in updates) {
            // Match by AniList id; fall back to a normalized-title match so we update (and backfill
            // the id on) entries that predate the anilistId field instead of creating duplicates.
            var idx = list.indexOfFirst { it.anilistId == u.anilistId }
            if (idx < 0) idx = list.indexOfFirst { it.anilistId == null && normTitle(it.title) == normTitle(u.title) }
            if (idx >= 0) {
                val e = list[idx]
                val alreadyWatching = e.status == AnimePaheEntry.STATUS_WATCHING
                val thumb = if (e.thumb.isBlank() && u.thumb != null) u.thumb else e.thumb
                when {
                    bumpExisting -> {
                        list[idx] = e.copy(anilistId = u.anilistId, status = AnimePaheEntry.STATUS_WATCHING,
                            ts = now, statusTs = now, title = e.title.ifBlank { u.title }, thumb = thumb); changed = true
                    }
                    !alreadyWatching -> {
                        list[idx] = e.copy(anilistId = u.anilistId, status = AnimePaheEntry.STATUS_WATCHING,
                            statusTs = now, title = e.title.ifBlank { u.title }, thumb = thumb); changed = true
                    }
                    e.anilistId == null -> { list[idx] = e.copy(anilistId = u.anilistId); changed = true }   // backfill id only
                }
            } else {
                list.add(
                    AnimePaheEntry(
                        title = u.title, thumb = u.thumb ?: "",
                        status = AnimePaheEntry.STATUS_WATCHING, ts = now, statusTs = now,
                        anilistId = u.anilistId,
                    )
                ); changed = true
            }
        }
        if (!changed) return@withContext Result.Ok(current)

        writeItems(phrase, list.sortedByDescending { it.sortTs }.take(70))   // rules cap items at 70
    }

    /** PATCH the items array (key-only; the write rules allow it by document id). */
    private fun writeItems(phrase: String, items: List<AnimePaheEntry>): Result {
        val docId = AnimePaheSyncKey.documentId(phrase)
        val url = "https://firestore.googleapis.com/v1/projects/" +
            "${BuildConfig.ANIMEPAHE_FIREBASE_PROJECT}/databases/(default)/documents/watchlists/" +
            "$docId?key=${BuildConfig.ANIMEPAHE_FIREBASE_API_KEY}&updateMask.fieldPaths=items"

        val body = buildJsonObject {
            putJsonObject("fields") {
                putJsonObject("items") {
                    putJsonObject("arrayValue") {
                        put("values", buildJsonArray { items.forEach { add(itemToValue(it)) } })
                    }
                }
            }
        }.toString()

        val req = Request.Builder().url(url).patch(body.toRequestBody(jsonType)).build()
        return try {
            http.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result.Ok(items) else Result.Error("Firestore HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            Result.Error(e.message ?: "Network error")
        }
    }

    /** One item as a Firestore `values[]` element (a mapValue). */
    private fun itemToValue(e: AnimePaheEntry): JsonObject = buildJsonObject {
        putJsonObject("mapValue") {
            putJsonObject("fields") {
                put("title",    strVal(e.title))
                put("episode",  strVal(e.episode))
                put("playUrl",  strVal(e.playUrl))
                put("animeUrl", strVal(e.animeUrl))
                put("thumb",    strVal(e.thumb))
                put("ts",       intVal(e.ts))
                put("status",   strVal(e.status))
                put("statusTs", intVal(e.statusTs))
                e.animeId?.let  { put("animeId",  intVal(it.toLong())) }
                e.anilistId?.let { put("anilistId", intVal(it.toLong())) }
            }
        }
    }

    private fun strVal(s: String): JsonObject = buildJsonObject { put("stringValue", s) }
    private fun intVal(n: Long): JsonObject = buildJsonObject { put("integerValue", n.toString()) }

    private fun normTitle(s: String): String = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

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
