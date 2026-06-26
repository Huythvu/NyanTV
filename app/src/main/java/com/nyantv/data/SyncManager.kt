package com.nyantv.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "SyncManager"

/**
 * Handles live cross-service sync between AniList ↔ MAL.
 *
 * Only syncs entry updates (status / progress / score).
 * Does NOT do a full account transfer.
 *
 * ID mapping is cached in-memory so repeated updates on the same
 * anime don't need extra network calls.
 */
class SyncManager(
    private val anilist: AnilistService,
    private val mal: MalService,
) {
    // anilistId (String) → malId (String)
    private val anilistToMal = mutableMapOf<String, String>()
    // malId (String) → anilistId (String)  (reverse, built lazily)
    private val malToAnilist = mutableMapOf<String, String>()

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    // ── Public entry points ────────────────────────────────────────────────────

    /**
     * Call this after a successful AniList updateEntry.
     * Will mirror the change to MAL if MAL is also logged in.
     */
    suspend fun syncFromAnilist(
        anilistId: String,
        status: String?,
        progress: Int?,
        score: Float?,
    ) = withContext(Dispatchers.IO) {
        if (!mal.isLoggedIn.value) {
            Log.d(TAG, "MAL not logged in – skipping sync from AniList")
            return@withContext
        }

        val malId = resolveMalId(anilistId) ?: run {
            Log.w(TAG, "Could not resolve MAL id for AniList id=$anilistId")
            return@withContext
        }

        Log.d(TAG, "Syncing AniList($anilistId) → MAL($malId) " +
                "status=$status progress=$progress score=$score")

        runCatching {
            mal.updateEntry(malId, status, progress, score)
        }.onFailure {
            Log.e(TAG, "MAL updateEntry failed for malId=$malId", it)
        }
    }
    /**
     * Call this after a successful MAL updateEntry.
     * Will mirror the change to AniList if AniList is also logged in.
     */
    suspend fun syncFromMal(
        malId: String,
        status: String?,
        progress: Int?,
        score: Float?,
    ) = withContext(Dispatchers.IO) {
        if (!anilist.isLoggedIn.value) {
            Log.d(TAG, "AniList not logged in – skipping sync from MAL")
            return@withContext
        }

        val anilistId = resolveAnilistId(malId) ?: run {
            Log.w(TAG, "Could not resolve AniList id for MAL id=$malId")
            return@withContext
        }

        Log.d(TAG, "Syncing MAL($malId) → AniList($anilistId) " +
                "status=$status progress=$progress score=$score")

        runCatching {
            anilist.updateEntry(anilistId, status, progress, score)
        }.onFailure {
            Log.e(TAG, "AniList updateEntry failed for anilistId=$anilistId", it)
        }
    }

    /** Mirror an AniList delete to MAL (if MAL is logged in and an id mapping exists). */
    suspend fun syncDeleteFromAnilist(anilistId: String) = withContext(Dispatchers.IO) {
        if (!mal.isLoggedIn.value) {
            Log.d(TAG, "MAL not logged in – skipping delete sync from AniList")
            return@withContext
        }
        val malId = resolveMalId(anilistId) ?: run {
            Log.w(TAG, "Could not resolve MAL id for AniList id=$anilistId (delete)")
            return@withContext
        }
        Log.d(TAG, "Deleting on MAL($malId) to mirror AniList($anilistId)")
        runCatching { mal.deleteEntry(malId) }
            .onFailure { Log.e(TAG, "MAL deleteEntry failed for malId=$malId", it) }
    }

    /** Mirror a MAL delete to AniList (if AniList is logged in and an id mapping exists). */
    suspend fun syncDeleteFromMal(malId: String) = withContext(Dispatchers.IO) {
        if (!anilist.isLoggedIn.value) {
            Log.d(TAG, "AniList not logged in – skipping delete sync from MAL")
            return@withContext
        }
        val anilistId = resolveAnilistId(malId) ?: run {
            Log.w(TAG, "Could not resolve AniList id for MAL id=$malId (delete)")
            return@withContext
        }
        Log.d(TAG, "Deleting on AniList($anilistId) to mirror MAL($malId)")
        runCatching { anilist.deleteEntry(anilistId) }
            .onFailure { Log.e(TAG, "AniList deleteEntry failed for anilistId=$anilistId", it) }
    }

    // ── ID resolution ──────────────────────────────────────────────────────────

    /**
     * Resolves the MAL id for a given AniList id.
     * Uses the in-memory cache first, then calls AniList's detail endpoint
     * which already returns `idMal` in the existing DETAILS_QUERY.
     */
    private suspend fun resolveMalId(anilistId: String): String? {
        anilistToMal[anilistId]?.let { return it }

        // AniList details already fetch idMal – reuse that
        return runCatching {
            val media = anilist.fetchDetails(anilistId)
            // Media.idMal must be exposed; see note below
            media.idMal?.also { malId ->
                anilistToMal[anilistId] = malId
                malToAnilist[malId] = anilistId
            }
        }.getOrNull()
    }

    /**
     * Resolves the AniList id for a given MAL id.
     * Uses the in-memory cache first, then asks AniList via Media(idMal: X).
     */
    private suspend fun resolveAnilistId(malId: String): String? {
        malToAnilist[malId]?.let { return it }

        // Query AniList for the anime that has this MAL id
        return runCatching {
            fetchAnilistIdByMalId(malId)?.also { anilistId ->
                malToAnilist[malId] = anilistId
                anilistToMal[anilistId] = malId
            }
        }.getOrNull()
    }

    /**
     * Lightweight AniList query: given a MAL id, return the AniList id.
     * We do this directly here to avoid modifying AnilistService's public API.
     */
    private suspend fun fetchAnilistIdByMalId(malId: String): String? =
        withContext(Dispatchers.IO) {
            val query = $$"""
                query($idMal: Int) {
                  Media(idMal: $idMal, type: ANIME) { id }
                }
            """.trimIndent()
            val vars = buildJsonObject { put("idMal", malId.toIntOrNull() ?: return@withContext null) }
            val body = buildJsonObject {
                put("query", query)
                put("variables", vars)
            }
            val req = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build()

            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) return@withContext null
            val data = runCatching {
                json.parseToJsonElement(resp.body.string()).jsonObject
            }.getOrNull() ?: return@withContext null

            data["data"]?.jsonObject
                ?.get("Media")?.jsonObject
                ?.get("id")?.jsonPrimitive
                ?.contentOrNull
        }
}