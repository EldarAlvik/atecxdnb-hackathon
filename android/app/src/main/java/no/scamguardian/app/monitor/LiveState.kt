package no.scamguardian.app.monitor

import no.scamguardian.app.board.BoardEvent
import no.scamguardian.app.board.BoardStatus
import no.scamguardian.app.board.EventKeys
import no.scamguardian.app.board.Verdict
import no.scamguardian.app.board.WifiInfo
import no.scamguardian.app.board.WordMatch

enum class Connection {
    /** No board paired, or the monitor is not running. */
    IDLE,

    /** WebSocket open and `hello` received. */
    LIVE,

    /** The board answers recently, but the WebSocket is down (reconnecting; /status is polled meanwhile). */
    RECONNECTING,

    /** No answer from the board for [BoardMonitor.OFFLINE_AFTER_MS]. */
    OFFLINE,

    /** The board refuses this phone's token (it was paired again with another phone). */
    UNAUTHORIZED,
}

/** What the dashboard shows. Built from /status and kept current by WebSocket events. */
data class LiveState(
    val known: Boolean = false,
    val device: String = "",
    val fw: String = "",
    val guarding: Boolean = false,
    val verdict: String = Verdict.NONE,
    val reason: String = "",
    val latched: Boolean = false,
    val transcript: String = "",
    val wordMatch: WordMatch? = null,
    val wifi: WifiInfo? = null,
    val stt: String = "closed",
    val degraded: List<String> = emptyList(),
    val lastSeq: Long = 0,
    val members: Int = 0,
) {
    fun withStatus(s: BoardStatus) = LiveState(
        known = true, device = s.device, fw = s.fw, guarding = s.guarding, verdict = s.verdict,
        reason = s.reason, latched = s.latched, transcript = s.transcript, wordMatch = s.wordMatch,
        wifi = s.wifi, stt = s.stt, degraded = s.degraded, lastSeq = maxOf(lastSeq, s.lastSeq), members = s.members,
    )

    /** Applies one live event the same way the board changes its own state. */
    fun apply(e: BoardEvent): LiveState {
        val next = copy(lastSeq = maxOf(lastSeq, e.seq))
        return when (e.key) {
            EventKeys.GUARD -> if (e.valueText == "on") {
                next.copy(guarding = true, verdict = Verdict.NONE, reason = "", latched = false, transcript = "", wordMatch = null)
            } else {
                next.copy(guarding = false, latched = false, stt = "closed")
            }
            EventKeys.VERDICT -> next.copy(
                verdict = e.str("verdict") ?: next.verdict,
                reason = e.str("reason") ?: "",
                latched = e.bool("latched") ?: false,
            )
            EventKeys.TRANSCRIPT -> {
                val text = e.str("text").orEmpty()
                if (text.isEmpty()) next else next.copy(transcript = (next.transcript + " " + text).trim().takeLast(TRANSCRIPT_CHARS))
            }
            EventKeys.WORD_MATCH -> next.copy(wordMatch = WordMatch(e.int("member_id") ?: 0, e.str("member").orEmpty()))
            EventKeys.WS -> next.copy(
                stt = when (e.valueText) {
                    "connected" -> "connected"
                    "connecting" -> "connecting"
                    else -> "closed"
                },
            )
            EventKeys.WIFI -> {
                val old = next.wifi ?: WifiInfo()
                next.copy(
                    wifi = old.copy(
                        rssi = e.int("rssi") ?: old.rssi,
                        ip = e.str("ip") ?: old.ip,
                        source = e.str("source") ?: old.source,
                        ssid = if (e.str("status") == "connected") old.ssid else "",
                    ),
                )
            }
            EventKeys.DEGRADED -> {
                val f = e.str("feature") ?: return next
                if (f in next.degraded) next else next.copy(degraded = next.degraded + f)
            }
            EventKeys.HEALTH -> next.copy(guarding = e.bool("guarding") ?: next.guarding)
            else -> next
        }
    }

    companion object {
        /** The board's /status keeps the last ~300 characters. */
        const val TRANSCRIPT_CHARS = 300
    }
}

/** Alerts to raise when the state moves from [before] to [after]. */
sealed interface Alert {
    data class Scam(val reason: String) : Alert
    data class WordHeard(val member: String) : Alert
}

fun alertsFor(before: LiveState?, after: LiveState): List<Alert> = buildList {
    if (!after.known) return@buildList
    // On the first status after the app starts, a leftover verdict from a finished call is old news.
    if (before == null && !after.guarding) return@buildList
    if (after.verdict == Verdict.SCAM && before?.verdict != Verdict.SCAM) add(Alert.Scam(after.reason))
    val wm = after.wordMatch
    if (wm != null && wm != before?.wordMatch) add(Alert.WordHeard(wm.member))
}
