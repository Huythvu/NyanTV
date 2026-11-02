package eu.kanade.tachiyomi.animesource.model

import android.net.Uri
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import java.io.Serializable

@kotlinx.serialization.Serializable
data class Track(val url: String, val lang: String) : Serializable

@kotlinx.serialization.Serializable
enum class ChapterType {
    Opening,
    Ending,
    Recap,
    MixedOp,
    Other,
}

@kotlinx.serialization.Serializable
data class TimeStamp(
    val start: Double,
    val end: Double,
    val name: String,
    val type: ChapterType = ChapterType.Other,
)

/**
 * extensions-lib 16 Video model. The primary constructor shape is fixed to match the library
 * extensions are compiled against — reordering or adding/removing a parameter changes the
 * Kotlin-generated `<init>$default` and `copy$default` descriptors and breaks every lib-16 source
 * with a NoSuchMethodError. Legacy (lib 14) binaries are supported through the secondary
 * constructors, the deprecated `quality`/`url` accessors, and the hidden legacy `copy` below.
 */
data class Video(
    var videoUrl: String = "",
    val videoTitle: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: Headers? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),
    val mpvArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegStreamArgs: List<Pair<String, String>> = emptyList(),
    val ffmpegVideoArgs: List<Pair<String, String>> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
) {
    // Deliberately NOT a primary-constructor parameter: keeping it out preserves the exact lib-16
    // constructor/copy synthetics. Public (upstream hides it behind `url`) because NyanTV reads
    // videoPageUrl directly in its player code.
    var videoPageUrl: String = ""

    @Transient
    @Volatile
    var status: State = State.QUEUE

    // ── Legacy (extensions-lib 14) binary compatibility ──────────────────────────────────────────

    @Deprecated("Use videoTitle instead", ReplaceWith("videoTitle"))
    val quality: String
        get() = videoTitle

    @Deprecated("Use videoPageUrl instead", ReplaceWith("videoPageUrl"))
    val url: String
        get() = videoPageUrl

    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        headers: Headers? = null,
        subtitleTracks: List<Track> = emptyList(),
        audioTracks: List<Track> = emptyList(),
    ) : this(
        videoUrl = videoUrl ?: "null",
        videoTitle = quality,
        headers = headers,
        subtitleTracks = subtitleTracks,
        audioTracks = audioTracks,
    ) {
        videoPageUrl = url
    }

    @Suppress("UNUSED_PARAMETER")
    constructor(
        url: String,
        quality: String,
        videoUrl: String?,
        uri: Uri? = null,
        headers: Headers? = null,
    ) : this(url, quality, videoUrl, headers)

    // Legacy lib-14 copy(url, quality, videoUrl, ...). HIDDEN so it stays callable from old extension
    // binaries but is invisible to Kotlin source and doesn't clash with the data class copy().
    @Deprecated("Use the data class copy with videoUrl/videoTitle", level = DeprecationLevel.HIDDEN)
    fun copy(
        url: String,
        quality: String,
        videoUrl: String?,
        headers: Headers? = this.headers,
        subtitleTracks: List<Track> = this.subtitleTracks,
        audioTracks: List<Track> = this.audioTracks,
    ): Video = Video(url, quality, videoUrl, headers, subtitleTracks, audioTracks)

    enum class State {
        QUEUE,
        LOAD_VIDEO,
        READY,
        ERROR,
    }
}

@kotlinx.serialization.Serializable
data class SerializableVideo(
    val videoUrl: String = "",
    val videoTitle: String = "",
    val resolution: Int? = null,
    val bitrate: Int? = null,
    val headers: List<Pair<String, String>>? = null,
    val preferred: Boolean = false,
    val subtitleTracks: List<Track> = emptyList(),
    val audioTracks: List<Track> = emptyList(),
    val timestamps: List<TimeStamp> = emptyList(),
    val internalData: String = "",
    val initialized: Boolean = false,
    val videoPageUrl: String = "",
) {

    companion object {
        fun List<Video>.serialize(): String =
            Json.encodeToString(
                this.map { vid ->
                    SerializableVideo(
                        vid.videoUrl,
                        vid.videoTitle,
                        vid.resolution,
                        vid.bitrate,
                        vid.headers?.toList(),
                        vid.preferred,
                        vid.subtitleTracks,
                        vid.audioTracks,
                        vid.timestamps,
                        vid.internalData,
                        vid.initialized,
                        vid.videoPageUrl,
                    )
                },
            )

        fun String.toVideoList(): List<Video> =
            Json.decodeFromString<List<SerializableVideo>>(this)
                .map { sVid ->
                    Video(
                        videoUrl = sVid.videoUrl,
                        videoTitle = sVid.videoTitle,
                        resolution = sVid.resolution,
                        bitrate = sVid.bitrate,
                        headers = sVid.headers
                            ?.flatMap { it.toList() }
                            ?.let { Headers.headersOf(*it.toTypedArray()) },
                        preferred = sVid.preferred,
                        subtitleTracks = sVid.subtitleTracks,
                        audioTracks = sVid.audioTracks,
                        timestamps = sVid.timestamps,
                        internalData = sVid.internalData,
                        initialized = sVid.initialized,
                    ).also { it.videoPageUrl = sVid.videoPageUrl }
                }
    }
}
