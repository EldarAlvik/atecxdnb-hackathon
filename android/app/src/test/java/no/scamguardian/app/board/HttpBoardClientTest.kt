package no.scamguardian.app.board

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The real client against MockWebServer on 127.0.0.1 (Robolectric for OkHttp's Android platform). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HttpBoardClientTest {
    private val server = MockWebServer()
    private lateinit var client: HttpBoardClient

    @Before
    fun setUp() {
        server.start()
        client = HttpBoardClient(BoardEndpoint("127.0.0.1", server.port, server.port, "tok123"), OkHttpClient())
    }

    @After
    fun tearDown() = server.close()

    private fun json(body: String, code: Int = 200) =
        MockResponse.Builder().code(code).addHeader("Content-Type", "application/json").body(body).build()

    @Test
    fun sendsBearerTokenAndParsesStatus() = runBlocking {
        server.enqueue(json("""{"device":"guardian-ab12","guarding":true,"verdict":"safe","last_seq":7}"""))
        val s = client.status()
        assertEquals("guardian-ab12", s.device)
        val req = server.takeRequest()
        assertEquals("Bearer tok123", req.headers["Authorization"])
        assertEquals("/status", req.url.encodedPath)
        // Requests always name the fixed host; LocalDns maps it to 127.0.0.1.
        assertEquals("${LocalDns.BOARD_HOST}:${server.port}", req.headers["Host"])
    }

    @Test
    fun eventsQueryAndWordBodies() = runBlocking {
        server.enqueue(json("""{"events":[],"last_seq":5,"oldest_seq":1,"more":false}"""))
        client.events(42, 200)
        assertEquals("/events?since=42&limit=200", server.takeRequest().target)

        server.enqueue(json("""{"id":1,"name":"Mom","has_word":true,"updated":1}"""))
        client.setWord(1, "blue elephant")
        val req = server.takeRequest()
        assertEquals("POST", req.method)
        assertEquals("""{"id":1,"word":"blue elephant"}""", req.body!!.utf8())

        server.enqueue(json("""{"id":1,"name":"Mom","has_word":false,"updated":1}"""))
        client.clearWord(1)
        assertEquals("""{"id":1,"clear":true}""", server.takeRequest().body!!.utf8())

        server.enqueue(json("""{"deleted":1}"""))
        client.deleteMember(1)
        val del = server.takeRequest()
        assertEquals("DELETE", del.method)
        assertEquals("/words?id=1", del.target)

        server.enqueue(json("""{"mic_gain":12,"ai_interval_s":6,"language":"multi","red_latch":true,"events":["verdict"],"screen_brightness":100}"""))
        val c = client.updateConfig(buildJsonObject { put("mic_gain", JsonPrimitive(12)) })
        assertEquals(12, c.micGain)
        assertEquals("""{"mic_gain":12}""", server.takeRequest().body!!.utf8())
    }

    @Test
    fun mapsErrors() = runBlocking {
        suspend fun expect(code: Int, body: String, check: (BoardException) -> Boolean) {
            server.enqueue(json(body, code))
            try {
                client.status()
                fail("expected an error for $code")
            } catch (e: BoardException) {
                assertTrue("$code -> $e", check(e))
            }
        }
        expect(401, """{"error":"unauthorized"}""") { it is BoardException.Unauthorized }
        expect(429, "{}") { it is BoardException.RateLimited }
        expect(400, """{"error":"bad value for mic_gain"}""") { it is BoardException.BadRequest && it.error == "bad value for mic_gain" }
        expect(404, """{"error":"no such member"}""") { it is BoardException.NotFound }
        expect(503, """{"error":"low_memory"}""") { it is BoardException.LowMemory }
    }

    @Test
    fun webSocketHelloThenEvents() = runBlocking {
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"hello","device":"guardian-ab12","fw":"2.0.0","proto":2,"last_seq":3}""")
                    webSocket.send("""{"type":"event","payload":{"event_type":"state","key":"guard","value":"on","seq":4,"ts":0}}""")
                }
            }).build(),
        )
        val msgs = client.live().take(2).toList()
        assertEquals(LiveMessage.Hello("guardian-ab12", "2.0.0", 2, 3), msgs[0])
        assertEquals("guard", (msgs[1] as LiveMessage.Event).event.key)
        val req = server.takeRequest()
        // The board only reads the token from the query string.
        assertEquals("/?token=tok123", req.target)
        assertEquals("Bearer tok123", req.headers["Authorization"])
    }

    @Test
    fun webSocketUpgradeRefusedWith401() = runBlocking {
        server.enqueue(json("""{"error":"unauthorized"}""", 401))
        try {
            client.live().first()
            fail("expected Unauthorized")
        } catch (e: BoardException.Unauthorized) {
        }
    }

    @Test
    fun webSocketClosedBeforeHello() = runBlocking {
        server.enqueue(
            MockResponse.Builder().webSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.close(1013, "busy")
                }
            }).build(),
        )
        try {
            client.live().first()
            fail("expected Refused")
        } catch (e: BoardException.Refused) {
            assertEquals(1013, e.code)
        }
    }
}
