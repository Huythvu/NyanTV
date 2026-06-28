package com.nyantv.data

import com.nyantv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A pairing session minted by the relay: the code/URL the TV shows the user. */
data class PairSession(val code: String, val verifyUrl: String, val expiresIn: Int)

sealed interface PollResult {
    data object Pending : PollResult
    data class  Done(val accessToken: String) : PollResult                 // legacy: relay exchanged the token
    // app exchanges the code; codeVerifier is set for PKCE providers (MAL)
    data class  Code(val authCode: String, val redirectUri: String, val codeVerifier: String? = null) : PollResult
    data object Expired : PollResult
}

/**
 * Talks to the NyanTV pairing relay (see /pair-server). The TV mints a session, shows the code/QR,
 * and polls until the user finishes logging in on their phone/PC.
 */
class PairingClient(private val baseUrl: String = BuildConfig.PAIR_BASE_URL) {

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    /** Why the last [newSession] failed (HTTP status / exception), for surfacing in the UI. */
    var lastError: String? = null
        private set

    suspend fun newSession(provider: String = "anilist"): PairSession? = withContext(Dispatchers.IO) {
        lastError = null
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/pair/new?provider=$provider")
                .post("".toRequestBody("application/json".toMediaType()))
                .build()
            http.newCall(req).execute().use { resp ->
                val body = resp.body.string()
                if (!resp.isSuccessful) {
                    lastError = "HTTP ${resp.code}" + body.take(120).let { if (it.isNotBlank()) ": $it" else "" }
                    android.util.Log.e("PairingClient", "newSession failed: $lastError")
                    return@use null
                }
                val o = json.parseToJsonElement(body).jsonObject
                val code = o["code"]?.jsonPrimitive?.contentOrNull ?: return@use null
                val url  = o["verifyUrl"]?.jsonPrimitive?.contentOrNull ?: return@use null
                PairSession(code, url, o["expiresIn"]?.jsonPrimitive?.intOrNull ?: 600)
            }
        } catch (e: Exception) {
            lastError = e.message ?: e.javaClass.simpleName
            android.util.Log.e("PairingClient", "newSession error: $lastError", e)
            null
        }
    }

    suspend fun poll(code: String): PollResult = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("$baseUrl/api/pair/poll?code=$code").build()
            http.newCall(req).execute().use { resp ->
                // The relay returns the body even on the 404 "expired" case, so parse regardless.
                val o = json.parseToJsonElement(resp.body.string()).jsonObject
                when (o["status"]?.jsonPrimitive?.contentOrNull) {
                    "done" -> {
                        val code     = o["code"]?.jsonPrimitive?.contentOrNull
                        val redirect = o["redirectUri"]?.jsonPrimitive?.contentOrNull
                        val verifier = o["codeVerifier"]?.jsonPrimitive?.contentOrNull
                        val token    = o["accessToken"]?.jsonPrimitive?.contentOrNull
                        when {
                            code != null && redirect != null -> PollResult.Code(code, redirect, verifier)
                            token != null                    -> PollResult.Done(token)
                            else                             -> PollResult.Pending
                        }
                    }
                    "expired" -> PollResult.Expired
                    else      -> PollResult.Pending
                }
            }
        }.getOrElse { PollResult.Pending }  // transient network error → keep polling
    }
}
