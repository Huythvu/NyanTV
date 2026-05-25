package com.nyantv.ui.player

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Fetches a subtitle file (SRT or WebVTT) from a URL into RAM, parses it into
 * a list of [Cue] objects, and optionally translates each cue via Lingva.
 *
 * Everything happens in the UI process — no local HTTP server needed.
 * The player process only knows about the video/audio stream.
 *
 * Usage:
 *   val engine = SubtitleEngine()
 *   engine.load("https://…/subs.srt", translator = LingvaTranslator(targetLang = "de"))
 *   val text = engine.currentCue(playerPositionMs)   // call on every position update
 */
class SubtitleEngine {

    data class Cue(
        val startMs: Long,
        val endMs: Long,
        val text: String
    )

    private var cues: List<Cue> = emptyList()

    val isLoaded: Boolean get() = cues.isNotEmpty()

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Fetch and parse subtitles. Optionally translate all cues via [translator].
     * Suspend function — call from a coroutine (e.g. LaunchedEffect).
     */
    suspend fun load(url: String, translator: LingvaTranslator? = null) {
        val raw = withContext(Dispatchers.IO) { URL(url).readText(Charsets.UTF_8) }
        val parsed = parse(raw.trim())

        cues = if (translator != null) {
            // Translate all cues; failed translations fall back to original text
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

    fun clear() { cues = emptyList() }

    // ── Parsing ────────────────────────────────────────────────────────────────

    private fun parse(raw: String): List<Cue> =
        if (raw.startsWith("WEBVTT")) parseVtt(raw) else parseSrt(raw)

    // ── SRT ────────────────────────────────────────────────────────────────────
    //
    // Format:
    //   1
    //   00:00:01,000 --> 00:00:04,000
    //   Hello world

    private fun parseSrt(raw: String): List<Cue> {
        val blocks = raw.split(Regex("\n\\s*\n"))
        return blocks.mapNotNull { block ->
            val lines = block.trim().lines()
            val timeLine = lines.firstOrNull { "-->" in it } ?: return@mapNotNull null
            val timeIdx  = lines.indexOf(timeLine)

            val parts = timeLine.split("-->", limit = 2)
            if (parts.size < 2) return@mapNotNull null

            val startMs = parseSrtTime(parts[0].trim())
            val endMs   = parseSrtTime(parts[1].trim().substringBefore(' ')) // strip positioning tags

            val text = lines.drop(timeIdx + 1)
                .joinToString("\n")
                .stripHtml()
                .trim()

            if (text.isEmpty()) null else Cue(startMs, endMs, text)
        }
    }

    /** HH:MM:SS,mmm → milliseconds */
    private fun parseSrtTime(t: String): Long {
        return runCatching {
            val (hms, ms) = t.split(",")
            val (h, m, s) = hms.split(":").map { it.trim().toLong() }
            h * 3_600_000L + m * 60_000L + s * 1_000L + ms.trim().toLong()
        }.getOrDefault(0L)
    }

    // ── WebVTT ─────────────────────────────────────────────────────────────────
    //
    // Format:
    //   WEBVTT
    //
    //   00:00:01.000 --> 00:00:04.000
    //   Hello world

    private fun parseVtt(raw: String): List<Cue> {
        val cues  = mutableListOf<Cue>()
        val lines = raw.lines()
        var i     = 0

        while (i < lines.size) {
            val line = lines[i]
            if ("-->" in line) {
                val arrow = line.split("-->", limit = 2)
                val startMs = parseVttTime(arrow[0].trim())
                // Strip VTT cue settings (align:, position:, etc.)
                val endMs   = parseVttTime(arrow[1].trim().split(Regex("\\s+")).first())

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

    /**
     * Accepts both HH:MM:SS.mmm and MM:SS.mmm (WebVTT allows omitting hours).
     */
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

    // Strip basic HTML tags that appear in some VTT/SRT files (<b>, <i>, <c>, <ruby>, etc.)
    private fun String.stripHtml(): String =
        replace(Regex("<[^>]*>"), "")
}
