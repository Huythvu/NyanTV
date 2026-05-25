package com.nyantv

import android.annotation.SuppressLint
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import com.nyantv.viewmodel.AppViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first

@SuppressLint("CustomSplashScreen")
class SplashActivity : ComponentActivity() {

    private val vm: AppViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory.getInstance(application)
    }
    private var mediaPlayer: MediaPlayer? = null
    private var hasNavigated = false
    private var soundDone = false
    private var dataReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )

        setContent { SplashScreen() }

        lifecycleScope.launch {
            delay(300)

            withTimeoutOrNull(5_000) {
                vm.trending.first { it.isNotEmpty() }
            }

            dataReady = true
            maybeNavigate()
        }

        // ── Sound ─────────────────────────────────────────────────────────────
        try {
            mediaPlayer = MediaPlayer.create(this, R.raw.splash_sound)
            mediaPlayer?.setOnCompletionListener {
                lifecycleScope.launch {
                    delay(500)
                    soundDone = true
                    maybeNavigate()
                }
            }
            mediaPlayer?.start()
        } catch (e: Exception) {
            e.printStackTrace()
            lifecycleScope.launch {
                delay(1_500)
                soundDone = true
                maybeNavigate()
            }
        }
    }

    private fun maybeNavigate() {
        if (soundDone && dataReady) navigateToMain()
    }

    private fun navigateToMain() {
        if (hasNavigated) return
        hasNavigated = true
        mediaPlayer?.stop()
        val options = ActivityOptionsCompat.makeCustomAnimation(
            this,
            android.R.anim.fade_in,
            android.R.anim.fade_out
        )
        startActivity(Intent(this, MainActivity::class.java), options.toBundle())
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }
}

@Composable
private fun SplashScreen() {
    var visible by remember { mutableStateOf(false) }
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "splash_fade"
    )
    LaunchedEffect(Unit) { visible = true }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF6438FF)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_logo),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(0.7f).alpha(alpha)
        )
    }
}