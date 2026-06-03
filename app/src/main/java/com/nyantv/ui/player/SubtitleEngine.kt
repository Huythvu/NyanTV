package com.nyantv.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Fetches a subtitle file (SRT or WebVTT) from a URL into RAM, parses it into
 * a list of [Cue] objects, and optionally translates each cue via Lingva.
 */
class SubtitleEngine {

    data class Cue(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    private var cues: List<Cue> = emptyList()

    val isLoaded: Boolean get() = cues.isNotEmpty()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /**
     * Fetch and parse subtitles.
     * @param url The subtitle URL
     * @param headers HTTP headers to send (e.g. mapOf("Referer" to "...", "User-Agent" to "..."))
     * @param translator Optional translator
     */
    suspend fun load(
        url: String,
        headers: Map<String, String> = emptyMap(),
        translator: LingvaTranslator? = null
    ) {
        val raw = withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder().url(url)
            headers.forEach { (name, value) ->
                requestBuilder.header(name, value)
            }
            val request = requestBuilder.build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            response.body?.string() ?: throw IOException("Empty response")
        }

        val parsed = parse(raw.trim())
        cues = if (translator != null) {
            parsed.map { cue ->
                val translated = runCatching { translator.translate(cue.text) }
                    .getOrDefault(cue.text)
                cue.copy(text = translated)
            }
        } else {
            parsed
        }
    }

    /** Returns the subtitle text active at [positionMs], or null if none. */
    fun currentCue(positionMs: Long): String? =
        cues.firstOrNull { positionMs in it.startMs..it.endMs }?.text

    fun clear() {
        cues = emptyList()
    }

    private fun parse(raw: String): List<Cue> =
        if (raw.startsWith("WEBVTT")) parseVtt(raw) else parseSrt(raw)

    private fun parseSrt(raw: String): List<Cue> {
        val blocks = raw.split(Regex("\n\\s*\n"))
        return blocks.mapNotNull { block ->
            val lines = block.trim().lines()
            val timeLine = lines.firstOrNull { "-->" in it } ?: return@mapNotNull null
            val timeIdx = lines.indexOf(timeLine)
            val parts = timeLine.split("-->", limit = 2)
            if (parts.size < 2) return@mapNotNull null
            val startMs = parseSrtTime(parts[0].trim())
            val endMs = parseSrtTime(parts[1].trim().substringBefore(' '))
            val text = lines.drop(timeIdx + 1).joinToString("\n").stripHtml().trim()
            if (text.isEmpty()) null else Cue(startMs, endMs, text)
        }
    }

    private fun parseSrtTime(t: String): Long {
        return runCatching {
            val (hms, ms) = t.split(",")
            val (h, m, s) = hms.split(":").map { it.trim().toLong() }
            h * 3_600_000L + m * 60_000L + s * 1_000L + ms.trim().toLong()
        }.getOrDefault(0L)
    }

    private fun parseVtt(raw: String): List<Cue> {
        val cues = mutableListOf<Cue>()
        val lines = raw.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if ("-->" in line) {
                val arrow = line.split("-->", limit = 2)
                val startMs = parseVttTime(arrow[0].trim())
                val endMs = parseVttTime(arrow[1].trim().split(Regex("\\s+")).first())
                val textLines = mutableListOf<String>()
                i++
                while (i < lines.size && lines[i].isNotBlank()) {
                    textLines.add(lines[i])
                    i++
                }
                val text = textLines.joinToString("\n").stripHtml().trim()
                if (text.isNotEmpty()) cues.add(Cue(startMs, endMs, text))
            } else {
                i++
            }
        }
        return cues
    }

    private fun parseVttTime(t: String): Long {
        return runCatching {
            val parts = t.split(":")
            when (parts.size) {
                3 -> {
                    val h = parts[0].toLong()
                    val m = parts[1].toLong()
                    val (s, ms) = parts[2].split(".")
                    h * 3_600_000L + m * 60_000L + s.toLong() * 1_000L + ms.padEnd(3, '0').take(3).toLong()
                }
                2 -> {
                    val m = parts[0].toLong()
                    val (s, ms) = parts[1].split(".")
                    m * 60_000L + s.toLong() * 1_000L + ms.padEnd(3, '0').take(3).toLong()
                }
                else -> 0L
            }
        }.getOrDefault(0L)
    }

    private fun String.stripHtml(): String = replace(Regex("<[^>]*>"), "")
}