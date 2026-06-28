package com.nyantv.data

import android.content.Context
import java.security.SecureRandom
import android.util.Base64
import com.nyantv.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import androidx.core.content.edit
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

private const val MAL_API  = "https://api.myanimelist.net/v2"
private const val MAL_AUTH = "https://myanimelist.net/v1/oauth2"
private const val MAL_PREFS = "mal_prefs"

class MalService(context: Context) : MediaService {

    override val serviceType = ServiceType.MAL

    private val http  = OkHttpClient()
    private val json  = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val prefs = context.getSharedPreferences(MAL_PREFS, Context.MODE_PRIVATE)
    // App-wide prefs, written by the settings UI; read here so fetched titles honour the choice.
    private val appPrefs = context.getSharedPreferences("nyantv_prefs", Context.MODE_PRIVATE)
    private fun preferEnglishTitles() = appPrefs.getBoolean("mal_english_titles", false)

    private var accessToken:  String? = prefs.getString("access_token",  null)
    private var refreshToken: String? = prefs.getString("refresh_token", null)

    private val _isLoggedIn      = MutableStateFlow(accessToken != null)
    private val _profile         = MutableStateFlow<Profile?>(null)
    private val _animeList       = MutableStateFlow<List<TrackedMedia>>(emptyList())
    private val _currentMedia    = MutableStateFlow<TrackedMedia?>(null)
    private val _trending        = MutableStateFlow<List<Media>>(emptyList())
    private val _popular         = MutableStateFlow<List<Media>>(emptyList())
    private val _upcoming        = MutableStateFlow<List<Media>>(emptyList())
    private val _recentlyUpdated = MutableStateFlow<List<Media>>(emptyList())
    private val _seasonal        = MutableStateFlow<List<Media>>(emptyList())
    private val _seasonLabel     = MutableStateFlow("")
    private var selSeasonYear    = 0
    private var selSeasonName    = ""

    override val isLoggedIn:      StateFlow<Boolean>            = _isLoggedIn.asStateFlow()
    override val profile:         StateFlow<Profile?>           = _profile.asStateFlow()
    override val animeList:       StateFlow<List<TrackedMedia>> = _animeList.asStateFlow()
    override val currentMedia:    StateFlow<TrackedMedia?>      = _currentMedia.asStateFlow()
    override val trending:        StateFlow<List<Media>>        = _trending.asStateFlow()
    override val popular:         StateFlow<List<Media>>        = _popular.asStateFlow()
    override val upcoming:        StateFlow<List<Media>>        = _upcoming.asStateFlow()
    override val recentlyUpdated: StateFlow<List<Media>>        = _recentlyUpdated.asStateFlow()
    /** MAL-specific extra row: current-season anime. */
    val seasonal:                 StateFlow<List<Media>>        = _seasonal.asStateFlow()
    /** Display label for the selected season, e.g. "Summer 2026". */
    val seasonLabel:              StateFlow<String>             = _seasonLabel.asStateFlow()

    // ── Auth ───────────────────────────────────────────────────────────────────

    override fun authUrl(): String {
        val clientId = BuildConfig.MAL_CLIENT_ID

        val bytes = ByteArray(96).also { SecureRandom().nextBytes(it) }
        val verifier = Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

        prefs.edit { putString("code_verifier", verifier) }

        // Send redirect_uri explicitly: once a second redirect URL (the pairing relay) is registered
        // on the MAL app, MAL can no longer assume which one to use, so the request must name it.
        return "$MAL_AUTH/authorize" +
                "?response_type=code" +
                "&client_id=$clientId" +
                "&redirect_uri=${enc(BuildConfig.REDIRECT_URI)}" +
                "&code_challenge=$verifier" +
                "&code_challenge_method=plain"
    }

