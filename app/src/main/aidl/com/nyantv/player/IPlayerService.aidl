package com.nyantv.player;

import com.nyantv.player.IPlayerCallback;
import android.view.Surface;

/**
 * Commands from the UI process to the PlayerService (player process).
 *
 * All mutating commands are marked `oneway`:
 *   - UI-Thread returns immediately, zero blocking wait for the player process.
 *   - Binder queues the transaction and delivers it async to the player process.
 *   - Critical on 32-bit Android TV: slow Binder under load won't jank the UI.
 *
 * Getters remain synchronous — they are only called during setup/teardown,
 * never from the hot path (position updates come via IPlayerCallback instead).
 */
interface IPlayerService {

    // ── Playback commands (fire-and-forget) ────────────────────────────────────
    oneway void load(String uri, long startPositionMs);
    oneway void loadWithHeaders(String uri, String headersJson, long startPositionMs);
    oneway void play();
    oneway void pause();
    oneway void stop();
    oneway void seekTo(long positionMs);
    oneway void seekForward();
    oneway void seekBackward();
    oneway void setPlaybackSpeed(float speed);

    // ── Surface binding (fire-and-forget) ─────────────────────────────────────
    oneway void setSurface(in Surface surface);
    oneway void clearSurface();

    // ── Callback registration (fire-and-forget) ────────────────────────────────
    oneway void registerCallback(IPlayerCallback callback);
    oneway void unregisterCallback(IPlayerCallback callback);

    // ── Synchronous state getters (setup/teardown only, not hot path) ──────────
    long    getPosition();
    long    getDuration();
    long    getBufferedPosition();
    int     getState();
    boolean getPlayWhenReady();
}
