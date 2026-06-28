package com.nyantv.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nyantv.data.*
import com.nyantv.player.WatchHistoryStore
import com.nyantv.player.WatchHistoryIndexStore
import com.nyantv.player.WatchedEntry
import com.nyantv.ui.theme.AppTheme
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.nyantv.ui.utils.NetworkState
import kotlinx.coroutines.delay
import androidx.core.content.edit
import com.nyantv.ui.theme.ActiveTheme
import com.nyantv.ui.theme.CustomTheme
import com.nyantv.ui.widgets.CarouselLogoResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import com.nyantv.extensions.AniyomiExtensions
import com.nyantv.extensions.ExtensionOrderStore
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource

class AppViewModel(app: Application) : AndroidViewModel(app) {

    // ── Service management ─────────────────────────────────────────────────────
    private val prefs = app.getSharedPreferences("nyantv_prefs", Context.MODE_PRIVATE)

    private val _serviceType = MutableStateFlow(
        ServiceType.valueOf(
            prefs.getString("service_type", ServiceType.ANILIST.name) ?: ServiceType.ANILIST.name
        )
    )
    val serviceType: StateFlow<ServiceType> = _serviceType.asStateFlow()

    private var _service: MediaService = buildService(_serviceType.value, app)
    private val serviceJobs = mutableListOf<kotlinx.coroutines.Job>()

    private var sideService: MediaService? = buildSideService(_serviceType.value, app)
    private var syncManager: SyncManager?  = buildSyncManager()


// ── Theme ──────────────────────────────────────────────────────────────────

    private val _activeTheme = MutableStateFlow(
        prefs.getString("active_theme_builtin", null)
            ?.let { runCatching { ActiveTheme.BuiltIn(AppTheme.valueOf(it)) }.getOrNull() }
            ?: run {
                val customName = prefs.getString("active_theme_custom", null)
                val customJson = prefs.getString("custom_themes", null)
                if (customName != null && customJson != null) {
                    runCatching {
                        val arr = org.json.JSONArray(customJson)
                        val match = (0 until arr.length())
                            .map { CustomTheme.fromJson(arr.getString(it)) }
                            .firstOrNull { it.name == customName }
                        match?.let { ActiveTheme.Custom(it) }
                    }.getOrNull()
                } else null
            }
            ?: ActiveTheme.BuiltIn(AppTheme.SAKURA)
    )
    val activeTheme: StateFlow<ActiveTheme> = _activeTheme.asStateFlow()

    private val _customThemes = MutableStateFlow(
        prefs.getString("custom_themes", null)
            ?.let { json ->
                runCatching {
                    val arr = org.json.JSONArray(json)
                    List(arr.length()) { i -> CustomTheme.fromJson(arr.getString(i)) }
                }.getOrElse { emptyList() }
            } ?: emptyList()
    )
    val customThemes: StateFlow<List<CustomTheme>> = _customThemes.asStateFlow()

    fun setTheme(t: AppTheme) {
        _activeTheme.value = ActiveTheme.BuiltIn(t)
        prefs.edit {
            putString("active_theme_builtin", t.name)
            remove("active_theme_custom")
        }
    }

    fun setCustomTheme(t: CustomTheme) {
        _activeTheme.value = ActiveTheme.Custom(t)
        prefs.edit {
            putString("active_theme_custom", t.name)
            remove("active_theme_builtin")
        }
    }

    fun importThemeJson(json: String): Result<CustomTheme> = runCatching {
        val theme = CustomTheme.fromJson(json)
        val updated = (_customThemes.value + theme).distinctBy { it.name }
        _customThemes.value = updated
        prefs.edit { putString("custom_themes", org.json.JSONArray(updated.map { it.toJson() }).toString()) }
        theme
    }

    fun deleteCustomTheme(t: CustomTheme) {
        val updated = _customThemes.value - t
        _customThemes.value = updated
        prefs.edit { putString("custom_themes", org.json.JSONArray(updated.map { it.toJson() }).toString()) }
        if (_activeTheme.value == ActiveTheme.Custom(t)) setTheme(AppTheme.SAKURA)
    }

    // ── Passthrough flows ──────────────────────────────────────────────────────
    private val _isLoggedIn      = MutableStateFlow(false)
    private val _profile         = MutableStateFlow<Profile?>(null)
    private val _animeList       = MutableStateFlow<List<TrackedMedia>>(emptyList())
    private val _currentMedia    = MutableStateFlow<TrackedMedia?>(null)
    private val _trending        = MutableStateFlow<List<Media>>(emptyList())
    private val _popular         = MutableStateFlow<List<Media>>(emptyList())
    private val _upcoming        = MutableStateFlow<List<Media>>(emptyList())
    private val _recentlyUpdated = MutableStateFlow<List<Media>>(emptyList())
    private val _searchResults   = MutableStateFlow<List<Media>>(emptyList())
    private val _networkState    = MutableStateFlow(NetworkState.LOADING)

    private val _trendingMovies = MutableStateFlow<List<Media>>(emptyList())
    private val _trendingShows  = MutableStateFlow<List<Media>>(emptyList())
    private val _seasonal       = MutableStateFlow<List<Media>>(emptyList())
    private val _seasonLabel     = MutableStateFlow("")

    private val _carouselLogos     = MutableStateFlow<Map<String, String?>>(emptyMap())
    private val _carouselBackdrops = MutableStateFlow<Map<String, String?>>(emptyMap())
    val carouselLogos:     StateFlow<Map<String, String?>> = _carouselLogos.asStateFlow()
    val carouselBackdrops: StateFlow<Map<String, String?>> = _carouselBackdrops.asStateFlow()

