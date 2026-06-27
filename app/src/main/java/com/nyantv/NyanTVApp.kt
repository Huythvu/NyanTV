package com.nyantv

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import eu.kanade.tachiyomi.extension.anime.AnimeExtensionManager
import eu.kanade.tachiyomi.network.NetworkHelper
import keiyoushi.utils.initializeApplicationContext
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.InjektModule
import uy.kohesive.injekt.api.InjektRegistrar
import uy.kohesive.injekt.api.addSingleton

class NyanTVApp : Application() {

    lateinit var extensionManager: AnimeExtensionManager
        private set

    lateinit var networkHelper: NetworkHelper
        private set

    override fun onCreate() {
        super.onCreate()
        initializeApplicationContext(this)

        // Derive the network User-Agent from the device's real WebView so it matches the
        // engine that solves Cloudflare challenges. Falls back to a default if WebView is
        // unavailable (e.g. some TV boxes).
        try {
            val webViewUa = android.webkit.WebSettings.getDefaultUserAgent(this)
            NetworkHelper.setDeviceUserAgent(webViewUa)
            android.util.Log.d("NyanExt", "Device WebView UA: $webViewUa")
            android.util.Log.d("NyanExt", "Using network UA: ${NetworkHelper.defaultUserAgentProvider()}")
        } catch (e: Throwable) {
            android.util.Log.e("NyanExt", "Could not read WebView UA; using fallback", e)
        }

        networkHelper = NetworkHelper(this)
        NetworkHelper.setInstance(networkHelper)
        extensionManager = AnimeExtensionManager(this)

        Injekt.importModule(object : InjektModule {
            override fun InjektRegistrar.registerInjectables() {
                addSingleton<Application>(this@NyanTVApp)
                addSingleton<kotlinx.serialization.json.Json>(
                    kotlinx.serialization.json.Json {
                        ignoreUnknownKeys = true
                        isLenient = true
                    }
                )
            }
        })

        // Load images through the extension network client (device User-Agent + shared cookie jar
        // incl. Cloudflare clearance), like Aniyomi does — many extension thumbnails 403 without it.
        // A same-origin Referer fallback also fixes the common hotlink-protection case.
        val imageClient = networkHelper.client.newBuilder()
            .addInterceptor { chain ->
                val req = chain.request()
                if (req.header("Referer") != null) chain.proceed(req)
                else chain.proceed(
                    req.newBuilder()
                        .header("Referer", "${req.url.scheme}://${req.url.host}/")
                        .build()
                )
            }
            .build()

        Coil.setImageLoader(
            ImageLoader.Builder(this)
                .okHttpClient(imageClient)
                .memoryCache {
                    MemoryCache.Builder(this)
                        .maxSizePercent(0.15)
                        .build()
                }
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(75L * 1024 * 1024)
                        .build()
                }
                .respectCacheHeaders(false)
                .crossfade(true)
                .build()
        )
    }
}