package eu.kanade.tachiyomi.network

import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import java.util.concurrent.TimeUnit.MINUTES

private val DEFAULT_CACHE_CONTROL = CacheControl.Builder().maxAge(10, MINUTES).build()
private val DEFAULT_HEADERS = Headers.Builder().build()
private val DEFAULT_BODY: RequestBody = FormBody.Builder().build()

fun GET(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    val nUrl = url.toHttpUrl()
    return GET(nUrl, headers, cache)
}

/**
 * @since extensions-lib 1.4
 */
fun GET(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    return Request.Builder()
        .url(url)
        .headers(headers)
        .cacheControl(cache)
        .build()
}

fun POST(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    return Request.Builder()
        .url(url)
        .post(body)
        .headers(headers)
        .cacheControl(cache)
        .build()
}

fun PUT(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    return Request.Builder()
        .url(url)
        .put(body)
        .headers(headers)
        .cacheControl(cache)
        .build()
}

fun DELETE(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    return Request.Builder()
        .url(url)
        .delete(body)
        .headers(headers)
        .cacheControl(cache)
        .build()
}

fun HEAD(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Request {
    return Request.Builder()
        .url(url)
        .head()
        .headers(headers)
        .cacheControl(cache)
        .build()
}

// ──────────────────────────────────────────────────────────────────────────────────────────────
// Suspend request helpers (extensions-lib 16). Newer extensions (e.g. Anichi, Anikoto) call
// client.get(...)/post(...) and expect these top-level functions in eu.kanade.tachiyomi.network
// (RequestsKt). Without them the extension crashes with "No static method get$default in the
// RequestsKt class". They build the request, execute it, and return the response, throwing on a
// non-2xx status (via Call.awaitSuccess). Both String and HttpUrl url overloads are provided
// because an extension may call either; the crash was specifically the missing HttpUrl overload.

suspend fun OkHttpClient.get(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response {
    return newCall(GET(url, headers, cache)).awaitSuccess()
}

suspend fun OkHttpClient.get(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response {
    return newCall(GET(url, headers, cache)).awaitSuccess()
}

suspend fun OkHttpClient.post(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response {
    return newCall(POST(url, headers, body, cache)).awaitSuccess()
}

suspend fun OkHttpClient.post(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response {
    return newCall(POST(url.toString(), headers, body, cache)).awaitSuccess()
}

suspend fun OkHttpClient.put(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    body: RequestBody = DEFAULT_BODY,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response {
    return newCall(PUT(url, headers, body, cache)).awaitSuccess()
}

suspend fun OkHttpClient.head(
    url: String,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response {
    return newCall(HEAD(url, headers, cache)).awaitSuccess()
}

suspend fun OkHttpClient.head(
    url: HttpUrl,
    headers: Headers = DEFAULT_HEADERS,
    cache: CacheControl = DEFAULT_CACHE_CONTROL,
): Response {
    return newCall(HEAD(url.toString(), headers, cache)).awaitSuccess()
}
