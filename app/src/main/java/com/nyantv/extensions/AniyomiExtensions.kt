package com.nyantv.extensions

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import eu.kanade.tachiyomi.extension.anime.model.AnimeExtension
import eu.kanade.tachiyomi.network.NetworkHelper
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

    // Install via the PackageInstaller session API using the extension's download URL.
    // The session API works on Android TV, unlike the legacy ACTION_VIEW + package-archive
    // intent which depends on an installer activity that TV builds often don't ship.
    suspend fun installExtension(extension: AnimeExtension.Available): Boolean {
        // The "install unknown apps" permission is required. If it hasn't been granted yet,
        // send the user to the system screen to grant it (works on TV too) and stop here;
        // they can tap Install again afterward.
        if (!context.packageManager.canRequestPackageInstalls()) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri(),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
            return false
        }

        val apkUrl = "${extension.repository}/apk/${extension.apkName}"

        val response = networkHelper.client
            .newCall(Request.Builder().url(apkUrl).build())
            .await()

        if (!response.isSuccessful) return false

        val apkFile = File(context.cacheDir, extension.apkName)
        apkFile.writeBytes(response.body.bytes())

        return ApkInstaller.install(context, apkFile)
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
        if (repoUrls.isEmpty()) {
            Log.d(TAG, "fetchAvailableExtensions: no repos configured")
            return emptyList()
        }
        val seen = mutableSetOf<String>()
        return repoUrls.flatMap { indexUrl ->
            val base = indexUrl.substringBeforeLast("/")
            runCatching {
                Log.d(TAG, "Fetching repo index: $indexUrl")
                val response = networkHelper.client
                    .newCall(Request.Builder().url(indexUrl).build())
                    .await()
                val bodyText = response.body.string()
                Log.d(TAG, "Repo $indexUrl -> HTTP ${response.code}, body=${bodyText.length} chars")
                if (!response.isSuccessful) {
                    Log.w(TAG, "Repo $indexUrl returned unsuccessful HTTP ${response.code}")
                    return@runCatching emptyList()
                }
                val entries = json.decodeFromString<List<ExtensionIndexEntry>>(bodyText)
                Log.d(TAG, "Repo $indexUrl parsed ${entries.size} entries")
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
                                id      = src.id,
                                lang    = src.lang,
                                name    = src.name,
                                baseUrl = src.baseUrl,
                            )
                        },
                    )
                }
            }.getOrElse { e ->
                Log.e(TAG, "Failed to fetch/parse repo $indexUrl", e)
                emptyList()
            }
        }.filter { seen.add(it.pkgName) }.also {
            Log.d(TAG, "fetchAvailableExtensions: total ${it.size} extensions across ${repoUrls.size} repo(s)")
        }
    }

    private companion object {
        const val TAG = "NyanExt"
    }

}