package no.scamguardian.app.board

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** Ranges and allowed values for GET/POST /config (protocol section 2). */
object ConfigRules {
    val MIC_GAIN = 1..64
    val AI_INTERVAL_S = 3..60
    val SCREEN_BRIGHTNESS = 10..100
    val LANGUAGES = listOf("multi", "no", "en")

    /**
     * The `events` filter keys the app offers. These are the keys in the protocol's default
     * config; `startup`, `ws_msg` and `wifi_retry` are listed in section 4 but not in the default
     * `events` list (and the mock refuses them), so the app never adds them. Keys the board
     * returns that are not listed here are kept as they are.
     */
    val DEFAULT_EVENT_KEYS = listOf(
        "guard", "wifi", "ws", "transcript", "verdict", "health", "word_match",
        "setup", "config", "words", "degraded", "error",
    )

    /** Returns one message per bad key; empty means the config is valid. */
    fun validate(c: BoardConfig): Map<String, String> = buildMap {
        if (c.micGain !in MIC_GAIN) put("mic_gain", "Mic gain must be ${MIC_GAIN.first}-${MIC_GAIN.last}")
        if (c.aiIntervalS !in AI_INTERVAL_S) {
            put("ai_interval_s", "AI interval must be ${AI_INTERVAL_S.first}-${AI_INTERVAL_S.last} s")
        }
        if (c.language !in LANGUAGES) put("language", "Language must be one of ${LANGUAGES.joinToString()}")
        if (c.screenBrightness !in SCREEN_BRIGHTNESS) {
            put("screen_brightness", "Brightness must be ${SCREEN_BRIGHTNESS.first}-${SCREEN_BRIGHTNESS.last} %")
        }
        if (c.events.any { it.isBlank() }) put("events", "Empty event key")
        else if (c.events.size != c.events.toSet().size) put("events", "Duplicate event keys")
    }

    /** The POST /config body: only the keys that differ from [old]. Empty object means nothing to save. */
    fun patch(old: BoardConfig, new: BoardConfig): JsonObject = buildJsonObject {
        if (new.micGain != old.micGain) put("mic_gain", JsonPrimitive(new.micGain))
        if (new.aiIntervalS != old.aiIntervalS) put("ai_interval_s", JsonPrimitive(new.aiIntervalS))
        if (new.language != old.language) put("language", JsonPrimitive(new.language))
        if (new.redLatch != old.redLatch) put("red_latch", JsonPrimitive(new.redLatch))
        if (new.events.toSet() != old.events.toSet()) put("events", JsonArray(new.events.map(::JsonPrimitive)))
        if (new.screenBrightness != old.screenBrightness) put("screen_brightness", JsonPrimitive(new.screenBrightness))
    }

    /** Toggles one event key, keeping the order of the default list and any keys unknown to the app. */
    fun toggleEvent(events: List<String>, key: String, on: Boolean): List<String> {
        val set = events.toMutableSet()
        if (on) set += key else set -= key
        val known = DEFAULT_EVENT_KEYS.filter { it in set }
        return known + set.filter { it !in DEFAULT_EVENT_KEYS }.sorted()
    }
}

/** Client-side checks that mirror the firmware's rules for POST /words, so most mistakes never leave the phone. */
object WordRules {
    const val MAX_MEMBERS = 8

    /** Lower case (æøå too), letters and digits only, single spaces. Same as the firmware and mock. */
    fun normalise(text: String): String {
        val sb = StringBuilder()
        for (ch in text.lowercase()) {
            val o = ch.code
            val keep = ch in 'a'..'z' || ch in '0'..'9' || (o in 0xDF..0xFF && o != 0xF7)
            sb.append(if (keep) ch else ' ')
        }
        return sb.toString().split(' ').filter { it.isNotEmpty() }.joinToString(" ")
    }

    /** Null when fine, else a message. */
    fun checkName(name: String): String? {
        val n = name.trim().toByteArray(Charsets.UTF_8).size
        return if (n in 1..24) null else "Name must be 1-24 bytes"
    }

    /** Null when fine, else a message. Does not include the word in the message. */
    fun checkWord(word: String): String? {
        val norm = normalise(word)
        val words = if (norm.isEmpty()) 0 else norm.split(' ').size
        val bytes = norm.toByteArray(Charsets.UTF_8).size
        return when {
            words !in 1..4 -> "Use 1-4 words"
            bytes !in 3..48 -> "Use 3-48 letters (after removing punctuation)"
            else -> null
        }
    }
}