    val isLoggedIn:      StateFlow<Boolean>            = _isLoggedIn.asStateFlow()
    val profile:         StateFlow<Profile?>           = _profile.asStateFlow()
    val animeList:       StateFlow<List<TrackedMedia>> = _animeList.asStateFlow()
    val currentMedia:    StateFlow<TrackedMedia?>      = _currentMedia.asStateFlow()
    val trending:        StateFlow<List<Media>>        = _trending.asStateFlow()
    val popular:         StateFlow<List<Media>>        = _popular.asStateFlow()
    val upcoming:        StateFlow<List<Media>>        = _upcoming.asStateFlow()
    val recentlyUpdated: StateFlow<List<Media>>        = _recentlyUpdated.asStateFlow()
    val searchResults:   StateFlow<List<Media>>        = _searchResults.asStateFlow()
    val networkState:    StateFlow<NetworkState>       = _networkState.asStateFlow()
    val trendingMovies: StateFlow<List<Media>> = _trendingMovies.asStateFlow()
    val trendingShows:  StateFlow<List<Media>> = _trendingShows.asStateFlow()
    val seasonal:       StateFlow<List<Media>> = _seasonal.asStateFlow()
    val seasonLabel:    StateFlow<String>      = _seasonLabel.asStateFlow()

    // ── Local "Continue Watching" (offline recently-watched, incl. extension entries) ──────────
    private val watchHistoryStore = WatchHistoryStore(app)
    private val historyIndex      = WatchHistoryIndexStore(app)
    private val _localContinue    = MutableStateFlow<List<Media>>(emptyList())
    val localContinue: StateFlow<List<Media>> = _localContinue.asStateFlow()

    /** Re-read the recently-watched index into [localContinue] (call on home show / player return). */
    fun refreshLocalContinue() {
        _localContinue.value = historyIndex.list().map { e ->
            Media(
                id          = e.id,
                title       = e.title,
                poster      = e.poster,
                serviceType = ServiceType.ANILIST,
                idMal       = e.malId,
            ).also { if (e.id.isExternalMediaId()) registerExternalMedia(it) }
        }
    }

    /** Full local watch history (newest first), for the Watch History settings screen. */
    fun watchHistoryEntries(): List<WatchedEntry> = historyIndex.list()

    /**
     * Back-fill the watch-history index from resume progress saved before the index existed.
     * Titles/posters are recovered from the player's source-result cache; entries we can't resolve
     * are skipped. Runs once automatically; [force] re-scans on demand.
     */
    fun importExistingHistory(force: Boolean = false) {
        if (!force && prefs.getBoolean("watch_history_imported", false)) return
        viewModelScope.launch(Dispatchers.IO) {
            val playerCache = com.nyantv.player.PlayerCache(getApplication())
            val known = historyIndex.list().map { it.id }.toMutableSet()
            val baseline = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000  // sit below fresh watches
            val resumeList = watchHistoryStore.allResumeProgress()
            // Resolve every candidate's title/poster in a single DataStore read instead of one
            // full-store scan per entry.
            val candidateIds = resumeList.mapNotNull { (base, _) ->
                when {
                    base.startsWith("al_")    -> base.removePrefix("al_")
                    base.startsWith("mal_")   -> base.removePrefix("mal_")
                    base.startsWith("simkl_") -> base.removePrefix("simkl_")
                    else -> null
                }
            }.filter { it !in known }.toSet()
            val recoveredMap = playerCache.findCachedResults(candidateIds)
            resumeList.forEachIndexed { i, (base, prog) ->
                val anilistId = if (base.startsWith("al_"))    base.removePrefix("al_")    else null
                val malId     = if (base.startsWith("mal_"))   base.removePrefix("mal_")   else null
                val simklId   = if (base.startsWith("simkl_")) base.removePrefix("simkl_") else null
                val mediaId   = anilistId ?: malId ?: simklId ?: return@forEachIndexed
                if (mediaId in known) return@forEachIndexed
                val recovered = recoveredMap[mediaId] ?: return@forEachIndexed
                historyIndex.upsert(
                    WatchedEntry(
                        id         = mediaId,
                        title      = recovered.title,
                        poster     = recovered.thumbnail,
                        episode    = prog.episodeNumber,
                        positionMs = prog.positionMs,
                        durationMs = prog.durationMs,
                        updatedAt  = baseline - i,
                        serviceKey = if (simklId != null) "simkl" else "anilist_mal",
                        anilistId  = anilistId,
                        malId      = malId,
                        simklId    = simklId,
                    )
                )
                known += mediaId
            }
            prefs.edit { putBoolean("watch_history_imported", true) }
            refreshLocalContinue()
        }
    }

    /** Wipe the entire local watch history and every anime's local progress. */
    fun clearAllWatchHistory() {
        historyIndex.list().forEach { e ->
            if (e.serviceKey == "simkl" && e.simklId != null) watchHistoryStore.clearAllForSimkl(e.simklId)
            else watchHistoryStore.clearAllForAnilistMal(e.anilistId, e.malId)
        }
        historyIndex.clearAll()
        refreshLocalContinue()
    }

    /** Remove an anime from the local watch list, wiping all of its local progress too. */
    fun removeFromLocalWatch(id: String) {
        val entry = historyIndex.list().firstOrNull { it.id == id }
        historyIndex.remove(id)
        if (entry != null) {
            if (entry.serviceKey == "simkl" && entry.simklId != null) {
                watchHistoryStore.clearAllForSimkl(entry.simklId)
            } else {
                watchHistoryStore.clearAllForAnilistMal(entry.anilistId, entry.malId)
            }
        }
        refreshLocalContinue()
    }

