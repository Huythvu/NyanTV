package com.nyantv.ui.player

import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder

/**
 * Lightweight Lingva Translate client.
 *
 * Lingva is an open-source front-end for Google Translate with no API key required.
 * Public instances: https://lingva.ml  (default)
 *
 * API: GET /api/v1/{source}/{target}/{encoded_text}
 * Response: { "translation": "..." }
 *
 * Results are cached in an LRU cache (max 512 entries) so repeated cues
 * (e.g. OP/ED lyrics that repeat) don't make duplicate requests.
 */
class LingvaTranslator(
    private val baseUrl: String = "https://lingva.ml",
    private val sourceLang: String = "auto",
    val targetLang: String
) {
    // Key = original text, value = translated text
    private val cache = LruCache<String, String>(512)

    suspend fun translate(text: String): String {
        if (text.isBlank()) return text
        cache.get(text)?.let { return it }

        return withContext(Dispatchers.IO) {
            runCatching {
                val encoded  = URLEncoder.encode(text, "UTF-8")
                val endpoint = "$baseUrl/api/v1/$sourceLang/$targetLang/$encoded"
                val json     = URL(endpoint).readText(Charsets.UTF_8)
                JSONObject(json).getString("translation")
            }.getOrDefault(text) // on failure, return original
        }.also { result ->
            cache.put(text, result)
        }
    }

    /** Translate a batch of strings, returning results in the same order. */
    suspend fun translateBatch(texts: List<String>): List<String> =
        texts.map { translate(it) }
}