    suspend fun handleAuthCallback(code: String) = withContext(Dispatchers.IO) {
        val verifier = prefs.getString("code_verifier", null)
        android.util.Log.d("MalService", "handleAuthCallback: code=$code, verifier=${verifier?.take(10)}...")
        if (verifier == null) {
            android.util.Log.e("MalService", "code_verifier is null!")
            return@withContext
        }

        val body = "grant_type=authorization_code" +
                "&client_id=${BuildConfig.MAL_CLIENT_ID}" +
                "&client_secret=${BuildConfig.MAL_CLIENT_SECRET}" +
                "&code=$code" +
                "&code_verifier=$verifier" +
                "&redirect_uri=${enc(BuildConfig.REDIRECT_URI)}"

        val tokens = postForm("$MAL_AUTH/token", body) ?: return@withContext

        prefs.edit { remove("code_verifier") }

        saveTokens(
            tokens["access_token"]!!.jsonPrimitive.content,
            tokens["refresh_token"]?.jsonPrimitive?.contentOrNull
        )
        fetchUserProfile()
        refreshUserLists()
    }

    /**
     * Finish a QR/phone-paired MAL login. The relay handed back the auth [code], the [redirectUri]
     * it was issued for, and the PKCE [verifier] it minted; we exchange them for a token on-device,
     * so the MAL client secret (from local.properties) never has to touch the relay.
     */
    suspend fun exchangePairedCode(code: String, verifier: String, redirectUri: String): Boolean =
        withContext(Dispatchers.IO) {
            val body = "grant_type=authorization_code" +
                    "&client_id=${BuildConfig.MAL_CLIENT_ID}" +
                    "&client_secret=${BuildConfig.MAL_CLIENT_SECRET}" +
                    "&code=$code" +
                    "&code_verifier=$verifier" +
                    "&redirect_uri=${enc(redirectUri)}"
            val tokens = postForm("$MAL_AUTH/token", body) ?: return@withContext false
            val access = tokens["access_token"]?.jsonPrimitive?.contentOrNull ?: return@withContext false
            saveTokens(access, tokens["refresh_token"]?.jsonPrimitive?.contentOrNull)
            fetchUserProfile()
            refreshUserLists()
            true
        }

    private suspend fun refreshAccessToken() = withContext(Dispatchers.IO) {
        val rt = refreshToken ?: return@withContext
        val body = "grant_type=refresh_token" +
                "&client_id=${BuildConfig.MAL_CLIENT_ID}" +
                "&client_secret=${BuildConfig.MAL_CLIENT_SECRET}" +
                "&refresh_token=$rt"
        val tokens = postForm("$MAL_AUTH/token", body) ?: return@withContext
        saveTokens(
            tokens["access_token"]!!.jsonPrimitive.content,
            tokens["refresh_token"]?.jsonPrimitive?.contentOrNull ?: rt
        )
    }

    private fun saveTokens(access: String, refresh: String?) {
        accessToken  = access
        refreshToken = refresh
        prefs.edit {
            putString("access_token", access)
                .apply { refresh?.let { putString("refresh_token", it) } }
        }
        _isLoggedIn.value = true
    }

    override suspend fun logout() {
        accessToken  = null
        refreshToken = null
        prefs.edit { remove("access_token").remove("refresh_token") }
        _isLoggedIn.value = false
        _profile.value    = null
        _animeList.value  = emptyList()
    }

    override suspend fun autoLogin() {
        if (accessToken == null) return
        withContext(Dispatchers.IO) {
            runCatching {
                coroutineScope {
                    launch { fetchUserProfile() }
                    launch { refreshUserLists() }
                }
            }.onFailure {
                android.util.Log.e("MalService", "autoLogin failed", it)
            }
        }
    }

    // ── Profile ────────────────────────────────────────────────────────────────

    private suspend fun fetchUserProfile() = withContext(Dispatchers.IO) {
        val data = get("$MAL_API/users/@me?fields=name,picture,anime_statistics") ?: return@withContext
        _profile.value = Profile(
            id     = data["id"]?.jsonPrimitive?.contentOrNull,
            name   = data["name"]?.jsonPrimitive?.contentOrNull,
            avatar = data["picture"]?.jsonPrimitive?.contentOrNull,
            animeCount      = data["anime_statistics"]?.jsonObject?.get("num_items")?.jsonPrimitive?.intOrNull,
            episodesWatched = data["anime_statistics"]?.jsonObject?.get("num_episodes")?.jsonPrimitive?.intOrNull,
            meanScore       = data["anime_statistics"]?.jsonObject?.get("mean_score")?.jsonPrimitive?.floatOrNull
        )
    }

