package com.nyantv.extensions

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.util.lang.awaitSingle
import kotlinx.coroutines.flow.StateFlow
import androidx.core.net.toUri
import com.nyantv.NyanTVApp
import eu.kanade.tachiyomi.extension.anime.model.AvailableAnimeSources
import eu.kanade.tachiyomi.network.await
import kotlinx.serialization.json.Json
import okhttp3.Request
import java.io.File

class AniyomiExtensions(private val context: Context) {

    val extensionManager = (context.applicationContext as NyanTVApp).extensionManager

    val installedExtensions: StateFlow<List<AnimeExtension.Installed>>
        get() = extensionManager.installedExtensionsFlow

    val untrustedExtensions: StateFlow<List<AnimeExtension.Untrusted>>
        get() = extensionManager.untrustedExtensionsFlow

    fun getSource(sourceId: Long): AnimeSource? {
        return installedExtensions.value
            .flatMap { it.sources }
            .find { it.id == sourceId }
    }

    suspend fun getAnimeList(source: AnimeHttpSource, page: Int = 1): AnimesPage {
        return source.fetchPopularAnime(page).awaitSingle()
    }

    suspend fun searchAnime(
        source: AnimeHttpSource,
        query: String,
        page: Int = 1,
    ): AnimesPage {
        return source.fetchSearchAnime(page, query, source.getFilterList()).awaitSingle()
    }

    suspend fun getAnimeDetails(source: AnimeHttpSource, anime: SAnime): SAnime {
        return source.fetchAnimeDetails(anime).awaitSingle()
    }

    suspend fun getEpisodeList(source: AnimeHttpSource, anime: SAnime): List<SEpisode> {
        return source.fetchEpisodeList(anime).awaitSingle()
    }

    suspend fun getVideoList(source: AnimeHttpSource, episode: SEpisode): List<Video> {
        return source.fetchVideoList(episode).awaitSingle()
    }

    // Install via Android's package manager using the extension's download URL
    suspend fun installExtension(extension: AnimeExtension.Available) {
        val apkUrl = "${extension.repository}/apk/${extension.apkName}"

        val response = networkHelper.client
            .newCall(Request.Builder().url(apkUrl).build())
            .await()

        if (!response.isSuccessful) return

        val apkFile = File(context.cacheDir, extension.apkName)
        apkFile.writeBytes(response.body.bytes())

        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    fun uninstallExtension(extension: AnimeExtension.Installed, activity: Activity) {
        Log.d("UNINSTALL", "Trying to uninstall ${extension.pkgName} via activity=$activity")
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = "package:${extension.pkgName}".toUri()
        }
        activity.startActivity(intent)
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val networkHelper = NetworkHelper(context)

    suspend fun fetchAvailableExtensions(repoUrls: List<String>): List<AnimeExtension.Available> {
        if (repoUrls.isEmpty()) return emptyList()
        val seen = mutableSetOf<String>()
        return repoUrls.flatMap { indexUrl ->
            val base = indexUrl.substringBeforeLast("/")
            runCatching {
                val response = networkHelper.client
                    .newCall(Request.Builder().url(indexUrl).build())
                    .await()
                if (!response.isSuccessful) return@runCatching emptyList<AnimeExtension.Available>()
                val entries = json.decodeFromString<List<ExtensionIndexEntry>>(response.body.string())
                entries.map { entry ->
                    AnimeExtension.Available(
                        name         = entry.name,
                        pkgName      = entry.pkg,
                        versionName  = entry.version,
                        versionCode  = entry.code,
                        libVersion   = entry.version.substringBeforeLast('.').toDoubleOrNull() ?: 14.0,
                        lang         = entry.lang,
                        isNsfw       = entry.nsfw == 1,
                        hasReadme    = false,
                        hasChangelog = false,
                        apkName      = entry.apk,
                        iconUrl      = "$base/icon/${entry.pkg}.png",
                        repository   = base,
                        sources      = entry.sources.map { src ->
                            AvailableAnimeSources(
                                id      = src.id.toLong(),
                                lang    = src.lang,
                                name    = src.name,
                                baseUrl = src.baseUrl,
                            )
                        },
                    )
                }
            }.getOrElse { emptyList() }
        }.filter { seen.add(it.pkgName) }
    }

}