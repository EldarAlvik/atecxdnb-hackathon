package no.scamguardian.app.monitor

import no.scamguardian.app.board.BoardEvent
import no.scamguardian.app.board.BoardStatus
import no.scamguardian.app.board.WifiInfo
import no.scamguardian.app.board.WordMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveStateTest {
    private var seq = 100L
    private fun ev(key: String, value: String) =
        BoardEvent.parse("""{"type":"event","payload":{"event_type":"state","key":"$key","value":$value,"seq":${++seq},"ts":0}}""")!!

    private val base = LiveState().withStatus(
        BoardStatus(device = "guardian-ab12", guarding = false, verdict = "safe", wifi = WifiInfo("Home", -50, "1.2.3.4", "saved"), lastSeq = 100),
    )

    @Test
    fun guardOnResetsTheCall() {
        val s = base.copy(transcript = "old", wordMatch = WordMatch(1, "Mom")).apply(ev("guard", "\"on\""))
        assertTrue(s.guarding)
        assertEquals("none", s.verdict)
        assertEquals("", s.transcript)
        assertNull(s.wordMatch)
        assertEquals(101, s.lastSeq)
    }

    @Test
    fun aCallPlaysOut() {
        var s = base.apply(ev("guard", "\"on\""))
        s = s.apply(ev("ws", "\"connected\""))
        assertEquals("connected", s.stt)
        s = s.apply(ev("transcript", """{"text":"Hei, dette er banken.","lang":"no"}"""))
        s = s.apply(ev("transcript", """{"text":"Bekreft med BankID.","lang":"no"}"""))
        assertEquals("Hei, dette er banken. Bekreft med BankID.", s.transcript)
        s = s.apply(ev("verdict", """{"verdict":"scam","reason":"BankID","latched":true,"http":200}"""))
        assertEquals("scam", s.verdict)
        assertTrue(s.latched)
        s = s.apply(ev("guard", "\"off\""))
        assertFalse(s.guarding)
        assertFalse(s.latched)
        assertEquals("closed", s.stt)
    }

    @Test
    fun transcriptKeepsTheTail() {
        var s = base
        repeat(50) { s = s.apply(ev("transcript", """{"text":"ten chars."}""")) }
        assertEquals(LiveState.TRANSCRIPT_CHARS, s.transcript.length)
    }

    @Test
    fun wifiAndDegraded() {
        var s = base.apply(ev("wifi", """{"status":"connected","rssi":-70,"ip":"192.168.0.9","source":"fallback"}"""))
        assertEquals(-70, s.wifi?.rssi)
        assertEquals("fallback", s.wifi?.source)
        assertEquals("Home", s.wifi?.ssid)
        s = s.apply(ev("wifi", """{"status":"down","code":5}"""))
        assertEquals("", s.wifi?.ssid)
        s = s.apply(ev("degraded", """{"feature":"ws_server","reason":"low_heap"}"""))
        s = s.apply(ev("degraded", """{"feature":"ws_server","reason":"low_heap"}"""))
        assertEquals(listOf("ws_server"), s.degraded)
    }

    @Test
    fun alertsOnlyOnChange() {
        val scam = base.copy(verdict = "scam", reason = "BankID")
        assertEquals(listOf(Alert.Scam("BankID")), alertsFor(base, scam))
        assertTrue(alertsFor(scam, scam.copy(reason = "still")).isEmpty())
        // First status after start already red: alert if a call is being guarded, not for a finished one.
        assertEquals(listOf(Alert.Scam("BankID")), alertsFor(null, scam.copy(guarding = true)))
        assertTrue(alertsFor(null, scam).isEmpty())

        val word = base.copy(wordMatch = WordMatch(2, "Ola"))
        assertEquals(listOf(Alert.WordHeard("Ola")), alertsFor(base, word))
        assertTrue(alertsFor(word, word).isEmpty())
        assertTrue(alertsFor(null, LiveState(verdict = "scam")).isEmpty()) // nothing known yet
    }
}
