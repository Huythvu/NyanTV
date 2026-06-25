package com.nyantv.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri

/**
 * Full-screen overlay that runs an OAuth login inside an in-app [WebView].
 *
 * The tracker logins (MAL/AniList/Simkl) used Chrome Custom Tabs, which depend on an external
 * browser — unavailable on many Android TVs (e.g. Sony Bravia ships no Chrome). This hosts the
 * authorize page in-app and captures the `nyantv://callback?code=...` redirect directly, so no
 * external browser is needed.
 *
 * @param authUrl the authorize URL to load, or null to hide the overlay.
 * @param onCode  invoked with the authorization code once the redirect is intercepted.
 * @param onDismiss invoked when the user cancels or the redirect has no code.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OAuthWebViewOverlay(
    authUrl: String?,
    onCode: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (authUrl == null) return

    BackHandler(enabled = true) { onDismiss() }

    // Re-create the WebView whenever the URL changes (e.g. a second login attempt).
    key(authUrl) {
        var loading by remember { mutableStateOf(true) }

        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true

                            webViewClient = object : WebViewClient() {
                                /** Returns true if [url] was the callback redirect (and consumes it). */
                                fun handleRedirect(url: String?): Boolean {
                                    val uri = url?.toUri() ?: return false
                                    if (uri.scheme == "nyantv" && uri.host == "callback") {
                                        val code = uri.getQueryParameter("code")
                                        if (code != null) onCode(code) else onDismiss()
                                        return true
                                    }
                                    return false
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean = handleRedirect(request?.url?.toString())

                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    if (!handleRedirect(url)) loading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    loading = false
                                }
                            }

                            loadUrl(authUrl)
                        }
                    },
                )

                if (loading) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                }

                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}
