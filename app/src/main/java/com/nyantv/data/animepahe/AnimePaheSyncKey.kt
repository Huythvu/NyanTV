package com.nyantv.data.animepahe

import java.security.MessageDigest

/**
 * Derives the Firestore document id for an AnimePahe Watchlist sync phrase.
 *
 * This must stay byte-for-byte identical to the companion Chrome extension's `syncKeyToDocumentId`
 * (sync.js): normalize the 5-word phrase exactly the same way, SHA-256 the UTF-8 bytes, hex-encode,
 * and prefix with `sync_`. Any divergence yields a different id and reads/writes the wrong document.
 */
object AnimePaheSyncKey {

    /** The extension's SYNC_WORDS list — the only valid words in a phrase. */
    val SYNC_WORDS: Set<String> = setOf(
        "mango", "tiger", "cloud", "ramen", "orbit",
        "river", "pixel", "storm", "melon", "paper",
        "toast", "lemon", "panda", "ocean", "berry",
        "comet", "pearl", "sunny", "yuzu", "apple",
        "coral", "ember", "frost", "hazel", "jelly",
        "kiwi", "lotus", "maple", "night", "olive",
        "peach", "quiet", "rain", "snow", "tea",
        "umber", "wave", "xenon", "zen", "fox",
        "moon", "star", "candy", "dango", "echo",
        "flame", "glow", "honey", "iris", "jade",
        "koala", "lime", "mist", "nova", "onyx",
        "plum", "rose", "shell", "tulip", "unity",
        "zebra", "acorn", "dream", "eagle", "ink",
        "karma", "neon", "ruby", "yarn", "dawn",
        "dusk", "fern", "cove", "grove", "cedar",
    )

    /** Mirror of the extension's `normalizeSyncKey`. Do not "improve" it — it must match exactly. */
    fun normalize(phrase: String): String =
        phrase.trim().lowercase()
            .replace(Regex("[^a-z\\s-]"), "")
            .replace("-", " ")
            .replace(Regex("\\s+"), " ")

    /** True when [phrase] normalizes to exactly 5 known words (mirror of the extension's validation). */
    fun isValid(phrase: String): Boolean {
        val words = normalize(phrase).split(" ").filter { it.isNotEmpty() }
        return words.size == 5 && words.all { it in SYNC_WORDS }
    }

    /** `sync_<sha256hex(normalized phrase)>`, matching the extension. */
    fun documentId(phrase: String): String {
        val normalized = normalize(phrase)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02x".format(it) }
        return "sync_$hex"
    }
}
