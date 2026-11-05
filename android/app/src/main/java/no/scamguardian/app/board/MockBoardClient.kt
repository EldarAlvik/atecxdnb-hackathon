package no.scamguardian.app.board

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * An in-process fake board for tests, previews and the "simulated board" debug setting. It follows
 * the same rules as `mock/mock_board.py`: each guard-on plays one scripted call (scam, ordinary,
 * family call with a security word, in turn). Words are only checked, never kept: a member only
 * remembers that it has one.
 */
class MockBoardClient(
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val pace: Long = 1000,
) : BoardClient {
    private val lock = Mutex()
    private var seq = 0L
    private val ring = ArrayDeque<BoardEvent>()
    private val live = MutableSharedFlow<BoardEvent>(extraBufferCapacity = 256)
    private var guarding = false
    private var verdict = Verdict.NONE
    private var reason = ""
    private var latched = false
    private var transcript = ""
    private var wordMatch: WordMatch? = null
    private var stt = "closed"
    private var aiCalls = 0
    private var config = BoardConfig()
    private val members = linkedMapOf<Int, Member>()
    private var nextId = 1
    private var scriptIndex = 0
    private var callJob: Job? = null
    private val started = clock()

    init {
        emit(EventKeys.STARTUP, obj("fw" to "2.0.0-sim", "reason" to "POWERON", "mode" to "normal"))
        emit(EventKeys.WIFI, obj("status" to "connected", "code" to 3, "ip" to "192.168.0.57", "rssi" to -52, "source" to "saved"))
        scope.launch {
            while (isActive) {
                delay(if (guarding) 5 * pace else 30 * pace)
                lock.withLock {
                    emit(EventKeys.HEALTH, obj("guarding" to guarding, "free_heap" to 150000, "ws_clients" to 1), "sensor")
                }
            }
        }
    }

    private fun emit(key: String, value: JsonElement, type: String = "state") {
        seq += 1
        if (key !in config.events && key != EventKeys.STARTUP) return
        val ev = BoardEvent(seq, clock(), key, type, value)
        if (ev.isLogged) {
            ring.addLast(ev)
            while (ring.size > 500) ring.removeFirst()
        }
        live.tryEmit(ev)
    }

    override suspend fun status() = lock.withLock {
        BoardStatus(
            device = DEVICE, fw = "2.0.0-sim", proto = PROTOCOL_VERSION, uptimeS = clock() - started, time = clock(),
            guarding = guarding, verdict = verdict, reason = reason, latched = latched,
            transcript = transcript.takeLast(300), wordMatch = wordMatch,
            wifi = WifiInfo("SimNet", -52, "192.168.0.57", "saved"), stt = stt,
            ai = AiInfo(aiCalls, 1430, if (aiCalls > 0) 200 else 0), heap = HeapInfo(151230, 98012, 69620),
            lastSeq = seq, members = members.size,
        )
    }

    override suspend fun setGuard(on: Boolean): Boolean = lock.withLock {
        if (on != guarding) {
            guarding = on
            if (on) {
                verdict = Verdict.NONE; reason = ""; latched = false; transcript = ""; wordMatch = null
                emit(EventKeys.GUARD, JsonPrimitive("on"))
                val script = SCRIPTS[scriptIndex++ % SCRIPTS.size]
                callJob = scope.launch { play(script) }
            } else {
                callJob?.cancel()
                emit(EventKeys.GUARD, JsonPrimitive("off"))
                stt = "closed"
                emit(EventKeys.WS, JsonPrimitive("closed"))
            }
        }
        guarding
    }

    private suspend fun play(script: List<Line>) {
        delay(pace)
        lock.withLock { stt = "connecting"; emit(EventKeys.WS, JsonPrimitive("connecting")) }
        delay(pace)
        lock.withLock { stt = "connected"; emit(EventKeys.WS, JsonPrimitive("connected")) }
        for (line in script) {
            delay(3 * pace)
            lock.withLock {
                if (!guarding) return
                transcript = (transcript + " " + line.text).trim()
                emit(EventKeys.TRANSCRIPT, obj("text" to line.text, "lang" to line.lang))
                if (line.wordMatch) {
                    members.values.firstOrNull { it.hasWord }?.let {
                        wordMatch = WordMatch(it.id, it.name)
                        emit(EventKeys.WORD_MATCH, obj("member_id" to it.id, "member" to it.name))
                    }
                }
            }
            val v = line.verdict ?: continue
            delay(pace)
            lock.withLock {
                if (!guarding || latched) return@withLock
                aiCalls++
                verdict = v.first; reason = v.second
                if (v.first == Verdict.SCAM && config.redLatch) latched = true
                emit(
                    EventKeys.VERDICT,
                    obj("verdict" to v.first, "reason" to v.second, "latched" to latched, "http" to 200, "ms" to 1430,
                        "chars" to transcript.length, "word_match" to (wordMatch != null)),
                )
            }
        }
    }

    override suspend fun events(since: Long, limit: Int): EventsPage = lock.withLock {
        val newer = ring.filter { it.seq > since }
        val page = newer.take(limit.coerceIn(1, 200))
        EventsPage(
            events = page.map { it.toEnvelope() },
            lastSeq = seq,
            oldestSeq = ring.firstOrNull()?.seq ?: (seq + 1),
            more = newer.size > page.size,
        )
    }

    override suspend fun config() = lock.withLock { config }

    override suspend fun updateConfig(patch: JsonObject): BoardConfig = lock.withLock {
        var c = config
        for ((k, v) in patch) {
            c = try {
                when (k) {
                    "mic_gain" -> c.copy(micGain = v.jsonPrimitive.int)
                    "ai_interval_s" -> c.copy(aiIntervalS = v.jsonPrimitive.int)
                    "language" -> c.copy(language = v.jsonPrimitive.content)
                    "red_latch" -> c.copy(redLatch = v.jsonPrimitive.boolean)
                    "events" -> c.copy(events = v.jsonArray.map { it.jsonPrimitive.content })
                    "screen_brightness" -> c.copy(screenBrightness = v.jsonPrimitive.int)
                    else -> throw BoardException.BadRequest("unknown setting $k")
                }
            } catch (e: BoardException) {
                throw e
            } catch (e: Exception) {
                throw BoardException.BadRequest("bad value for $k")
            }
        }
        ConfigRules.validate(c).entries.firstOrNull()?.let { throw BoardException.BadRequest("bad value for ${it.key}") }
        if (c.events.any { it !in ConfigRules.DEFAULT_EVENT_KEYS }) throw BoardException.BadRequest("bad value for events")
        val changed = patch.keys.filter { k -> ConfigRules.patch(config, c).containsKey(k) }
        config = c
        if (changed.isNotEmpty()) emit(EventKeys.CONFIG, buildJsonObject { put("changed", JsonArray(changed.map(::JsonPrimitive))) })
        config
    }

    override suspend fun members() = lock.withLock { WordsList(members.values.toList(), WordRules.MAX_MEMBERS) }

    override suspend fun addMember(name: String, word: String?): Member = lock.withLock {
        WordRules.checkName(name)?.let { throw BoardException.BadRequest(it) }
        if (members.size >= WordRules.MAX_MEMBERS) throw BoardException.BadRequest("at most 8 members")
        if (!word.isNullOrEmpty()) WordRules.checkWord(word)?.let { throw BoardException.BadRequest(it) }
        val m = Member(nextId++, name.trim(), !word.isNullOrEmpty(), clock())
        members[m.id] = m
        emit(EventKeys.WORDS, obj("action" to "add", "id" to m.id, "name" to m.name))
        m
    }

    private fun member(id: Int) = members[id] ?: throw BoardException.NotFound("no such member")

    override suspend fun renameMember(id: Int, name: String): Member = lock.withLock {
        WordRules.checkName(name)?.let { throw BoardException.BadRequest(it) }
        val m = member(id).copy(name = name.trim(), updated = clock())
        members[id] = m
        emit(EventKeys.WORDS, obj("action" to "rename", "id" to id, "name" to m.name))
        m
    }

    override suspend fun setWord(id: Int, word: String): Member = lock.withLock {
        WordRules.checkWord(word)?.let { throw BoardException.BadRequest(it) }
        val m = member(id).copy(hasWord = true, updated = clock())
        members[id] = m
        emit(EventKeys.WORDS, obj("action" to "set", "id" to id, "name" to m.name))
        m
    }

    override suspend fun clearWord(id: Int): Member = lock.withLock {
        val m = member(id).copy(hasWord = false, updated = clock())
        members[id] = m
        emit(EventKeys.WORDS, obj("action" to "clear", "id" to id, "name" to m.name))
        m
    }

    override suspend fun deleteMember(id: Int) = lock.withLock {
        val m = member(id)
        members.remove(id)
        emit(EventKeys.WORDS, obj("action" to "remove", "id" to id, "name" to m.name))
    }

    override suspend fun startSetup() {
        throw BoardException.BadRequest("The simulated board has no Bluetooth setup")
    }

    override fun live(): Flow<LiveMessage> = flow {
        emit(LiveMessage.Hello(DEVICE, "2.0.0-sim", PROTOCOL_VERSION, lock.withLock { seq }))
        live.collect { emit(LiveMessage.Event(it)) }
    }

    private data class Line(val text: String, val lang: String, val verdict: Pair<String, String>? = null, val wordMatch: Boolean = false)

    companion object {
        const val DEVICE = "guardian-sim0"

        private val SCRIPTS = listOf(
            listOf(
                Line("Hei, dette er Kari fra sikkerhetsavdelingen i banken.", "no"),
                Line("Vi har oppdaget mistenkelig aktivitet på kontoen din.", "no", Verdict.UNSURE to "Bank claims account issue"),
                Line("For å sikre pengene dine må du bekrefte med BankID nå.", "no", Verdict.SCAM to "Ber om BankID-kode, typisk svindel"),
                Line("Ikke fortell dette til noen, det haster.", "no"),
            ),
            listOf(
                Line("Hi, it's Anna from the dentist.", "en"),
                Line("I'm calling to confirm your appointment next Tuesday at ten.", "en", Verdict.SAFE to "Ordinary appointment reminder"),
            ),
            listOf(
                Line("Hei, det er meg, jeg har mistet telefonen min.", "no", Verdict.UNSURE to "Ukjent nummer, sier det er familie"),
                Line("[family word], du vet det er meg.", "no", wordMatch = true),
                Line("Kan du ringe meg tilbake i kveld?", "no", Verdict.SAFE to "Familieord bekreftet, vanlig samtale"),
            ),
        )

        private fun obj(vararg pairs: Pair<String, Any>): JsonObject = buildJsonObject {
            for ((k, v) in pairs) put(
                k,
                when (v) {
                    is String -> JsonPrimitive(v)
                    is Number -> JsonPrimitive(v)
                    is Boolean -> JsonPrimitive(v)
                    else -> JsonPrimitive(v.toString())
                },
            )
        }
    }
}

fun BoardEvent.toEnvelope(): JsonObject = buildJsonObject {
    put("type", JsonPrimitive("event"))
    put("payload", buildJsonObject {
        put("event_type", JsonPrimitive(eventType))
        put("key", JsonPrimitive(key))
        put("value", value)
        put("source", JsonPrimitive(source))
        put("seq", JsonPrimitive(seq))
        put("ts", JsonPrimitive(ts))
    })
}
