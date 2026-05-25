package com.nyantv.player

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import android.view.Surface
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/**
 * Runs in the dedicated `:player` process (see AndroidManifest).
 *
 * ── Threading rules ──────────────────────────────────────────────────────────
 * ExoPlayer is built with setLooper(playerThread.looper). This means:
 *   - ALL ExoPlayer API calls (getters AND setters) MUST happen on playerThread.
 *   - mainHandler is only used for Service lifecycle (onCreate, onDestroy).
 *   - Position ticks MUST also run on playerHandler — NOT mainHandler.
 *     (v2 bug: ticks were on mainHandler → "Player accessed on wrong thread" crash)
 */
@UnstableApi
class PlayerService : Service() {

    companion object { private const val TAG = "NyanTV:PlayerService" }

    // ── Threads & handlers ─────────────────────────────────────────────────────

    private lateinit var playerThread: HandlerThread
    private lateinit var playerHandler: Handler

    // ── Player ─────────────────────────────────────────────────────────────────

    private lateinit var player: ExoPlayer

    private lateinit var trackSelector: DefaultTrackSelector

    // ── Callbacks ──────────────────────────────────────────────────────────────

    private val callbacks = RemoteCallbackList<IPlayerCallback>()
    private var positionTick: Runnable? = null

    // ── AIDL stub ──────────────────────────────────────────────────────────────

    private val stub = object : IPlayerService.Stub() {

        override fun load(uri: String) {
            Log.d(TAG, "load: $uri")
            post {
                val forceHighest = getSharedPreferences("nyantv_player_prefs", Context.MODE_PRIVATE)
                    .getString("quality_mode", "abr") == "highest"
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setForceHighestSupportedBitrate(forceHighest)
                )
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
                player.playWhenReady = true
            }
        }

