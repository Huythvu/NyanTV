package com.nyantv.player;

/**
 * Callbacks from PlayerService (player process) → UI process.
 * All methods are oneway = fire-and-forget, never block the player thread.
 */
oneway interface IPlayerCallback {
    /** Player.STATE_IDLE=1, BUFFERING=2, READY=3, ENDED=4 */
    void onStateChanged(int state);
    void onPositionChanged(long positionMs, long durationMs);
    void onBufferedChanged(long bufferedMs);
    void onPlayWhenReadyChanged(boolean playWhenReady);
    void onVideoSizeChanged(int width, int height);
    void onError(String message);
}