    init {
        viewModelScope.launch {
            awaitAll(
                async { _service.autoLogin() },
                async { sideService?.autoLogin() }
            )
            bindService()
            loadHome()
            importExistingHistory()
        }
    }


    // ── Service switching ──────────────────────────────────────────────────────

    fun switchService(type: ServiceType) {
        if (type == _serviceType.value) return
        _serviceType.value = type
        prefs.edit { putString("service_type", type.name) }

        serviceJobs.forEach { it.cancel() }
        serviceJobs.clear()

        val cached = loadProfileCache(type)

        _isLoggedIn.value        = cached != null
        _profile.value           = cached
        _animeList.value         = emptyList()
        _trending.value          = emptyList()
        _carouselLogos.value     = emptyMap()
        _carouselBackdrops.value = emptyMap()
        _popular.value           = emptyList()
        _upcoming.value          = emptyList()
        _recentlyUpdated.value   = emptyList()
        _trendingMovies.value    = emptyList()
        _trendingShows.value     = emptyList()
        _seasonal.value          = emptyList()
        _seasonLabel.value       = ""

        _service     = buildService(type, getApplication())
        sideService  = buildSideService(type, getApplication())
        syncManager  = buildSyncManager()
        bindService()

        viewModelScope.launch {
            _service.autoLogin()
            sideService?.autoLogin()
            loadHome()
            System.gc()
        }
    }


    private fun bindService() {
        serviceJobs += viewModelScope.launch { _service.isLoggedIn.collect      { _isLoggedIn.value = it } }
        serviceJobs += viewModelScope.launch {
            _service.profile
                .filterNotNull()
                .collect { profile ->
                    _profile.value = profile
                    saveProfileCache(_serviceType.value, profile)
                }
        }
        serviceJobs += viewModelScope.launch { _service.animeList.collect       { _animeList.value = it } }
        serviceJobs += viewModelScope.launch { _service.currentMedia.collect    { _currentMedia.value = it } }
        serviceJobs += viewModelScope.launch {
            _service.trending.collect { items ->
                _trending.value = items
                launch { preloadCarouselAssets(items) }
            }
        }
        serviceJobs += viewModelScope.launch { _service.popular.collect         { _popular.value = it } }
        serviceJobs += viewModelScope.launch { _service.upcoming.collect        { _upcoming.value = it } }
        serviceJobs += viewModelScope.launch { _service.recentlyUpdated.collect { _recentlyUpdated.value = it } }
        val simkl = _service as? SimklService
        if (simkl != null) {
            serviceJobs += viewModelScope.launch { simkl.trendingMovies.collect { _trendingMovies.value = it } }
            serviceJobs += viewModelScope.launch { simkl.trendingShows.collect  { _trendingShows.value = it } }
        } else {
            _trendingMovies.value = emptyList()
            _trendingShows.value  = emptyList()
        }
        val mal     = _service as? MalService
        val anilist = _service as? AnilistService
        when {
            mal != null -> {
                serviceJobs += viewModelScope.launch { mal.seasonal.collect    { _seasonal.value = it } }
                serviceJobs += viewModelScope.launch { mal.seasonLabel.collect { _seasonLabel.value = it } }
            }
            anilist != null -> {
                serviceJobs += viewModelScope.launch { anilist.seasonal.collect    { _seasonal.value = it } }
                serviceJobs += viewModelScope.launch { anilist.seasonLabel.collect { _seasonLabel.value = it } }
            }
            else -> {
                _seasonal.value = emptyList()
                _seasonLabel.value = ""
            }
        }
    }

    /** Steps the Seasonal row to an older (-1) or newer (+1) season (AniList or MAL). */
    fun seasonShift(delta: Int) = viewModelScope.launch {
        runCatching { (_service as? MalService)?.shiftSeason(delta) }
        runCatching { (_service as? AnilistService)?.shiftSeason(delta) }
    }

    // ── Tracking Automation ────────────────────────────────────────────────────
    enum class TrackingMode { ALWAYS_ASK, ALWAYS_AUTO, NEVER_AUTO }

    private val _trackingMode = MutableStateFlow(
        TrackingMode.valueOf(prefs.getString("tracking_mode", TrackingMode.ALWAYS_ASK.name) ?: TrackingMode.ALWAYS_ASK.name)
    )
    val trackingMode: StateFlow<TrackingMode> = _trackingMode.asStateFlow()

    fun setTrackingMode(mode: TrackingMode) {
        _trackingMode.value = mode
        prefs.edit { putString("tracking_mode", mode.name) }
    }

    // ── Incognito (global, temporary override — forces tracking off, mutates no settings) ────────
    private val _incognito = MutableStateFlow(prefs.getBoolean("incognito", false))
    val incognito: StateFlow<Boolean> = _incognito.asStateFlow()
    fun setIncognito(v: Boolean) { _incognito.value = v; prefs.edit { putBoolean("incognito", v) } }

    // ── Extra tracking options ───────────────────────────────────────────────────
    private val _autoCompleteTracking = MutableStateFlow(prefs.getBoolean("track_auto_complete", false))
    private val _askOncePerSeries     = MutableStateFlow(prefs.getBoolean("track_ask_once",      false))
    private val _excludedTrackingExts = MutableStateFlow(
        prefs.getString("excluded_tracking_exts", "")?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    )
    val autoCompleteTracking: StateFlow<Boolean>     = _autoCompleteTracking.asStateFlow()
    val askOncePerSeries:     StateFlow<Boolean>     = _askOncePerSeries.asStateFlow()
    val excludedTrackingExts: StateFlow<Set<String>> = _excludedTrackingExts.asStateFlow()

