package eu.kanade.tachiyomi.network

import android.content.Context
import eu.kanade.tachiyomi.network.interceptor.CloudflareInterceptor
import eu.kanade.tachiyomi.network.interceptor.IgnoreGzipInterceptor
import eu.kanade.tachiyomi.network.interceptor.UncaughtExceptionInterceptor
import eu.kanade.tachiyomi.network.interceptor.UserAgentInterceptor
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.brotli.BrotliInterceptor
import java.io.File
import java.util.concurrent.TimeUnit

class NetworkHelper(private val context: Context) {

    val cookieJar = AndroidCookieJar()

    val client: OkHttpClient = run {
        val baseClient = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(2, TimeUnit.MINUTES)
            .cache(
                Cache(
                    directory = File(context.externalCacheDir ?: context.cacheDir, "network_cache"),
                    maxSize = 5L * 1024 * 1024,
                ),
            )
            .addInterceptor(UncaughtExceptionInterceptor())
            .addInterceptor(UserAgentInterceptor(::defaultUserAgentProvider))
            .addNetworkInterceptor(IgnoreGzipInterceptor())
            .addNetworkInterceptor(BrotliInterceptor)
            .build()

        baseClient.newBuilder()
            .addInterceptor(
                CloudflareInterceptor(context, cookieJar, ::defaultUserAgentProvider),
            )
            .build()
    }

    @Deprecated("The regular client handles Cloudflare by default")
    @Suppress("UNUSED")
    val cloudflareClient: OkHttpClient = client

    companion object {
        /**
         * User-Agent derived from the device's actual WebView (set once at startup via
         * [setDeviceUserAgent]). Using the real engine's UA keeps the UA consistent with the
         * browser that solves Cloudflare challenges — claiming a newer Chrome than the
         * installed WebView is itself a fingerprint mismatch that Cloudflare penalizes.
         */
        @Volatile
        private var deviceUserAgent: String? = null

        /**
         * Fallback UA used until the real WebView UA is known (or if WebView is unavailable).
         * A current mobile Chrome string.
         */
        private const val FALLBACK_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

        fun defaultUserAgentProvider(): String =
            deviceUserAgent?.takeIf { it.isNotBlank() } ?: FALLBACK_USER_AGENT

        /**
         * Stores a sanitized copy of the device WebView's User-Agent. The raw WebView UA
         * contains a "; wv" token and a "Version/x.x" token that flag it as a WebView; we
         * strip those so it reads as a normal Chrome browser.
         */
        fun setDeviceUserAgent(rawWebViewUserAgent: String?) {
            deviceUserAgent = rawWebViewUserAgent
                ?.replace("; wv", "")
                ?.replace(Regex("""Version/[\d.]+ """), "")
                ?.takeIf { it.isNotBlank() }
        }

        @Volatile
        private var instance: NetworkHelper? = null

        fun setInstance(helper: NetworkHelper) {
            instance = helper
        }

        fun requireInstance(): NetworkHelper =
            instance ?: error("NetworkHelper not initialized. Call setInstance() first.")
    }
}