        override fun loadWithHeaders(uri: String, headersJson: String) {
            post {
                val headers = runCatching {
                    org.json.JSONObject(headersJson).let { json ->
                        json.keys().asSequence().associateWith { json.getString(it) }
                    }
                }.getOrDefault(emptyMap())

                val dataSourceFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                    .setUserAgent(headers["User-Agent"] ?: "Mozilla/5.0")
                    .setDefaultRequestProperties(headers)

                val mediaItem = androidx.media3.common.MediaItem.fromUri(uri)

                // HLS erkennen: .m3u8 ODER Content-Type-Header
                val isHls = uri.contains(".m3u8", ignoreCase = true)
                        || headers["Content-Type"]?.contains("mpegurl", ignoreCase = true) == true

                val mediaSource = if (isHls) {
                    androidx.media3.exoplayer.hls.HlsMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                } else {
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
                        .createMediaSource(mediaItem)
                }

                val forceHighest = getSharedPreferences("nyantv_player_prefs", Context.MODE_PRIVATE)
                    .getString("quality_mode", "abr") == "highest"
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .setForceHighestSupportedBitrate(forceHighest)
                )
                player.setMediaSource(mediaSource)
                player.prepare()
                player.playWhenReady = true
            }
        }

        override fun play()  = post { player.play() }
        override fun pause() = post { player.pause() }

        override fun stop() = post {
            player.stop()
            player.clearMediaItems()
        }

        override fun seekTo(positionMs: Long)       = post { player.seekTo(positionMs) }
        override fun seekForward()                  = post { player.seekForward() }
        override fun seekBackward()                 = post { player.seekBack() }
        override fun setPlaybackSpeed(speed: Float) = post { player.setPlaybackSpeed(speed) }

        override fun setSurface(surface: Surface?) {
            Log.d(TAG, "setSurface: $surface")
            post { player.setVideoSurface(surface) }
        }

        override fun clearSurface() = post { player.clearVideoSurface() }

        override fun registerCallback(cb: IPlayerCallback?) {
            cb?.let {
                callbacks.register(it)
                Log.d(TAG, "registerCallback — total=${callbacks.registeredCallbackCount}")
            }
        }

        override fun unregisterCallback(cb: IPlayerCallback?) {
            cb?.let { callbacks.unregister(it) }
        }

        // Synchronous getters — must also be called from playerThread context.
        // The UI calls these rarely (only in refreshPosition) and accepts the
        // brief Binder blocking; ExoPlayer will throw if called from a wrong thread.
        override fun getPosition()         = player.currentPosition
        override fun getDuration()         = player.duration
        override fun getBufferedPosition() = player.bufferedPosition
        override fun getState()            = player.playbackState
        override fun getPlayWhenReady()    = player.playWhenReady
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        playerThread = HandlerThread("ExoPlayerThread", Process.THREAD_PRIORITY_AUDIO)
            .also { it.start() }
        playerHandler = Handler(playerThread.looper)

        // Build ExoPlayer synchronously; setLooper() tells it to use playerThread
        // for ALL internal and external API calls.
        trackSelector = DefaultTrackSelector(this)
        player = ExoPlayer.Builder(this)
            .setLooper(playerThread.looper)
            .setTrackSelector(trackSelector)
            .setLoadControl(AdaptiveBufferingHelper.buildLoadControl(this))
            .build()
            .also { it.addListener(playerListener) }

        Log.d(TAG, "ExoPlayer ready on thread: ${playerThread.name}")

        // Position ticks must also run on playerHandler — player getters are
        // only allowed on ExoPlayerThread.
        schedulePositionTicks()
    }

    override fun onBind(intent: Intent): IBinder {
        Log.d(TAG, "onBind")
        return stub
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")

        // Cancel ticks on the same handler they were scheduled on
        positionTick?.let { playerHandler.removeCallbacks(it) }

        playerHandler.post {
            player.removeListener(playerListener)
            player.release()
            playerThread.quitSafely()
        }

        callbacks.kill()
        super.onDestroy()
    }

    // ── Player listener (fires on playerThread — same looper ExoPlayer uses) ───

    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            Log.d(TAG, "onPlaybackStateChanged: $state")
            broadcast { it.onStateChanged(state) }
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            Log.d(TAG, "onPlayWhenReadyChanged: $playWhenReady")
            broadcast { it.onPlayWhenReadyChanged(playWhenReady) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "onPlayerError: ${error.errorCode} — ${error.localizedMessage}", error)
            broadcast { it.onError(error.localizedMessage ?: "Playback error") }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            Log.d(TAG, "onVideoSizeChanged: ${videoSize.width}x${videoSize.height}")
            broadcast { it.onVideoSizeChanged(videoSize.width, videoSize.height) }
        }

        override fun onPositionDiscontinuity(
            oldPos: Player.PositionInfo,
            newPos: Player.PositionInfo,
            reason: Int
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                val pos = player.currentPosition
                val dur = player.duration
                val buf = player.bufferedPosition
                Log.d(TAG, "seek discontinuity → pos=$pos")
                broadcast { cb ->
                    cb.onPositionChanged(pos, dur)
                    cb.onBufferedChanged(buf)
                }
            }
        }
    }

    // ── Position ticks ─────────────────────────────────────────────────────────
    // Run on playerHandler so ExoPlayer getters are called on the correct thread.

    private fun schedulePositionTicks() {
        positionTick = object : Runnable {
            override fun run() {
                val state = player.playbackState   // safe: we are on playerThread
                if (state == Player.STATE_READY || state == Player.STATE_BUFFERING) {
                    val pos = player.currentPosition
                    val dur = player.duration
                    val buf = player.bufferedPosition
                    broadcast {
                        it.onPositionChanged(pos, dur)
                        it.onBufferedChanged(buf)
                    }
                }
                playerHandler.postDelayed(this, 500L)
            }
        }
        playerHandler.postDelayed(positionTick!!, 500L)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun broadcast(action: (IPlayerCallback) -> Unit) {
        val n = callbacks.beginBroadcast()
        repeat(n) { i -> runCatching { action(callbacks.getBroadcastItem(i)) } }
        callbacks.finishBroadcast()
    }

    /** Dispatch to playerThread. If already on it, execute inline. */
    private fun post(block: () -> Unit) {
        if (Looper.myLooper() == playerThread.looper) block()
        else playerHandler.post(block)
    }
}