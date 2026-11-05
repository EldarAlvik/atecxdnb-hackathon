package no.scamguardian.app.board

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wire models for docs/protocol.md, version 2. Unknown keys are ignored so newer firmware still parses. */
val BoardJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

const val PROTOCOL_VERSION = 2

/** Where the board's local API lives, plus the device token. */
data class BoardEndpoint(
    val host: String,
    val port: Int,
    val wsPort: Int,
    val token: String,
) {
    override fun toString() = "BoardEndpoint(host=$host, port=$port, wsPort=$wsPort, token=<redacted>)"
}

object Verdict {
    const val NONE = "none"
    const val SAFE = "safe"
    const val UNSURE = "unsure"
    const val SCAM = "scam"
    const val AI_ERROR = "ai_error"
}

@Serializable
data class WordMatch(
    @SerialName("member_id") val memberId: Int = 0,
    val member: String = "",
)

@Serializable
data class WifiInfo(
    val ssid: String = "",
    val rssi: Int = 0,
    val ip: String = "",
    val source: String = "",
)

@Serializable
data class AiInfo(
    val calls: Int = 0,
    @SerialName("last_ms") val lastMs: Int = 0,
    @SerialName("last_http") val lastHttp: Int = 0,
)

@Serializable
data class HeapInfo(
    val free: Long = 0,
    val min: Long = 0,
    @SerialName("max_block") val maxBlock: Long = 0,
)

@Serializable
data class BoardStatus(
    val device: String = "",
    val fw: String = "",
    val proto: Int = 0,
    @SerialName("uptime_s") val uptimeS: Long = 0,
    val time: Long = 0,
    val guarding: Boolean = false,
    val verdict: String = Verdict.NONE,
    val reason: String = "",
    val latched: Boolean = false,
    val transcript: String = "",
    @SerialName("word_match") val wordMatch: WordMatch? = null,
    val wifi: WifiInfo? = null,
    val stt: String = "closed",
    val ai: AiInfo? = null,
    val heap: HeapInfo? = null,
    val degraded: List<String> = emptyList(),
    @SerialName("last_seq") val lastSeq: Long = 0,
    val members: Int = 0,
)

@Serializable
data class GuardResponse(val guarding: Boolean)

@Serializable
data class EventsPage(
    val events: List<kotlinx.serialization.json.JsonElement> = emptyList(),
    @SerialName("last_seq") val lastSeq: Long = 0,
    @SerialName("oldest_seq") val oldestSeq: Long = 0,
    val more: Boolean = false,
)

@Serializable
data class BoardConfig(
    @SerialName("mic_gain") val micGain: Int = 8,
    @SerialName("ai_interval_s") val aiIntervalS: Int = 6,
    val language: String = "multi",
    @SerialName("red_latch") val redLatch: Boolean = true,
    val events: List<String> = ConfigRules.DEFAULT_EVENT_KEYS,
    @SerialName("screen_brightness") val screenBrightness: Int = 100,
)

@Serializable
data class Member(
    val id: Int,
    val name: String,
    @SerialName("has_word") val hasWord: Boolean = false,
    val updated: Long = 0,
)

@Serializable
data class WordsList(
    val members: List<Member> = emptyList(),
    @SerialName("max_members") val maxMembers: Int = 8,
)

@Serializable
data class ErrorBody(val error: String = "")

/** The board's first WebSocket message, or one event. */
sealed interface LiveMessage {
    data class Hello(val device: String, val fw: String, val proto: Int, val lastSeq: Long) : LiveMessage
    data class Event(val event: BoardEvent) : LiveMessage
}
