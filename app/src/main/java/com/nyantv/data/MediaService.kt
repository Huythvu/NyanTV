package com.nyantv.data

import android.content.Context
import kotlinx.coroutines.flow.StateFlow

// ─── Service type registry ─────────────────────────────────────────────────────

enum class ServiceType(val label: String) {
    ANILIST("AniList"),
    MAL("MyAnimeList"),
    SIMKL("Simkl")
}

// ─── Core interface ────────────────────────────────────────────────────────────

interface MediaService {

    val serviceType: ServiceType

    // Auth state
    val isLoggedIn: StateFlow<Boolean>
    val profile: StateFlow<Profile?>

    // Tracking lists
    val animeList: StateFlow<List<TrackedMedia>>
    val currentMedia: StateFlow<TrackedMedia?>

    // Homepage data buckets
    val trending: StateFlow<List<Media>>
    val popular: StateFlow<List<Media>>
    val upcoming: StateFlow<List<Media>>
    val recentlyUpdated: StateFlow<List<Media>>

    // Network
    suspend fun fetchHomePage()
    suspend fun fetchDetails(id: String): Media
    suspend fun search(query: String): List<Media>

    // Auth
    /** Builds the OAuth authorize URL (and persists any PKCE verifier). Shown in an in-app
     *  WebView so login works on devices without a browser / Custom Tabs (e.g. Android TV). */
    fun authUrl(): String
    suspend fun logout()
    suspend fun autoLogin()

    // List management
    suspend fun updateEntry(id: String, status: String?, progress: Int?, score: Float?)
    suspend fun deleteEntry(id: String)
    suspend fun refreshUserLists()
    fun setCurrentMedia(id: String)
}

// ─── Service factory ───────────────────────────────────────────────────────────

fun buildService(type: ServiceType, context: Context): MediaService = when (type) {
    ServiceType.ANILIST -> AnilistService(context)
    ServiceType.MAL     -> MalService(context)
    ServiceType.SIMKL   -> SimklService(context)
}
