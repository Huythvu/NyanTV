package com.nyantv.player

import android.app.ActivityManager
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl

/**
 * Builds an ExoPlayer LoadControl tuned to the device's available RAM and
 * current network transport. Android TV devices on 32-bit arch often have
 * ≤2 GB of RAM — we scale buffers aggressively to avoid OOM.
 */
object AdaptiveBufferingHelper {

    @OptIn(UnstableApi::class)
    fun buildLoadControl(context: Context): DefaultLoadControl {
        val totalRamMb = totalRamMb(context)
        val onWifi     = isOnWifi(context)

        // Buffer tiers based on available RAM
        val (minMs, maxMs, targetMs) = when {
            totalRamMb < 512  -> Triple( 4_000,  20_000, 12_000)  // very low RAM (512 MB)
            totalRamMb < 1024 -> Triple( 6_000,  30_000, 20_000)  // low  RAM (≤1 GB)
            totalRamMb < 2048 -> Triple( 8_000,  45_000, 30_000)  // mid  RAM (≤2 GB)
            else              -> Triple(10_000,  60_000, 40_000)  // high RAM (>2 GB)
        }

        val bufMax    = if (onWifi) maxMs    else maxMs / 2

        // Buffer for playback re-buffer: half of target

        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                minMs,
                bufMax,
                minMs / 2,
                minMs * 3 / 4
            )
            // On low-RAM: prefer time accuracy over size thresholds so we don't
            // buffer more bytes than needed
            .setPrioritizeTimeOverSizeThresholds(totalRamMb < 1024)
            // Back-buffer: keep 10 s behind for seek-back; reduce to 5 s on low RAM
            .setBackBuffer(
                /* backBufferDurationMs         = */ if (totalRamMb < 1024) 5_000 else 10_000,
                /* retainBackBufferFromKeyframe = */ true
            )
            .build()
    }

    private fun totalRamMb(context: Context): Long {
        val am  = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        return mem.totalMem / (1024L * 1024L)
    }

    private fun isOnWifi(context: Context): Boolean {
        val cm   = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            cm.getNetworkCapabilities(cm.activeNetwork)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.let { null } // fallback: assume not-wifi
        }
        return caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }
}
