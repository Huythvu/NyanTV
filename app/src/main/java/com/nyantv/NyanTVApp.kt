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

        // The player runs in its own process (":player"), and Application.onCreate runs once per
        // process. WebView can't be used from two processes that share the same data directory, so
        // any WebView touch in the non-main process (e.g. reading the UA below) throws and falls
        // back to a generic UA — which then mismatches the main process's cf_clearance cookies.
        // Give each non-main process its own WebView data dir. Must run before any WebView use.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            val proc = getProcessName()
            if (proc != packageName) {
                runCatching {
                    android.webkit.WebView.setDataDirectorySuffix(proc.substringAfter(':', "alt"))
                }
            }
        }

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

        // Register Injekt singletons BEFORE loading extensions. Many extension source classes
        // resolve Injekt dependencies (Application, Json, …) in their constructors, and the
        // extension manager loads/instantiates every source in its init block. If Injekt isn't
        // configured yet, those constructors throw and the extension is silently dropped as a
        // load error on cold start — only reappearing after a later refresh(). Order matters.
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

        extensionManager = AnimeExtensionManager(this)

        Coil.setImageLoader(
            ImageLoader.Builder(this)
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