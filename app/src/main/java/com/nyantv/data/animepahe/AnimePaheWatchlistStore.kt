package com.nyantv.data.animepahe

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Local persistence for the AnimePahe Watchlist integration: the user's sync phrase, an enable flag,
 * and a cached snapshot of the last successful fetch (so the home rows render offline and instantly
 * on launch, before the network refresh completes).
 */
class AnimePaheWatchlistStore(context: Context) {

    private val prefs = context.getSharedPreferences("nyantv_animepahe", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var syncPhrase: String
        get() = prefs.getString(KEY_PHRASE, "") ?: ""
        set(value) = prefs.edit { putString(KEY_PHRASE, value.trim()) }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** True when the integration is switched on and holds a usable phrase. */
    val isConfigured: Boolean
        get() = enabled && AnimePaheSyncKey.isValid(syncPhrase)

    fun loadSnapshot(): AnimePaheWatchlistSnapshot {
        val raw = prefs.getString(KEY_SNAPSHOT, "") ?: ""
        if (raw.isBlank()) return AnimePaheWatchlistSnapshot()
        return runCatching { json.decodeFromString<AnimePaheWatchlistSnapshot>(raw) }
            .getOrDefault(AnimePaheWatchlistSnapshot())
    }

    fun saveSnapshot(items: List<AnimePaheEntry>) {
        val snap = AnimePaheWatchlistSnapshot(items = items, fetchedAt = System.currentTimeMillis())
        prefs.edit { putString(KEY_SNAPSHOT, json.encodeToString(snap)) }
    }

    fun clearSnapshot() = prefs.edit { remove(KEY_SNAPSHOT) }

    private companion object {
        const val KEY_PHRASE = "sync_phrase"
        const val KEY_ENABLED = "enabled"
        const val KEY_SNAPSHOT = "snapshot"
    }
}
