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