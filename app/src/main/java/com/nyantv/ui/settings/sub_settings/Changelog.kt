package com.nyantv.ui.settings.sub_settings

/**
 * In-app version history. This is the source of truth for the "Version history" screen, so a user
 * can confirm which build they're running and what it contains.
 *
 * Add a new entry at the TOP (newest first) when bumping versionName/versionCode in build.gradle.kts;
 * keep [code] in sync with versionCode so the running build can be matched and highlighted.
 */
data class ChangelogEntry(
    val version: String,
    val code: Int,
    val date: String,
    val changes: List<String>,
)

val CHANGELOG: List<ChangelogEntry> = listOf(
    ChangelogEntry(
        version = "1.3.1",
        code = 5,
        date = "2026-11-02",
        changes = listOf(
            "Fixed the video-playback crash on lib-16 sources (KissKH, Anikoto) by aligning the Video model to the current library",
        ),
    ),
    ChangelogEntry(
        version = "1.3.0",
        code = 4,
        date = "2026-11-02",
        changes = listOf(
            "Compatibility with newer (extensions-lib 16) anime extensions — KissKH, AniHQ, Anichi, AniDB, Anikoto and others now load instead of crashing on the Hoster constructor",
            "Fixed lib-16 episode-list errors (SEpisode fillermark) and the network helper crash (RequestsKt.get / get\$default)",
            "More robust video playback through the hoster API, with correct eager/lazy hoster handling",
            "Player: no longer asks for server/quality every episode — uses your quality by default and remembers manual changes",
            "Player: in-entry search stays on the current anime when browsing related shows, and resets when you leave the detail",
            "AnimePahe Watchlist two-way sync: Continue Watching / Plan to Watch, episode progress, and deletions that don't come back",
        ),
    ),
    ChangelogEntry(
        version = "1.2.0",
        code = 3,
        date = "",
        changes = listOf(
            "Resolved detail-screen scroll jank and a login race condition",
            "Corrected Continue Watching order after updating an entry",
            "Reused the player across sessions to prevent out-of-memory crashes",
            "Fixed duplicate/restricted subtitles and the stream/subtitle picker layout",
            "Fixed focus loss when skipping OP/ED, and an extension-settings crash",
            "Faster startup: parallelized carousel preloading and auth init",
        ),
    ),
)
