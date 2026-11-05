package no.scamguardian.app.board

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/**
 * One board event (protocol section 4), the same shape on USB serial, the WebSocket and /events:
 * `{"type":"event","payload":{"event_type":"state","key":"verdict","value":{...},"source":"guardian","seq":1832,"ts":1762352000}}`
 */
data class BoardEvent(
    val seq: Long,
    val ts: Long,
    val key: String,
    val eventType: String,
    val value: JsonElement,
    val source: String = "guardian",
) {
    val valueObject: JsonObject? get() = value as? JsonObject

    /** The value as text for string values (`guard`, `ws`, `error`, ...). */
    val valueText: String? get() = (value as? JsonPrimitive)?.takeIf { it.isString }?.content

    fun str(name: String): String? = valueObject?.get(name)?.let { (it as? JsonPrimitive)?.contentOrNull }
    fun int(name: String): Int? = valueObject?.get(name)?.let { (it as? JsonPrimitive)?.intOrNull }
    fun long(name: String): Long? = valueObject?.get(name)?.let { (it as? JsonPrimitive)?.longOrNull }
    fun bool(name: String): Boolean? = valueObject?.get(name)?.let { (it as? JsonPrimitive)?.booleanOrNull }

    /** `health` goes to the WebSocket but never to the board's event log (protocol section 4). */
    val isLogged: Boolean get() = key != EventKeys.HEALTH

    companion object {
        /** Parses an event envelope. Returns null for anything that is not a well-formed event. */
        fun parse(element: JsonElement): BoardEvent? {
            val root = element as? JsonObject ?: return null
            if (root["type"]?.jsonPrimitiveOrNull()?.contentOrNull != "event") return null
            val p = root["payload"] as? JsonObject ?: return null
            val key = p["key"]?.jsonPrimitiveOrNull()?.contentOrNull ?: return null
            val seq = p["seq"]?.jsonPrimitiveOrNull()?.longOrNull ?: return null
            return BoardEvent(
                seq = seq,
                ts = p["ts"]?.jsonPrimitiveOrNull()?.longOrNull ?: 0,
                key = key,
                eventType = p["event_type"]?.jsonPrimitiveOrNull()?.contentOrNull ?: "state",
                value = p["value"] ?: JsonNull,
                source = p["source"]?.jsonPrimitiveOrNull()?.contentOrNull ?: "guardian",
            )
        }

        fun parse(text: String): BoardEvent? =
            runCatching { BoardJson.parseToJsonElement(text) }.getOrNull()?.let(::parse)

        /** Parses one WebSocket text frame: `hello` or an event. Null for anything else. */
        fun parseLive(text: String): LiveMessage? {
            val el = runCatching { BoardJson.parseToJsonElement(text) }.getOrNull() as? JsonObject ?: return null
            return when (el["type"]?.jsonPrimitiveOrNull()?.contentOrNull) {
                "hello" -> LiveMessage.Hello(
                    device = el["device"]?.jsonPrimitiveOrNull()?.contentOrNull ?: "",
                    fw = el["fw"]?.jsonPrimitiveOrNull()?.contentOrNull ?: "",
                    proto = el["proto"]?.jsonPrimitiveOrNull()?.intOrNull ?: 0,
                    lastSeq = el["last_seq"]?.jsonPrimitiveOrNull()?.longOrNull ?: 0,
                )
                "event" -> parse(el)?.let { LiveMessage.Event(it) }
                else -> null
            }
        }
    }
}

private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

object EventKeys {
    const val STARTUP = "startup"
    const val GUARD = "guard"
    const val WIFI = "wifi"
    const val WS = "ws"
    const val TRANSCRIPT = "transcript"
    const val VERDICT = "verdict"
    const val WORD_MATCH = "word_match"
    const val HEALTH = "health"
    const val SETUP = "setup"
    const val CONFIG = "config"
    const val WORDS = "words"
    const val DEGRADED = "degraded"
    const val ERROR = "error"
    const val WS_MSG = "ws_msg"
    const val WIFI_RETRY = "wifi_retry"
}

/** A one-line title and an optional detail line for the event feed. */
data class EventText(val title: String, val detail: String? = null)

fun BoardEvent.describe(): EventText = when (key) {
    EventKeys.STARTUP -> EventText(
        "Board started (${str("mode") ?: "normal"} mode)",
        listOfNotNull(str("reason")?.let { "reason $it" }, str("fw")?.let { "fw $it" }).joinToString(", ").ifEmpty { null },
    )
    EventKeys.GUARD -> EventText(if (valueText == "on") "Guarding on" else "Guarding off")
    EventKeys.WIFI -> EventText(
        "Wi-Fi ${str("status") ?: "?"}",
        listOfNotNull(str("ip")?.takeIf { it.isNotEmpty() }, int("rssi")?.let { "$it dBm" }, str("source"))
            .joinToString(" · ").ifEmpty { null },
    )
    EventKeys.WS -> EventText("Speech-to-text ${valueText ?: "?"}")
    EventKeys.TRANSCRIPT -> EventText("Heard", str("text"))
    EventKeys.VERDICT -> EventText(
        "Verdict: ${verdictLabel(str("verdict") ?: "?")}" + if (bool("latched") == true) " (latched)" else "",
        listOfNotNull(str("reason")?.takeIf { it.isNotEmpty() }, str("detail")).joinToString(" · ").ifEmpty { null },
    )
    EventKeys.WORD_MATCH -> EventText("Family word heard: ${str("member") ?: "?"}")
    EventKeys.HEALTH -> EventText("Health", long("free_heap")?.let { "free heap $it" })
    EventKeys.SETUP -> EventText("Setup ${str("state") ?: "?"}", str("error"))
    EventKeys.CONFIG -> EventText(
        "Settings changed",
        (value as? JsonObject)?.get("changed")?.toString()?.trim('[', ']')?.replace("\"", ""),
    )
    EventKeys.WORDS -> EventText("Family words: ${str("action") ?: "?"} ${str("name") ?: ""}".trim())
    EventKeys.DEGRADED -> EventText(
        "Feature off: ${str("feature") ?: "?"}",
        listOfNotNull(str("reason"), long("free_heap")?.let { "free heap $it" }).joinToString(" · ").ifEmpty { null },
    )
    EventKeys.ERROR -> EventText("Error", valueText ?: value.toString())
    EventKeys.WS_MSG -> EventText("Speech-to-text message", valueText ?: value.toString())
    EventKeys.WIFI_RETRY -> EventText("Retrying Wi-Fi", valueText)
    else -> EventText(key, if (value is JsonNull) null else (valueText ?: value.toString()))
}

fun verdictLabel(verdict: String): String = when (verdict) {
    Verdict.NONE -> "No verdict yet"
    Verdict.SAFE -> "Safe"
    Verdict.UNSURE -> "Unsure"
    Verdict.SCAM -> "Scam"
    Verdict.AI_ERROR -> "AI error"
    else -> verdict
}

/** Parses the `value` of a stored event back from JSON text. */
fun parseValueJson(text: String): JsonElement =
    runCatching { BoardJson.parseToJsonElement(text) }.getOrDefault(JsonNull)