    fun setAutoCompleteTracking(v: Boolean) { _autoCompleteTracking.value = v; prefs.edit { putBoolean("track_auto_complete", v) } }
    fun setAskOncePerSeries(v: Boolean)     { _askOncePerSeries.value     = v; prefs.edit { putBoolean("track_ask_once",      v) } }
    fun setExtensionTrackingExcluded(pkg: String, excluded: Boolean) {
        val updated = if (excluded) _excludedTrackingExts.value + pkg else _excludedTrackingExts.value - pkg
        _excludedTrackingExts.value = updated
        prefs.edit { putString("excluded_tracking_exts", updated.joinToString("\n")) }
    }

    // Remembered "Always ask" answer per series (ids are newline-joined so extension ids are safe).
    private fun consentSet(key: String): Set<String> =
        prefs.getString(key, "")?.split("\n")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
    fun seriesTrackingConsent(mediaId: String): Boolean? = when (mediaId) {
        in consentSet("tracking_consent_yes") -> true
        in consentSet("tracking_consent_no")  -> false
        else                                  -> null
    }
    fun rememberSeriesConsent(mediaId: String, granted: Boolean) {
        val yes = consentSet("tracking_consent_yes").toMutableSet()
        val no  = consentSet("tracking_consent_no").toMutableSet()
        if (granted) { yes += mediaId; no -= mediaId } else { no += mediaId; yes -= mediaId }
        prefs.edit {
            putString("tracking_consent_yes", yes.joinToString("\n"))
            putString("tracking_consent_no",  no.joinToString("\n"))
        }
    }

    /** Forget every remembered "Always ask" choice, so each series prompts again. */
    fun clearSeriesConsents() = prefs.edit {
        remove("tracking_consent_yes")
        remove("tracking_consent_no")
    }

    // ── Sync Tracking ──────────────────────────────────────────────────────────
    private val _syncMalWithAnilist = MutableStateFlow(prefs.getBoolean("sync_mal_anilist", false))
    val syncMalWithAnilist: StateFlow<Boolean> = _syncMalWithAnilist.asStateFlow()

    fun setSyncMalWithAnilist(v: Boolean) {
        _syncMalWithAnilist.value = v
        prefs.edit { putBoolean("sync_mal_anilist", v) }
    }

    // ── Homescreen Lists ───────────────────────────────────────────────────────
    private val _anilistShowContinue = MutableStateFlow(prefs.getBoolean("anilist_show_continue", true))
    private val _anilistShowPlanned  = MutableStateFlow(prefs.getBoolean("anilist_show_planned",  false))
    private val _malShowContinue     = MutableStateFlow(prefs.getBoolean("mal_show_continue",     true))
    private val _malShowPlanned      = MutableStateFlow(prefs.getBoolean("mal_show_planned",      false))
    private val _simklShowContMovies = MutableStateFlow(prefs.getBoolean("simkl_cont_movies",     true))
    private val _simklShowPlanMovies = MutableStateFlow(prefs.getBoolean("simkl_plan_movies",     false))
    private val _simklShowContSeries = MutableStateFlow(prefs.getBoolean("simkl_cont_series",     true))
    private val _simklShowPlanSeries = MutableStateFlow(prefs.getBoolean("simkl_plan_series",     false))

    val anilistShowContinue: StateFlow<Boolean> = _anilistShowContinue.asStateFlow()
    val anilistShowPlanned:  StateFlow<Boolean> = _anilistShowPlanned.asStateFlow()
    val malShowContinue:     StateFlow<Boolean> = _malShowContinue.asStateFlow()
    val malShowPlanned:      StateFlow<Boolean> = _malShowPlanned.asStateFlow()
    val simklShowContMovies: StateFlow<Boolean> = _simklShowContMovies.asStateFlow()
    val simklShowPlanMovies: StateFlow<Boolean> = _simklShowPlanMovies.asStateFlow()
    val simklShowContSeries: StateFlow<Boolean> = _simklShowContSeries.asStateFlow()
    val simklShowPlanSeries: StateFlow<Boolean> = _simklShowPlanSeries.asStateFlow()

    fun setAnilistShowContinue(v: Boolean) { _anilistShowContinue.value = v; prefs.edit { putBoolean("anilist_show_continue", v) } }
    fun setAnilistShowPlanned(v: Boolean)  { _anilistShowPlanned.value  = v; prefs.edit { putBoolean("anilist_show_planned",  v) } }
    fun setMalShowContinue(v: Boolean)     { _malShowContinue.value     = v; prefs.edit { putBoolean("mal_show_continue",     v) } }
    fun setMalShowPlanned(v: Boolean)      { _malShowPlanned.value      = v; prefs.edit { putBoolean("mal_show_planned",      v) } }
    fun setSimklShowContMovies(v: Boolean) { _simklShowContMovies.value = v; prefs.edit { putBoolean("simkl_cont_movies",     v) } }
    fun setSimklShowPlanMovies(v: Boolean) { _simklShowPlanMovies.value = v; prefs.edit { putBoolean("simkl_plan_movies",     v) } }
    fun setSimklShowContSeries(v: Boolean) { _simklShowContSeries.value = v; prefs.edit { putBoolean("simkl_cont_series",     v) } }
    fun setSimklShowPlanSeries(v: Boolean) { _simklShowPlanSeries.value = v; prefs.edit { putBoolean("simkl_plan_series",     v) } }

