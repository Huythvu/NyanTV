package com.nyantv

import android.content.Intent
import android.os.Bundle
import androidx.lifecycle.ViewModelProvider
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nyantv.ui.MainNavigation
import com.nyantv.ui.theme.NyanTVTheme
import com.nyantv.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Handle OAuth deep-link that may have launched the Activity
        handleIntent(intent)

        setContent {
            val activeTheme by vm.activeTheme.collectAsStateWithLifecycle()
            NyanTVTheme(activeTheme = activeTheme) {
                MainNavigation(vm = vm)
            }
        }
    }

    // Called when the app is already running and a new deep-link arrives
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "nyantv" && uri.host == "callback") {
            val code = uri.getQueryParameter("code") ?: return
            vm.handleAuthCallback(code)
        }
    }
}