    // ── Homepage ───────────────────────────────────────────────────────────────

    override suspend fun fetchHomePage() = withContext(Dispatchers.IO) {
        val fields = "fields=mean,status,media_type,num_episodes,main_picture,start_season,alternative_titles"
        _trending.value = fetchList("$MAL_API/anime/ranking?ranking_type=airing&limit=15&$fields")
        _popular.value  = fetchList("$MAL_API/anime/ranking?ranking_type=bypopularity&limit=15&$fields")
        _upcoming.value = fetchList("$MAL_API/anime/ranking?ranking_type=upcoming&limit=15&$fields")
        // Reset the Seasonal row to the current season on every home (re)load.
        val (y, s) = currentSeason()
        selSeasonYear = y; selSeasonName = s
        loadSeason()
    }

    private val seasonOrder = listOf("winter", "spring", "summer", "fall")

    /** Current anime season as (year, "winter"|"spring"|"summer"|"fall"). */
    private fun currentSeason(): Pair<Int, String> {
        val cal = java.util.Calendar.getInstance()
        val season = when (cal.get(java.util.Calendar.MONTH)) {  // Calendar.MONTH is 0-based
            in 0..2 -> "winter"
            in 3..5 -> "spring"
            in 6..8 -> "summer"
            else    -> "fall"
        }
        return cal.get(java.util.Calendar.YEAR) to season
    }

    private fun seasonLabelOf(year: Int, season: String) =
        season.replaceFirstChar { it.uppercase() } + " " + year

    private suspend fun loadSeason() {
        _seasonLabel.value = seasonLabelOf(selSeasonYear, selSeasonName)
        _seasonal.value    = fetchSeasonalFor(selSeasonYear, selSeasonName)
    }

    private suspend fun fetchSeasonalFor(year: Int, season: String): List<Media> = withContext(Dispatchers.IO) {
        val fields = "fields=mean,status,media_type,num_episodes,main_picture,start_season,alternative_titles"
        // The season endpoint returns everything *airing* that season, which includes
        // long-running shows that premiered years ago (One Piece, etc.). MAL's seasonal page
        // is about *premieres*, so keep only titles whose start season matches.
        val all = fetchList("$MAL_API/anime/season/$year/$season?sort=anime_num_list_users&limit=100&$fields")
        all.filter { it.season.equals(season, ignoreCase = true) && it.seasonYear == year }.take(20)
    }

    /**
     * Moves the selected season by [delta] (negative = older). Allows up to 4 seasons past the
     * current one, but only actually advances into a future season if it has entries — so the
     * user can't wander into empty, not-yet-scheduled seasons.
     */
    suspend fun shiftSeason(delta: Int) {
        var idx  = seasonOrder.indexOf(selSeasonName).coerceAtLeast(0) + delta
        var year = selSeasonYear
        while (idx > 3) { idx -= 4; year += 1 }
        while (idx < 0) { idx += 4; year -= 1 }

        // Compare seasons on an absolute chronological index (year*4 + seasonIndex).
        val (cy, cs) = currentSeason()
        val curAbs = cy * 4 + seasonOrder.indexOf(cs)
        val target = (year * 4 + idx).coerceIn(1960 * 4, curAbs + 4)  // at most 4 seasons ahead
        year = target / 4
        idx  = target % 4
        val season = seasonOrder[idx]

        val list = fetchSeasonalFor(year, season)
        // Don't advance into a future season that has no premieres yet; stay where we are.
        if (target > curAbs && list.isEmpty()) return

        selSeasonYear = year
        selSeasonName = season
        _seasonLabel.value = seasonLabelOf(year, season)
        _seasonal.value = list
    }

    private suspend fun fetchList(url: String): List<Media> = withContext(Dispatchers.IO) {
        val data = get(url) ?: return@withContext emptyList()
        val english = preferEnglishTitles()
        data["data"]?.jsonArray?.map { it.jsonObject["node"]!!.jsonObject.toMalMedia(english) } ?: emptyList()
    }