    // Discovery rows (Trending / Popular / Seasonal). Default on to preserve existing behaviour.
    private val _anilistShowTrending = MutableStateFlow(prefs.getBoolean("anilist_show_trending", true))
    private val _anilistShowPopular  = MutableStateFlow(prefs.getBoolean("anilist_show_popular",  true))
    private val _malShowTrending     = MutableStateFlow(prefs.getBoolean("mal_show_trending",     true))
    private val _malShowPopular      = MutableStateFlow(prefs.getBoolean("mal_show_popular",      true))
    private val _malShowSeasonal     = MutableStateFlow(prefs.getBoolean("mal_show_seasonal",     true))
    private val _malShowUpcoming     = MutableStateFlow(prefs.getBoolean("mal_show_upcoming",     false))

    val anilistShowTrending: StateFlow<Boolean> = _anilistShowTrending.asStateFlow()
    val anilistShowPopular:  StateFlow<Boolean> = _anilistShowPopular.asStateFlow()
    val malShowTrending:     StateFlow<Boolean> = _malShowTrending.asStateFlow()
    val malShowPopular:      StateFlow<Boolean> = _malShowPopular.asStateFlow()
    val malShowSeasonal:     StateFlow<Boolean> = _malShowSeasonal.asStateFlow()
    val malShowUpcoming:     StateFlow<Boolean> = _malShowUpcoming.asStateFlow()

    fun setAnilistShowTrending(v: Boolean) { _anilistShowTrending.value = v; prefs.edit { putBoolean("anilist_show_trending", v) } }
    fun setAnilistShowPopular(v: Boolean)  { _anilistShowPopular.value  = v; prefs.edit { putBoolean("anilist_show_popular",  v) } }
    fun setMalShowTrending(v: Boolean)     { _malShowTrending.value     = v; prefs.edit { putBoolean("mal_show_trending",     v) } }
    fun setMalShowPopular(v: Boolean)      { _malShowPopular.value      = v; prefs.edit { putBoolean("mal_show_popular",      v) } }
    fun setMalShowSeasonal(v: Boolean)     { _malShowSeasonal.value     = v; prefs.edit { putBoolean("mal_show_seasonal",     v) } }
    fun setMalShowUpcoming(v: Boolean)     { _malShowUpcoming.value     = v; prefs.edit { putBoolean("mal_show_upcoming",     v) } }

    // Local "Continue Watching" row + AniList "Upcoming" row (toggles for the new sections).
    private val _anilistShowLocalContinue = MutableStateFlow(prefs.getBoolean("anilist_show_local_continue", true))
    private val _malShowLocalContinue     = MutableStateFlow(prefs.getBoolean("mal_show_local_continue",     true))
    private val _anilistShowUpcoming      = MutableStateFlow(prefs.getBoolean("anilist_show_upcoming",       false))
    val anilistShowLocalContinue: StateFlow<Boolean> = _anilistShowLocalContinue.asStateFlow()
    val malShowLocalContinue:     StateFlow<Boolean> = _malShowLocalContinue.asStateFlow()
    val anilistShowUpcoming:      StateFlow<Boolean> = _anilistShowUpcoming.asStateFlow()
    fun setAnilistShowLocalContinue(v: Boolean) { _anilistShowLocalContinue.value = v; prefs.edit { putBoolean("anilist_show_local_continue", v) } }
    fun setMalShowLocalContinue(v: Boolean)     { _malShowLocalContinue.value     = v; prefs.edit { putBoolean("mal_show_local_continue",     v) } }
    fun setAnilistShowUpcoming(v: Boolean)      { _anilistShowUpcoming.value      = v; prefs.edit { putBoolean("anilist_show_upcoming",       v) } }

    // AniList "Airing This Week" + "Seasonal" rows (new sections, default off).
    private val _anilistShowAiring   = MutableStateFlow(prefs.getBoolean("anilist_show_airing",   false))
    private val _anilistShowSeasonal = MutableStateFlow(prefs.getBoolean("anilist_show_seasonal", false))
    val anilistShowAiring:   StateFlow<Boolean> = _anilistShowAiring.asStateFlow()
    val anilistShowSeasonal: StateFlow<Boolean> = _anilistShowSeasonal.asStateFlow()
    fun setAnilistShowAiring(v: Boolean)   { _anilistShowAiring.value   = v; prefs.edit { putBoolean("anilist_show_airing",   v) } }
    fun setAnilistShowSeasonal(v: Boolean) { _anilistShowSeasonal.value = v; prefs.edit { putBoolean("anilist_show_seasonal", v) } }

    // ── Card airing-status badges ────────────────────────────────────────────────
    private val allCardStatusKeys = setOf("airing", "finished", "not_yet", "cancelled", "hiatus")
    private val _showCardStatus   = MutableStateFlow(prefs.getBoolean("show_card_status", true))
    private val _cardStatusStates = MutableStateFlow(
        prefs.getString("card_status_states", null)
            ?.split(",")?.map { it.trim() }?.filter { it in allCardStatusKeys }?.toSet()
            ?: allCardStatusKeys
    )
    val showCardStatus:   StateFlow<Boolean>     = _showCardStatus.asStateFlow()
    val cardStatusStates: StateFlow<Set<String>> = _cardStatusStates.asStateFlow()

    fun setShowCardStatus(v: Boolean) {
        _showCardStatus.value = v
        prefs.edit { putBoolean("show_card_status", v) }
    }

