package eu.kanade.tachiyomi.network.interceptor

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import eu.kanade.tachiyomi.network.AndroidCookieJar
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import java.util.concurrent.CountDownLatch

class CloudflareInterceptor(
    private val context: Context,
    private val cookieManager: AndroidCookieJar,
    defaultUserAgentProvider: () -> String,
) : WebViewInterceptor(context, defaultUserAgentProvider) {

    private val executor = ContextCompat.getMainExecutor(context)

    override fun shouldIntercept(response: Response): Boolean {
        return if (response.code in ERROR_CODES && response.header("Server") in SERVER_CHECK) {
            val document = Jsoup.parse(
                response.peekBody(Long.MAX_VALUE).string(),
                response.request.url.toString(),
            )
            document.getElementById("challenge-error-title") != null ||
                    document.getElementById("challenge-error-text") != null
        } else {
            false
        }
    }

    override fun intercept(
        chain: Interceptor.Chain,
        request: Request,
        response: Response,
    ): Response {
        Log.d(TAG, "Cloudflare challenge detected for ${request.url} (HTTP ${response.code})")
        try {
            response.close()
            cookieManager.remove(request.url, COOKIE_NAMES, 0)
            val oldCookie = cookieManager.get(request.url)
                .firstOrNull { it.name == "cf_clearance" }
            resolveWithWebView(request, oldCookie)
            Log.d(TAG, "Cloudflare bypass succeeded for ${request.url}")
            return chain.proceed(request)
        } catch (e: CloudflareBypassException) {
            Log.e(TAG, "Cloudflare bypass FAILED for ${request.url} — challenge not solved within timeout", e)
            throw IOException("Cloudflare bypass failed", e)
        } catch (e: Exception) {
            Log.e(TAG, "Cloudflare bypass error for ${request.url}: ${e.javaClass.simpleName}: ${e.message}", e)
            throw IOException(e)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun resolveWithWebView(originalRequest: Request, oldCookie: Cookie?) {
        val latch = CountDownLatch(1)
        var webview: WebView? = null
        var challengeFound = false
        var cloudflareBypassed = false

        val origRequestUrl = originalRequest.url.toString()
        val headers = parseHeaders(originalRequest.headers)

        executor.execute {
            try {
                webview = createWebView(originalRequest)
                Log.d(TAG, "WebView created; UA=${webview?.settings?.userAgentString}")
            } catch (e: Throwable) {
                // e.g. MissingWebViewPackageException on devices without Android System WebView
                Log.e(TAG, "WebView unavailable on this device: ${e.javaClass.simpleName}: ${e.message}", e)
                latch.countDown()
                return@execute
            }

            webview?.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    fun isCloudFlareBypassed(): Boolean {
                        return cookieManager.get(origRequestUrl.toHttpUrl())
                            .firstOrNull { it.name == "cf_clearance" }
                            .let { it != null && it != oldCookie }
                    }

                    if (isCloudFlareBypassed()) {
                        cloudflareBypassed = true
                        latch.countDown()
                    }

                    if (url == origRequestUrl && !challengeFound) {
                        latch.countDown()
                    }
                }

                override fun onReceivedHttpError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    errorResponse: WebResourceResponse?,
                ) {
                    if (request?.isForMainFrame == true) {
                        if (errorResponse?.statusCode in ERROR_CODES) {
                            challengeFound = true
                        } else {
                            latch.countDown()
                        }
                    }
                }
            }

            webview?.loadUrl(origRequestUrl, headers)
        }

        latch.awaitFor30Seconds()

        executor.execute {
            webview?.run {
                stopLoading()
                destroy()
            }
        }

        if (!cloudflareBypassed) {
            Log.w(TAG, "Bypass unsuccessful (challengeFound=$challengeFound). " +
                "If challengeFound=true the WebView ran but couldn't solve it — usually an " +
                "outdated Android System WebView. Update it via the Play Store.")
            throw CloudflareBypassException()
        }
    }
}

private const val TAG = "NyanExt"
private val ERROR_CODES = listOf(403, 503)
private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
private val COOKIE_NAMES = listOf("cf_clearance")
private class CloudflareBypassException : Exception()