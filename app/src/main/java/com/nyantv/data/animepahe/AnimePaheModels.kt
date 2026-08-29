package com.nyantv.data.animepahe

import kotlinx.serialization.Serializable

/**
 * One entry from the AnimePahe Watchlist (the companion Chrome extension's synced record).
 *
 * Field semantics come from the extension's `sanitizeItems` (sync.js):
 *  - [episode] is AnimePahe's **continuous** episode number as a string (e.g. "166") — NOT AniList
 *    per-season numbering. Displayed as-is; never fed into AniList progress in Stage 1.
 *  - [animeUrl] / [playUrl] are AnimePahe-relative paths (the extension's own join key).
 *  - [animeId] is AnimePahe's internal id, useless to AniList/MAL.
 *  - [anilistId] is written by the extension when it has resolved one (added for NyanTV sync); it is
 *    the cross-app join key. Null on older records until the list is re-synced from the extension.
 */
@Serializable
data class AnimePaheEntry(
    val title: String,
    val episode: String = "",
    val playUrl: String = "",
    val animeUrl: String = "",
    val thumb: String = "",
    val ts: Long = 0L,
    val status: String = STATUS_WATCHING,   // "watching" | "plan"
    val statusTs: Long = 0L,
    val animeId: Int? = null,
    val anilistId: Int? = null,
) {
    /** Most recent activity timestamp, matching the extension's merge ordering. */
    val sortTs: Long get() = maxOf(ts, statusTs)

    val isWatching: Boolean get() = status != STATUS_PLAN
    val isPlan: Boolean get() = status == STATUS_PLAN

    companion object {
        const val STATUS_WATCHING = "watching"
        const val STATUS_PLAN = "plan"
    }
}

/** Locally cached snapshot of a fetched watchlist, so the rows survive offline / between launches. */
@Serializable
data class AnimePaheWatchlistSnapshot(
    val items: List<AnimePaheEntry> = emptyList(),
    val fetchedAt: Long = 0L,
)

/**
 * A resolved AnimePahe→AniList match, cached by animeUrl so we don't re-query AniList on every home
 * open. [anilistId] null means we tried and found nothing (kept so failures aren't retried forever).
 */
@Serializable
data class AnimePaheResolution(
    val anilistId: Int? = null,
    val title: String = "",
    val poster: String? = null,
)