    fun setCardStatusState(key: String, on: Boolean) {
        val updated = _cardStatusStates.value.toMutableSet().apply { if (on) add(key) else remove(key) }
        _cardStatusStates.value = updated
        prefs.edit { putString("card_status_states", updated.joinToString(",")) }
    }

    // Order of the MAL home rows, top to bottom. Saved keys are sanitised against the known set
    // and any missing keys are appended, so adding new sections later stays forward-compatible.
    private fun loadHomeOrder(prefKey: String, default: List<String>): List<String> =
        prefs.getString(prefKey, null)
            ?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }
            ?.let { saved -> saved.filter { it in default } + default.filter { it !in saved } }
            ?.takeIf { it.isNotEmpty() }
            ?: default

    private fun moveSection(flow: MutableStateFlow<List<String>>, prefKey: String, key: String, up: Boolean) {
        val list = flow.value.toMutableList()
        val i = list.indexOf(key)
        if (i < 0) return
        val j = if (up) i - 1 else i + 1
        if (j !in list.indices) return
        val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        flow.value = list
        prefs.edit { putString(prefKey, list.joinToString(",")) }
    }

    private val defaultMalHomeOrder = listOf("local_continue", "continue", "planned", "trending", "popular", "seasonal", "upcoming")
    private val _malHomeOrder = MutableStateFlow(loadHomeOrder("mal_home_order", defaultMalHomeOrder))
    val malHomeOrder: StateFlow<List<String>> = _malHomeOrder.asStateFlow()
    fun moveMalSection(key: String, up: Boolean) = moveSection(_malHomeOrder, "mal_home_order", key, up)

    private val defaultAnilistHomeOrder = listOf("local_continue", "continue", "planned", "airing", "trending", "popular", "seasonal", "upcoming")
    private val _anilistHomeOrder = MutableStateFlow(loadHomeOrder("anilist_home_order", defaultAnilistHomeOrder))
    val anilistHomeOrder: StateFlow<List<String>> = _anilistHomeOrder.asStateFlow()
    fun moveAnilistSection(key: String, up: Boolean) = moveSection(_anilistHomeOrder, "anilist_home_order", key, up)

    // ── MAL title language ───────────────────────────────────────────────────────
    private val _malEnglishTitles = MutableStateFlow(prefs.getBoolean("mal_english_titles", false))
    val malEnglishTitles: StateFlow<Boolean> = _malEnglishTitles.asStateFlow()

    fun setMalEnglishTitles(v: Boolean) {
        _malEnglishTitles.value = v
        prefs.edit { putBoolean("mal_english_titles", v) }
        // Titles are baked into Media at fetch time, so re-fetch home rows and the user's lists.
        loadHome()
        viewModelScope.launch { runCatching { _service.refreshUserLists() } }
    }

    // ── Actions ────────────────────────────────────────────────────────────────

    fun loadHome() = viewModelScope.launch {
        _networkState.value = NetworkState.LOADING
        refreshLocalContinue()

        repeat(3) { attempt ->
            val result = runCatching { _service.fetchHomePage() }
            if (result.isSuccess) {
                _networkState.value = NetworkState.SUCCESS
                return@launch
            }
            android.util.Log.e("AppViewModel", "loadHome attempt $attempt failed", result.exceptionOrNull())
            if (attempt < 2) delay(2_000L * (attempt + 1))
        }

        _networkState.value = NetworkState.ERROR
    }

    fun retryLoad() = viewModelScope.launch {
        runCatching { _service.autoLogin() }
            .onFailure { android.util.Log.e("AppViewModel", "autoLogin failed on retry", it) }

        runCatching { sideService?.autoLogin() }
            .onFailure {  android.util.Log.e("AppViewModel", "sideService autoLogin failed on retry", it) }

        loadHome()
    }

    fun search(query: String) = viewModelScope.launch {
        if (query.isBlank()) { _searchResults.value = emptyList(); return@launch }
        runCatching { _searchResults.value = _service.search(query) }
    }

    // ── Global search across installed extensions (opt-in) ───────────────────────

    private val aniyomi       by lazy { AniyomiExtensions(getApplication()) }
    private val extOrderStore by lazy { ExtensionOrderStore(getApplication()) }

    private val _extSearchResults = MutableStateFlow<List<Media>>(emptyList())
    val extSearchResults: StateFlow<List<Media>> = _extSearchResults.asStateFlow()
    private val _extSearchLoading = MutableStateFlow(false)
    val extSearchLoading: StateFlow<Boolean> = _extSearchLoading.asStateFlow()

    /** Whether the "search all extensions" setting is enabled. */
    fun globalSearchEnabled(): Boolean = prefs.getBoolean("global_search_extensions", false)

    private var extSearchJob: Job? = null

    /** Fan a query out to every installed extension source and collect their hits. */
    fun searchExtensions(query: String) {
        extSearchJob?.cancel()
        if (query.isBlank() || !globalSearchEnabled()) {
            _extSearchResults.value = emptyList()
            return
        }
        extSearchJob = viewModelScope.launch(Dispatchers.IO) {
            _extSearchLoading.value = true
            _extSearchResults.value = emptyList()
            val sources = extOrderStore.sort(aniyomi.installedExtensions.value)
                .flatMap { it.sources.filterIsInstance<AnimeHttpSource>() }
            val gate    = Semaphore(5)
            val results = java.util.Collections.synchronizedList(mutableListOf<Media>())
            coroutineScope {
                sources.forEach { src ->
                    launch {
                        gate.withPermit {
                            val page = runCatching {
                                withTimeoutOrNull(8_000L) { src.getSearchAnime(1, query, src.getFilterList()) }
                            }.getOrNull()
                            page?.animes?.take(10)?.forEach { anime ->
                                val media = Media(
                                    id          = "$EXTERNAL_MEDIA_PREFIX${src.id}:${anime.url}",
                                    title       = anime.title,
                                    poster      = anime.thumbnail_url,
                                    serviceType = ServiceType.ANILIST,
                                )
                                registerExternalMedia(media)
                                results.add(media)
                            }
                        }
                    }
                }
            }
            _extSearchResults.value = results.toList().distinctBy { it.id }
            _extSearchLoading.value = false
        }
    }

    /** Resolve an extension search hit to a tracking entry by title, falling back to the raw ext id. */
    fun resolveExtToDetail(media: Media, onResolved: (String) -> Unit) {
        viewModelScope.launch {
            val id = runCatching { _service.search(media.title) }.getOrNull()?.firstOrNull()?.id
            onResolved(id ?: media.id)
        }
    }

    // ── Airing This Week ─────────────────────────────────────────────────────────

    /** A tracked, currently-airing show with its next episode and unwatched-aired count. */
    data class AiringItem(
        val id:          String,
        val title:       String,
        val poster:      String?,
        val episode:     Int,    // next (upcoming) episode number
        val airingAtSec: Long,   // epoch seconds when the next episode airs
        val newEpisodes: Int,    // aired but not yet watched
    )

    /** Tracked shows still airing, soonest next-episode first. Strongest for AniList (others lack data). */
    val airingThisWeek: StateFlow<List<AiringItem>> = animeList
        .map { list ->
            val active = setOf("CURRENT", "WATCHING", "REPEATING")
            list.mapNotNull { tm ->
                val next = tm.nextAiringEpisode ?: return@mapNotNull null
                if (tm.watchingStatus != null && tm.watchingStatus !in active) return@mapNotNull null
                val latestAired = (next.episode - 1).coerceAtLeast(0)
                val watched     = tm.episodeCount ?: 0
                AiringItem(
                    id          = tm.id,
                    title       = tm.title,
                    poster      = tm.poster,
                    episode     = next.episode,
                    airingAtSec = next.airingAt,
                    newEpisodes = (latestAired - watched).coerceAtLeast(0),
                )
            }.sortedBy { it.airingAtSec }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Media opened straight from an extension catalog (no AniList/MAL entry); watchable, not tracked. */
    private val externalMedia = mutableMapOf<String, Media>()

    fun registerExternalMedia(media: Media) { externalMedia[media.id] = media }

    suspend fun fetchDetails(id: String): Media {
        externalMedia[id]?.let { return it }
        // Extension-only anime (e.g. opened cold from the TV "Play Next" row) must never hit a
        // tracking API — recover its title/poster from the local watch index instead.
        if (id.isExternalMediaId()) {
            historyIndex.list().firstOrNull { it.id == id }?.let { e ->
                return Media(id = e.id, title = e.title, poster = e.poster, serviceType = ServiceType.ANILIST, idMal = e.malId)
                    .also { registerExternalMedia(it) }
            }
            return Media(id = id, title = "?")
        }
        return runCatching { _service.fetchDetails(id) }.getOrElse { Media(id = id, title = "?") }
    }

    // OAuth authorize URL to display in the in-app WebView (null = no login in progress).
    private val _authUrl = MutableStateFlow<String?>(null)
    val authUrl: StateFlow<String?> = _authUrl

    fun login() { _authUrl.value = _service.authUrl() }

    /** Called by the auth WebView once the login flow ends (success or cancel). */
    fun onAuthHandled() { _authUrl.value = null }

    fun logout() = viewModelScope.launch { _service.logout() }

    /**
     * Starts a login while forcing a signed-out state first: the in-app WebView (MAL/Simkl) keeps
     * its session in the shared cookie jar, so a plain login would silently reuse the same account.
     * This clears that session so the user can sign into a different account.
     */
    fun loginWithDifferentAccount() {
        clearAuthWebViewSession()
        login()
    }

    /** Wipes the in-app WebView's cookies + DOM storage so the next OAuth login starts signed-out. */
    private fun clearAuthWebViewSession() {
        runCatching {
            val cookies = android.webkit.CookieManager.getInstance()
            cookies.removeAllCookies(null)
            cookies.flush()
            android.webkit.WebStorage.getInstance().deleteAllData()
        }
    }

    /** Debug-only quick sign-in: a dev AniList token is baked in via local.properties. */
    val devLoginAvailable: Boolean =
        com.nyantv.BuildConfig.DEBUG && com.nyantv.BuildConfig.ANILIST_DEV_TOKEN.isNotBlank()

    /** Sign in instantly with the local.properties dev token (no QR, no Cloudflare). */
    fun devSignInAnilist() {
        if (devLoginAvailable) applyPairedAnilistToken(com.nyantv.BuildConfig.ANILIST_DEV_TOKEN)
    }

    /** Apply an AniList access token obtained via the device-pairing flow (switches to AniList). */
    fun applyPairedAnilistToken(token: String) = viewModelScope.launch {
        if (_serviceType.value != ServiceType.ANILIST) switchService(ServiceType.ANILIST)
        (_service as? AnilistService)?.applyAccessToken(token)
    }

    /**
     * Finish a paired AniList login by exchanging the auth code on-device (switches to AniList).
     * Returns true on success. The exchange runs through the Cloudflare-aware client; see
     * [AnilistService.exchangePairedCode].
     */
    suspend fun exchangePairedAnilistCode(code: String, redirectUri: String): Boolean {
        if (_serviceType.value != ServiceType.ANILIST) switchService(ServiceType.ANILIST)
        val svc = (_service as? AnilistService) ?: (sideService as? AnilistService) ?: return false
        return svc.exchangePairedCode(code, redirectUri)
    }

    fun updateEntry(id: String, status: String?, progress: Int?, score: Float?) =
        viewModelScope.launch {
            // Extension-only anime have no tracking entry; skip remote writes (watch
            // progress is still kept locally via the player's watch-history store).
            if (id.isExternalMediaId()) return@launch
            _service.updateEntry(id, status, progress, score)

            _animeList.update { list ->
                val updated = list.firstOrNull { it.id == id } ?: return@update list
                listOf(updated) + list.filter { it.id != id }
            }

            if (_syncMalWithAnilist.value) {
                val sm = syncManager ?: return@launch
                when (_serviceType.value) {
                    ServiceType.ANILIST -> sm.syncFromAnilist(id, status, progress, score)
                    ServiceType.MAL     -> sm.syncFromMal(id, status, progress, score)
                    ServiceType.SIMKL   -> { }
                }
            }
        }


    fun deleteEntry(id: String) = viewModelScope.launch {
        if (id.isExternalMediaId()) return@launch
        _service.deleteEntry(id)
        if (_syncMalWithAnilist.value) {
            val sm = syncManager ?: return@launch
            when (_serviceType.value) {
                ServiceType.ANILIST -> sm.syncDeleteFromAnilist(id)
                ServiceType.MAL     -> sm.syncDeleteFromMal(id)
                ServiceType.SIMKL   -> { }
            }
        }
    }

    fun markEpisodeWatched(mediaId: String, episodeNumber: Int) {
        // Auto-complete: if enabled and this is the final episode of a known total, set Completed.
        val total = _animeList.value.firstOrNull { it.id == mediaId }?.totalEpisodes
        val completed = _autoCompleteTracking.value && total != null && total > 0 && episodeNumber >= total
        updateEntry(
            id       = mediaId,
            status   = if (completed) "COMPLETED" else "CURRENT",
            progress = episodeNumber,
            score    = null,
        )
    }

    fun setCurrentMedia(id: String) = _service.setCurrentMedia(id)

    fun handleAuthCallback(code: String) = viewModelScope.launch {
        val target: MediaService? = when {
            _service    is AnilistService -> _service
            _service    is MalService     -> _service
            _service    is SimklService   -> _service
            sideService is AnilistService -> sideService
            sideService is MalService     -> sideService
            else                          -> null
        }
        when (target) {
            is AnilistService -> target.handleAuthCallback(code)
            is MalService     -> target.handleAuthCallback(code)
            is SimklService   -> target.handleAuthCallback(code)
        }
    }

    // Filtered list helpers
    //fun listByStatus(status: String) = animeList.value.filter {
    //    it.watchingStatus?.equals(status, ignoreCase = true) == true
    //}

    private fun buildSideService(active: ServiceType, app: Application): MediaService? =
        when (active) {
            ServiceType.ANILIST -> buildService(ServiceType.MAL,     app)
            ServiceType.MAL     -> buildService(ServiceType.ANILIST, app)
            ServiceType.SIMKL   -> null
        }

    private fun buildSyncManager(): SyncManager? {
        val anilist = when {
            _service    is AnilistService -> _service    as AnilistService
            sideService is AnilistService -> sideService as AnilistService
            else                          -> return null
        }
        val mal = when {
            _service    is MalService -> _service    as MalService
            sideService is MalService -> sideService as MalService
            else                      -> return null
        }
        return SyncManager(anilist, mal)
    }

    private fun saveProfileCache(type: ServiceType, profile: Profile) {
        prefs.edit {
            putString("cache_${type.name}_name",   profile.name)
            putString("cache_${type.name}_avatar", profile.avatar)
        }
    }

    private fun loadProfileCache(type: ServiceType): Profile? {
        val name   = prefs.getString("cache_${type.name}_name",   null) ?: return null
        val avatar = prefs.getString("cache_${type.name}_avatar", null)
        return Profile(name = name, avatar = avatar)
    }

    private suspend fun preloadCarouselAssets(items: List<Media>) {
        coroutineScope {
            items
                .filter { !_carouselLogos.value.containsKey(it.id) }
                .map { media ->
                    async(Dispatchers.IO) {
                        val logo     = async { CarouselLogoResolver.resolve(media) }
                        val backdrop = async { CarouselLogoResolver.resolveBackdrop(media) }
                        media.id to Pair(logo.await(), backdrop.await())
                    }
                }
                .awaitAll()
                .forEach { (id, pair) ->
                    _carouselLogos.update     { it + (id to pair.first) }
                    _carouselBackdrops.update { it + (id to pair.second) }
                }
        }
    }

    suspend fun resolveAnilistBanner(media: Media): String? {
        val anilist = when {
            _service    is AnilistService -> _service    as AnilistService
            sideService is AnilistService -> sideService as AnilistService
            else                          -> return null
        }
        return anilist.resolveAnilistBanner(media)
    }

    suspend fun prefetchAnilistBanners(items: List<Media>) {
        val anilist = when {
            _service    is AnilistService -> _service    as AnilistService
            sideService is AnilistService -> sideService as AnilistService
            else                          -> return
        }
        anilist.prefetchAnilistBanners(items)
    }

    fun getAnilistBanner(media: Media): String? {
        val anilist = when {
            _service    is AnilistService -> _service    as AnilistService
            sideService is AnilistService -> sideService as AnilistService
            else                          -> return null
        }
        return anilist.getAnilistBanner(media)
    }

}
