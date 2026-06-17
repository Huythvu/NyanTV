package eu.kanade.tachiyomi.animesource.online

import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.NetworkHelper.Companion.defaultUserAgentProvider
import eu.kanade.tachiyomi.network.ProgressListener
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.newCachelessCallWithProgress
import eu.kanade.tachiyomi.util.lang.awaitSingle
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest

@Suppress("unused")
abstract class AnimeHttpSource : AnimeCatalogueSource {

    protected val network: NetworkHelper
        get() = NetworkHelper.requireInstance()

    abstract val baseUrl: String

    open val versionId = 1

    override val id by lazy { generateId(name, lang, versionId) }

    val headers: Headers by lazy { headersBuilder().build() }

    open val client: OkHttpClient
        get() = network.client

    @Suppress("MemberVisibilityCanBePrivate")
    protected fun generateId(name: String, lang: String, versionId: Int): Long {
        val key = "${name.lowercase()}/$lang/$versionId"
        val bytes = MessageDigest.getInstance("MD5").digest(key.toByteArray())
        return (0..7).map { bytes[it].toLong() and 0xff shl 8 * (7 - it) }
            .reduce(Long::or) and Long.MAX_VALUE
    }

    protected open fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", defaultUserAgentProvider())
    }

    override fun toString() = "$name (${lang.uppercase()})"

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getPopularAnime"))
    override fun fetchPopularAnime(page: Int): Observable<AnimesPage> {
        return client.newCall(popularAnimeRequest(page))
            .asObservableSuccess()
            .map { response -> popularAnimeParse(response) }
    }

    protected abstract fun popularAnimeRequest(page: Int): Request

    protected abstract fun popularAnimeParse(response: Response): AnimesPage

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getSearchAnime"))
    override fun fetchSearchAnime(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Observable<AnimesPage> {
        return Observable.defer {
            try {
                client.newCall(searchAnimeRequest(page, query, filters)).asObservableSuccess()
            } catch (e: NoClassDefFoundError) {
                throw RuntimeException(e)
            }
        }.map { response -> searchAnimeParse(response) }
    }

    protected abstract fun searchAnimeRequest(
        page: Int,
        query: String,
        filters: AnimeFilterList,
    ): Request

    protected abstract fun searchAnimeParse(response: Response): AnimesPage

    @Deprecated("Use the non-RxJava API instead", ReplaceWith("getLatestUpdates"))
    override fun fetchLatestUpdates(page: Int): Observable<AnimesPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response -> latestUpdatesParse(response) }
    }

    protected abstract fun latestUpdatesRequest(page: Int): Request

    protected abstract fun latestUpdatesParse(response: Response): AnimesPage

    @Suppress("DEPRECATION")
    override suspend fun getAnimeDetails(anime: SAnime): SAnime {
        return fetchAnimeDetails(anime).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getAnimeDetails"))
    override fun fetchAnimeDetails(anime: SAnime): Observable<SAnime> {
        return client.newCall(animeDetailsRequest(anime))
            .asObservableSuccess()
            .map { response -> animeDetailsParse(response).apply { initialized = true } }
    }

    open fun animeDetailsRequest(anime: SAnime): Request {
        return GET(baseUrl + anime.url, headers)
    }

    protected abstract fun animeDetailsParse(response: Response): SAnime

    @Suppress("DEPRECATION")
    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        if (anime.status == SAnime.LICENSED) {
            throw LicensedEntryItemsException()
        }
        return fetchEpisodeList(anime).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getEpisodeList"))
    override fun fetchEpisodeList(anime: SAnime): Observable<List<SEpisode>> {
        return if (anime.status != SAnime.LICENSED) {
            client.newCall(episodeListRequest(anime))
                .asObservableSuccess()
                .map { response -> episodeListParse(response) }
        } else {
            Observable.error(LicensedEntryItemsException())
        }
    }

    protected open fun episodeListRequest(anime: SAnime): Request {
        return GET(baseUrl + anime.url, headers)
    }

    protected abstract fun episodeListParse(response: Response): List<SEpisode>

    protected abstract fun episodeVideoParse(response: Response): SEpisode

    @Suppress("DEPRECATION")
    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return try {
            getHosterList(episode).flatMap { hoster -> getVideoList(hoster) }
        } catch (e: IllegalStateException) {
            fetchVideoList(episode).awaitSingle()
        }
    }

    private fun supportsHosterList(): Boolean {
        return try {
            this::class.java.getMethod(
                "getHosterList",
                SEpisode::class.java,
                kotlin.coroutines.Continuation::class.java,
            ).declaringClass != AnimeSource::class.java
        } catch (e: NoSuchMethodException) {
            false
        }
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getVideoList"))
    override fun fetchVideoList(episode: SEpisode): Observable<List<Video>> {
        return client.newCall(videoListRequest(episode))
            .asObservableSuccess()
            .map { response -> videoListParse(response).sort() }
    }

    protected open fun videoListRequest(episode: SEpisode): Request {
        return GET(baseUrl + episode.url, headers)
    }

    protected abstract fun videoListParse(response: Response): List<Video>

    protected open fun List<Video>.sort(): List<Video> = this

    @Suppress("DEPRECATION")
    open suspend fun getVideoUrl(video: Video): String {
        return fetchVideoUrl(video).awaitSingle()
    }

    @Deprecated("Use the non-RxJava API instead", replaceWith = ReplaceWith("getVideoUrl"))
    open fun fetchVideoUrl(video: Video): Observable<String> {
        return client.newCall(videoUrlRequest(video))
            .asObservableSuccess()
            .map { videoUrlParse(it) }
    }

    protected open fun videoUrlRequest(video: Video): Request {
        return GET(video.url, headers)
    }

    protected abstract fun videoUrlParse(response: Response): String

    suspend fun getVideo(request: Request, listener: ProgressListener): Response {
        return client.newCachelessCallWithProgress(request, listener).awaitSuccess()
    }

    fun getVideoSize(video: Video, tries: Int): Long {
        val hdrs = Headers.Builder().addAll(video.headers ?: headers).add("Range", "bytes=0-1").build()
        val request = GET(video.videoUrl!!, hdrs)
        val response = client.newCall(request).execute()
        val contentRange = response.header("Content-Range")
        if (contentRange != null) return contentRange.split("/")[1].toLong()
        if (tries > 0) return getVideoSize(video, tries - 1)
        return -1L
    }

    fun videoRequest(video: Video, start: Long, end: Long): Request {
        val hdrs = video.headers ?: headers
        val newHeaders = if (end - start > 0L) {
            Headers.Builder().addAll(hdrs).add("Range", "bytes=$start-$end").build()
        } else if (start >= 0L) {
            Headers.Builder().addAll(hdrs).add("Range", "bytes=$start-").build()
        } else null
        return GET(video.videoUrl!!, newHeaders ?: hdrs)
    }

    fun safeVideoRequest(video: Video): Request {
        return GET(video.videoUrl!!, video.headers ?: headers)
    }

    fun SEpisode.setUrlWithoutDomain(url: String) { this.url = getUrlWithoutDomain(url) }

    fun SAnime.setUrlWithoutDomain(url: String) { this.url = getUrlWithoutDomain(url) }

    private fun getUrlWithoutDomain(orig: String): String {
        return try {
            val uri = URI(orig)
            var out = uri.path
            if (uri.query != null) out += "?" + uri.query
            if (uri.fragment != null) out += "#" + uri.fragment
            out
        } catch (e: URISyntaxException) { orig }
    }

    open fun getAnimeUrl(anime: SAnime): String = animeDetailsRequest(anime).url.toString()

    open fun getEpisodeUrl(episode: SEpisode): String = episode.url

    open fun prepareNewEpisode(episode: SEpisode, anime: SAnime) {}

    override fun getFilterList() = AnimeFilterList()
}

class LicensedEntryItemsException : RuntimeException()