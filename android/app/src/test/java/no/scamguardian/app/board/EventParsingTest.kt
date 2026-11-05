package no.scamguardian.app.board

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EventParsingTest {
    private val verdictLine =
        """{"type":"event","payload":{"event_type":"state","key":"verdict","value":{"verdict":"scam","reason":"Asks for BankID","latched":true,"http":200,"ms":1430,"chars":512,"word_match":false},"source":"guardian","seq":1832,"ts":1762352000}}"""

    @Test
    fun parsesVerdictEvent() {
        val e = BoardEvent.parse(verdictLine)!!
        assertEquals(1832, e.seq)
        assertEquals(1762352000, e.ts)
        assertEquals("verdict", e.key)
        assertEquals("state", e.eventType)
        assertEquals("scam", e.str("verdict"))
        assertEquals(true, e.bool("latched"))
        assertEquals(1430, e.int("ms"))
        assertEquals("Verdict: Scam (latched)", e.describe().title)
        assertEquals("Asks for BankID", e.describe().detail)
    }

    @Test
    fun parsesStringValuesAndUnknownKeys() {
        val guard = BoardEvent.parse("""{"type":"event","payload":{"event_type":"state","key":"guard","value":"on","seq":5,"ts":0,"extra":1}}""")!!
        assertEquals("on", guard.valueText)
        assertEquals("Guarding on", guard.describe().title)
        val future = BoardEvent.parse("""{"type":"event","payload":{"key":"new_thing","value":{"a":1},"seq":6}}""")!!
        assertEquals("new_thing", future.key)
        assertEquals("state", future.eventType)
        assertEquals(0, future.ts)
    }

    @Test
    fun healthIsNotLogged() {
        val h = BoardEvent.parse("""{"type":"event","payload":{"event_type":"sensor","key":"health","value":{"free_heap":1},"seq":7,"ts":1}}""")!!
        assertFalse(h.isLogged)
        assertTrue(BoardEvent.parse(verdictLine)!!.isLogged)
    }

    @Test
    fun rejectsMalformed() {
        assertNull(BoardEvent.parse("not json"))
        assertNull(BoardEvent.parse("""{"type":"hello"}"""))
        assertNull(BoardEvent.parse("""{"type":"event","payload":{"key":"guard"}}"""))
        assertNull(BoardEvent.parse("""{"type":"event","payload":{"seq":1}}"""))
        assertNull(BoardEvent.parse("""[1,2]"""))
    }

    @Test
    fun parsesLiveMessages() {
        val hello = BoardEvent.parseLive("""{"type":"hello","device":"guardian-ab12","fw":"2.0.0","proto":2,"last_seq":1832}""")
        assertEquals(LiveMessage.Hello("guardian-ab12", "2.0.0", 2, 1832), hello)
        val ev = BoardEvent.parseLive(verdictLine) as LiveMessage.Event
        assertEquals(1832, ev.event.seq)
        assertNull(BoardEvent.parseLive("""{"type":"ping"}"""))
        assertNull(BoardEvent.parseLive("garbage"))
    }

    @Test
    fun envelopeRoundTrip() {
        val e = BoardEvent(9, 100, "guard", "state", JsonPrimitive("off"))
        assertEquals(e, BoardEvent.parse(e.toEnvelope()))
    }

    @Test
    fun decodesStatusAndPagesFromProtocolExamples() {
        val status = BoardJson.decodeFromString(
            BoardStatus.serializer(),
            """{"device":"guardian-ab12","fw":"2.0.0","proto":2,"uptime_s":5120,"time":1762352000,"guarding":true,
              "verdict":"unsure","reason":"Too little context","latched":false,"transcript":"hei","word_match":null,
              "wifi":{"ssid":"Home","rssi":-52,"ip":"192.168.0.57","source":"saved"},"stt":"connected",
              "ai":{"calls":12,"last_ms":1430,"last_http":200},"heap":{"free":151230,"min":98012,"max_block":69620},
              "degraded":[],"last_seq":1832,"members":3}""",
        )
        assertEquals("Home", status.wifi?.ssid)
        assertEquals(1832, status.lastSeq)
        assertNull(status.wordMatch)

        val page = BoardJson.decodeFromString(
            EventsPage.serializer(),
            """{"events":[$verdictLine],"last_seq":1832,"oldest_seq":1333,"more":false}""",
        )
        assertEquals(1, page.events.size)
        assertEquals(1333, page.oldestSeq)
        assertEquals(1832, BoardEvent.parse(page.events[0])!!.seq)
    }
}
