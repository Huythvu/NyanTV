package com.nyantv.data

import kotlinx.serialization.json.*

/**
 * Synthetic id prefix for anime opened straight from an extension catalog that has no
 * matching AniList/MAL entry. Such media are watchable but not trackable; code that talks
 * to a tracking service must skip these ids.
 */
const val EXTERNAL_MEDIA_PREFIX = "ext:"

fun String.isExternalMediaId() = startsWith(EXTERNAL_MEDIA_PREFIX)

// ─── Domain models ────────────────────────────────────────────────────────────

data class Media(
    val id: String,
    val title: String,
    val romajiTitle: String? = null,
    val altTitles: List<String> = emptyList(),   // alternative titles (en/ja/synonyms) for source search
    val poster: String? = null,
    val cover: String? = null,          // banner
    val logo: String? = null,
    val description: String? = null,
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val status: String? = null,
    val genres: List<String> = emptyList(),
    val serviceType: ServiceType = ServiceType.ANILIST,
    val idMal: String? = null,
    val color: String? = null,
    val format: String? = null,
    val season: String? = null,
    val seasonYear: Int? = null,
    val relations: List<Media> = emptyList(),
    val relationType: String? = null,
    val recommendations: List<Media> = emptyList(),
    val nextAiringEpisode: AiringEpisode? = null,
    val popularity: Int? = null,
    val imdbId: String? = null,
    val tmdbId: String? = null,
)

data class AiringEpisode(val airingAt: Long, val episode: Int)

/** One scheduled episode airing (for the Schedule tab): which media, when, and which episode. */
data class AiringScheduleEntry(val media: Media, val airingAtSec: Long, val episode: Int)

data class TrackedMedia(
    val id: String = "",
    val title: String = "",
    val poster: String? = null,
    val watchingStatus: String? = null,   // CURRENT / COMPLETED / PLANNING / DROPPED / PAUSED
    val episodeCount: Int? = null,
    val totalEpisodes: Int? = null,
    val averageScore: Int? = null,
    val score: Float? = null,
    val isMovie: Boolean? = null,
    val status: String? = null,           // airing status (RELEASING / FINISHED / …)
    val nextAiringEpisode: AiringEpisode? = null,
)

data class Profile(
    val id: String? = null,
    val name: String? = null,
    val avatar: String? = null,
    val banner: String? = null,
    val episodesWatched: Int? = null,
    val meanScore: Float? = null,
    val animeCount: Int? = null
)

// ─── AniList JSON parsing ──────────────────────────────────────────────────────

fun JsonObject.toMedia(serviceType: ServiceType = ServiceType.ANILIST): Media {
    val title = this["title"]?.takeIf { it !is JsonNull }?.jsonObject
    val cover  = this["coverImage"]?.takeIf { it !is JsonNull }?.jsonObject
    return Media(
        id          = this["id"]!!.jsonPrimitive.content,
        title       = title?.get("english")?.jsonPrimitive?.contentOrNull
            ?: title?.get("romaji")?.jsonPrimitive?.contentOrNull ?: "?",
        romajiTitle = title?.get("romaji")?.jsonPrimitive?.contentOrNull,
        altTitles   = listOfNotNull(
            title?.get("english")?.jsonPrimitive?.contentOrNull,
            title?.get("romaji")?.jsonPrimitive?.contentOrNull,
            title?.get("native")?.jsonPrimitive?.contentOrNull,
        ).distinct(),
        poster      = cover?.get("large")?.jsonPrimitive?.contentOrNull,
        cover       = this["bannerImage"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull,
        color       = cover?.get("color")?.jsonPrimitive?.contentOrNull,
        description = this["description"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull,
        averageScore= this["averageScore"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull,
        episodes    = this["episodes"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull,
        status      = this["status"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull,
        genres      = this["genres"]?.takeIf { it !is JsonNull }?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
        idMal       = this["idMal"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull,
        format      = this["format"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull,
        season      = this["season"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull,
        seasonYear  = this["seasonYear"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull,
        popularity  = this["popularity"]?.takeIf { it !is JsonNull }?.jsonPrimitive?.intOrNull,
        serviceType = serviceType,
        nextAiringEpisode = this["nextAiringEpisode"]?.takeIf { it !is JsonNull }?.jsonObject?.let {
            AiringEpisode(
                airingAt = it["airingAt"]?.jsonPrimitive?.long ?: 0L,
                episode  = it["episode"]?.jsonPrimitive?.int ?: 0
            )
        },
        relations = this["relations"]?.takeIf { it !is JsonNull }?.jsonObject
            ?.get("edges")?.takeIf { it !is JsonNull }?.jsonArray
            ?.mapNotNull { edge ->
                val node = edge.jsonObject["node"]
                    ?.takeIf { n -> n !is JsonNull }
                    ?.jsonObject ?: return@mapNotNull null
                val type = node["type"]?.jsonPrimitive?.contentOrNull
                if (type == "MANGA" || type == "NOVEL") return@mapNotNull null
                val relationType = edge.jsonObject["relationType"]
                    ?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull
                node.toMedia(serviceType).copy(relationType = relationType)
            } ?: emptyList(),
        recommendations = this["recommendations"]?.takeIf { it !is JsonNull }?.jsonObject
            ?.get("edges")?.takeIf { it !is JsonNull }?.jsonArray
            ?.mapNotNull {
                it.jsonObject["node"]
                    ?.takeIf { n -> n !is JsonNull }
                    ?.jsonObject
                    ?.get("mediaRecommendation")
                    ?.takeIf { r -> r !is JsonNull }
                    ?.jsonObject
                    ?.toMedia(serviceType)
            } ?: emptyList()
    )
}

fun JsonObject.toTrackedMedia(): TrackedMedia {
    val media    = this["media"]?.jsonObject
    val cover    = media?.get("coverImage")?.jsonObject
    val titleObj = media?.get("title")?.jsonObject
    return TrackedMedia(
        id             = media?.get("id")?.jsonPrimitive?.contentOrNull ?: "",
        title          = titleObj?.get("english")?.jsonPrimitive?.contentOrNull
            ?: titleObj?.get("romaji")?.jsonPrimitive?.contentOrNull ?: "?",
        poster         = cover?.get("large")?.jsonPrimitive?.contentOrNull,
        watchingStatus = this["status"]?.jsonPrimitive?.contentOrNull.normalizeStatus(),
        episodeCount   = this["progress"]?.jsonPrimitive?.intOrNull,
        totalEpisodes  = media?.get("episodes")?.jsonPrimitive?.intOrNull,
        score          = this["score"]?.jsonPrimitive?.floatOrNull,
        averageScore   = media?.get("averageScore")?.jsonPrimitive?.intOrNull,
        status         = media?.get("status")?.jsonPrimitive?.contentOrNull.normalizeStatus(),
        nextAiringEpisode = media?.get("nextAiringEpisode")?.takeIf { it !is JsonNull }?.jsonObject?.let {
            AiringEpisode(
                airingAt = it["airingAt"]?.jsonPrimitive?.long ?: 0L,
                episode  = it["episode"]?.jsonPrimitive?.int ?: 0,
            )
        },
    )
}

fun String?.normalizeStatus(): String? =
    this?.replace('_', ' ')?.uppercase()?.ifBlank { null }
