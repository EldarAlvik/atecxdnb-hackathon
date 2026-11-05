package no.scamguardian.app.board

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigRulesTest {
    private val ok = BoardConfig()

    @Test
    fun defaultConfigIsValid() = assertTrue(ConfigRules.validate(ok).isEmpty())

    @Test
    fun rangesFromTheProtocol() {
        assertTrue(ConfigRules.validate(ok.copy(micGain = 1)).isEmpty())
        assertTrue(ConfigRules.validate(ok.copy(micGain = 64)).isEmpty())
        assertEquals(setOf("mic_gain"), ConfigRules.validate(ok.copy(micGain = 0)).keys)
        assertEquals(setOf("mic_gain"), ConfigRules.validate(ok.copy(micGain = 65)).keys)
        assertEquals(setOf("ai_interval_s"), ConfigRules.validate(ok.copy(aiIntervalS = 2)).keys)
        assertEquals(setOf("ai_interval_s"), ConfigRules.validate(ok.copy(aiIntervalS = 61)).keys)
        assertTrue(ConfigRules.validate(ok.copy(aiIntervalS = 3)).isEmpty())
        assertEquals(setOf("screen_brightness"), ConfigRules.validate(ok.copy(screenBrightness = 9)).keys)
        assertEquals(setOf("screen_brightness"), ConfigRules.validate(ok.copy(screenBrightness = 101)).keys)
        assertEquals(setOf("language"), ConfigRules.validate(ok.copy(language = "sv")).keys)
        assertEquals(setOf("events"), ConfigRules.validate(ok.copy(events = listOf("guard", "guard"))).keys)
        assertEquals(setOf("mic_gain", "language"), ConfigRules.validate(ok.copy(micGain = 100, language = "")).keys)
    }

    @Test
    fun patchHasOnlyChangedKeys() {
        assertTrue(ConfigRules.patch(ok, ok).isEmpty())
        val p = ConfigRules.patch(ok, ok.copy(micGain = 12, redLatch = false))
        assertEquals(setOf("mic_gain", "red_latch"), p.keys)
        assertEquals(JsonPrimitive(12), p["mic_gain"])
        assertEquals(JsonPrimitive(false), p["red_latch"])
        // The same keys in another order are not a change.
        assertTrue(ConfigRules.patch(ok, ok.copy(events = ok.events.reversed())).isEmpty())
        val ev = ConfigRules.patch(ok, ok.copy(events = listOf("verdict")))
        assertEquals(JsonArray(listOf(JsonPrimitive("verdict"))), ev["events"])
    }

    @Test
    fun toggleEventKeepsOrderAndUnknownKeys() {
        val off = ConfigRules.toggleEvent(ConfigRules.DEFAULT_EVENT_KEYS + "ws_msg", "health", false)
        assertTrue("health" !in off)
        assertEquals("ws_msg", off.last())
        assertEquals(listOf("guard", "verdict"), ConfigRules.toggleEvent(listOf("verdict"), "guard", true))
    }
}

class WordRulesTest {
    @Test
    fun normalisesLikeTheFirmware() {
        assertEquals("blå elefant", WordRules.normalise("Blå Elefant!"))
        assertEquals(WordRules.normalise("blå elefant"), WordRules.normalise("  BLÅ,   elefant?? "))
        assertEquals("ærlig øl", WordRules.normalise("Ærlig-Øl"))
        assertEquals("red fox 42", WordRules.normalise("Red fox #42"))
    }

    @Test
    fun wordLimits() {
        assertNull(WordRules.checkWord("blue elephant"))
        assertNull(WordRules.checkWord("one two three four"))
        assertTrue(WordRules.checkWord("one two three four five") != null)
        assertTrue(WordRules.checkWord("ab") != null)
        assertNull(WordRules.checkWord("abc"))
        assertTrue(WordRules.checkWord("!!!") != null)
        assertTrue(WordRules.checkWord("a".repeat(49)) != null)
        assertNull(WordRules.checkWord("a".repeat(48)))
        // æøå are two bytes each in UTF-8.
        assertTrue(WordRules.checkWord("å".repeat(25)) != null)
        assertNull(WordRules.checkWord("å".repeat(24)))
    }

    @Test
    fun nameLimits() {
        assertNull(WordRules.checkName("Mom"))
        assertTrue(WordRules.checkName("  ") != null)
        assertTrue(WordRules.checkName("x".repeat(25)) != null)
        assertNull(WordRules.checkName("ø".repeat(12)))
        assertTrue(WordRules.checkName("ø".repeat(13)) != null)
    }
}
