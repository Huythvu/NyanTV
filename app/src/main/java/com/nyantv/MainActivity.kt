package com.nyantv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nyantv.ui.MainNavigation
import com.nyantv.ui.theme.NyanTVTheme
import com.nyantv.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    private var pendingWatchLink by mutableStateOf<Triple<String, String, String>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)

        setContent {
            val activeTheme by vm.activeTheme.collectAsStateWithLifecycle()
            NyanTVTheme(activeTheme = activeTheme) {
                MainNavigation(
                    vm                  = vm,
                    deepLink            = pendingWatchLink,
                    onDeepLinkConsumed  = { pendingWatchLink = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        when {
            uri.scheme == "nyantv" && uri.host == "callback" -> {
                val code = uri.getQueryParameter("code") ?: return
                vm.handleAuthCallback(code)
            }
            uri.scheme == "nyantv" && uri.host == "watch" -> {
                val serviceKey    = uri.pathSegments.getOrNull(0) ?: return
                val mediaId       = uri.pathSegments.getOrNull(1) ?: return
                val episodeNumber = uri.pathSegments.getOrNull(2) ?: "1"
                pendingWatchLink  = Triple(serviceKey, mediaId, episodeNumber)
            }
        }
    }
}