    // ── Details ────────────────────────────────────────────────────────────────

    override suspend fun fetchDetails(id: String): Media = withContext(Dispatchers.IO) {
        val fields = "fields=mean,status,media_type,synopsis,genres,num_episodes,start_date,rank,popularity,main_picture,recommendations,alternative_titles"
        runCatching {
            val data = get("$MAL_API/anime/$id?$fields") ?: return@withContext Media(id = id, title = "?")
            val english = preferEnglishTitles()
            val recommendations = data["recommendations"]?.jsonArray?.mapNotNull { rec ->
                val node = rec.jsonObject["node"]?.jsonObject ?: return@mapNotNull null
                node.toMalMedia(english)
            } ?: emptyList()
            data.toMalMedia(english).copy(recommendations = recommendations)
        }.getOrElse { Media(id = id, title = "?") }
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    override suspend fun search(query: String): List<Media> = withContext(Dispatchers.IO) {
        val fields = "fields=mean,status,num_episodes,main_picture,alternative_titles"
        val data = get("$MAL_API/anime?q=$query&limit=30&$fields") ?: return@withContext emptyList()
        val english = preferEnglishTitles()
        data["data"]?.jsonArray?.map { it.jsonObject["node"]!!.jsonObject.toMalMedia(english) } ?: emptyList()
    }

    // ── User list ──────────────────────────────────────────────────────────────

    override suspend fun refreshUserLists() = withContext(Dispatchers.IO) {
        val fields = "fields=num_episodes,mean,list_status,alternative_titles,status"
        val data = get("$MAL_API/users/@me/animelist?$fields&limit=1000&sort=list_updated_at") ?: return@withContext
        val english = preferEnglishTitles()
        _animeList.value = data["data"]?.jsonArray?.map { entry ->
            val node   = entry.jsonObject["node"]!!.jsonObject
            val status = entry.jsonObject["list_status"]!!.jsonObject
            TrackedMedia(
                id             = node["id"]?.jsonPrimitive?.contentOrNull ?: "",
                title          = node.pickTitle(english),
                poster         = node["main_picture"]?.jsonObject?.get("large")?.jsonPrimitive?.contentOrNull,
                watchingStatus = malStatusToAL(status["status"]?.jsonPrimitive?.contentOrNull),
                episodeCount   = status["num_episodes_watched"]?.jsonPrimitive?.intOrNull,
                totalEpisodes  = node["num_episodes"]?.jsonPrimitive?.intOrNull,
                score          = status["score"]?.jsonPrimitive?.floatOrNull,
                averageScore   = node["mean"]?.jsonPrimitive?.floatOrNull?.times(10)?.toInt(),
                isMovie        = null,
                status         = node["status"]?.jsonPrimitive?.contentOrNull.normalizeStatus(),
            )
        } ?: emptyList()
    }

    override fun setCurrentMedia(id: String) {
        _currentMedia.value = _animeList.value.firstOrNull { it.id == id }
    }

    override suspend fun updateEntry(id: String, status: String?, progress: Int?, score: Float?) =
        withContext(Dispatchers.IO) {
            val body = buildString {
                status?.let   { append("status=${alStatusToMal(it)}&") }
                progress?.let { append("num_watched_episodes=$it&") }
                score?.let    { append("score=${it.toInt()}&") }
            }.trimEnd('&')
            putForm("$MAL_API/anime/$id/my_list_status", body)
            refreshUserLists()
            setCurrentMedia(id)
        }

    override suspend fun deleteEntry(id: String) = withContext(Dispatchers.IO) {
        delete("$MAL_API/anime/$id/my_list_status")
        refreshUserLists()
        setCurrentMedia(id)
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────────

    private fun enc(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")

    private suspend fun get(url: String, retryOnUnauth: Boolean = true): JsonObject? =
        withContext(Dispatchers.IO) {
            val resp = http.newCall(
                Request.Builder().url(url)
                    .apply {
                        if (accessToken != null) header("Authorization", "Bearer $accessToken")
                        else header("X-MAL-CLIENT-ID", BuildConfig.MAL_CLIENT_ID)
                    }.build()
            ).execute()

            if (resp.code == 401 && retryOnUnauth) {
                refreshAccessToken()
                return@withContext get(url, retryOnUnauth = false)
            }
            if (!resp.isSuccessful) {
                android.util.Log.e("MalService", "GET $url failed: ${resp.code}")
                return@withContext null
            }
            runCatching { json.parseToJsonElement(resp.body.string()).jsonObject }.getOrNull()
        }

    private suspend fun postForm(url: String, body: String): JsonObject? = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        if (!resp.isSuccessful) {
            val errorBody = resp.body.string()
            android.util.Log.e("MalService", "postForm $url failed: ${resp.code} – $errorBody")
            return@withContext null
        }
        json.parseToJsonElement(resp.body.string()).jsonObject
    }

    private suspend fun putForm(url: String, body: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .put(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .header("Authorization", "Bearer $accessToken")
            .build()
        http.newCall(req).execute()
    }

    private suspend fun delete(url: String) = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .delete()
            .header("Authorization", "Bearer $accessToken")
            .build()
        http.newCall(req).execute()
    }

    // ── Status mapping ─────────────────────────────────────────────────────────

    private fun malStatusToAL(s: String?) = when (s) {
        "watching"      -> "CURRENT"
        "completed"     -> "COMPLETED"
        "on_hold"       -> "PAUSED"
        "dropped"       -> "DROPPED"
        "plan_to_watch" -> "PLANNING"
        else            -> "UNKNOWN"
    }

    private fun alStatusToMal(s: String) = when (s) {
        "CURRENT"   -> "watching"
        "COMPLETED" -> "completed"
        "PAUSED"    -> "on_hold"
        "DROPPED"   -> "dropped"
        "PLANNING"  -> "plan_to_watch"
        else        -> "watching"
    }
}

// ── MAL JSON → Media ───────────────────────────────────────────────────────────

/** Picks the English alternative title when [preferEnglish] and one is present, else the default. */
private fun JsonObject.pickTitle(preferEnglish: Boolean): String {
    val main = this["title"]?.jsonPrimitive?.contentOrNull ?: "?"
    if (!preferEnglish) return main
    val en = this["alternative_titles"]?.jsonObject?.get("en")?.jsonPrimitive?.contentOrNull
    return en?.takeIf { it.isNotBlank() } ?: main
}

private fun JsonObject.toMalMedia(preferEnglish: Boolean = false): Media {
    val pic = this["main_picture"]?.jsonObject
    val malId = this["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
    val startSeason = this["start_season"]?.jsonObject
    val alt = this["alternative_titles"]?.jsonObject
    val altTitles = buildList {
        this@toMalMedia["title"]?.jsonPrimitive?.contentOrNull?.let { add(it) }
        alt?.get("en")?.jsonPrimitive?.contentOrNull?.let { add(it) }
        alt?.get("ja")?.jsonPrimitive?.contentOrNull?.let { add(it) }
        alt?.get("synonyms")?.jsonArray?.forEach { s -> s.jsonPrimitive.contentOrNull?.let { add(it) } }
    }.filter { it.isNotBlank() }.distinct()
    return Media(
        id           = malId,
        title        = pickTitle(preferEnglish),
        altTitles    = altTitles,
        poster       = pic?.get("large")?.jsonPrimitive?.contentOrNull,
        description  = this["synopsis"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull,
        averageScore = this["mean"]?.jsonPrimitive?.floatOrNull?.times(10)?.toInt(),
        episodes     = this["num_episodes"]?.jsonPrimitive?.intOrNull,
        status       = this["status"]?.jsonPrimitive?.contentOrNull.normalizeStatus(),
        format       = this["media_type"]?.jsonPrimitive?.contentOrNull?.uppercase(),
        season       = startSeason?.get("season")?.jsonPrimitive?.contentOrNull?.uppercase(),
        seasonYear   = startSeason?.get("year")?.jsonPrimitive?.intOrNull,
        genres       = this["genres"]?.takeIf { it !is JsonNull }?.jsonArray
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?: emptyList(),
        serviceType  = ServiceType.MAL,
        idMal        = malId.takeIf { it.isNotBlank() }
    )
}
