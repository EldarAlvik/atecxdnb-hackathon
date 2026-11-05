package no.scamguardian.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import no.scamguardian.app.board.BoardClient
import no.scamguardian.app.board.BoardEvent
import no.scamguardian.app.board.EventsPage
import no.scamguardian.app.board.toEnvelope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The board's event log: logged seqs in a ring, like /events. */
private class FakeLog(var lastSeq: Long = 0) : BoardClient {
    val ring = ArrayDeque<BoardEvent>()
    val calls = mutableListOf<Pair<Long, Int>>()

    fun add(vararg seqs: Long, key: String = "transcript") = seqs.forEach {
        ring.addLast(BoardEvent(it, 1000 + it, key, "state", JsonPrimitive("e$it")))
        lastSeq = maxOf(lastSeq, it)
    }

    override suspend fun events(since: Long, limit: Int): EventsPage {
        calls += since to limit
        val newer = ring.filter { it.seq > since }
        val page = newer.take(limit)
        return EventsPage(page.map { it.toEnvelope() }, lastSeq, ring.firstOrNull()?.seq ?: (lastSeq + 1), newer.size > page.size)
    }

    override suspend fun status() = error("unused")
    override suspend fun setGuard(on: Boolean) = error("unused")
    override suspend fun config() = error("unused")
    override suspend fun updateConfig(patch: JsonObject) = error("unused")
    override suspend fun members() = error("unused")
    override suspend fun addMember(name: String, word: String?) = error("unused")
    override suspend fun renameMember(id: Int, name: String) = error("unused")
    override suspend fun setWord(id: Int, word: String) = error("unused")
    override suspend fun clearWord(id: Int) = error("unused")
    override suspend fun deleteMember(id: Int) = error("unused")
    override suspend fun startSetup() = error("unused")
    override fun live(): Flow<no.scamguardian.app.board.LiveMessage> = emptyFlow()
}

class FakeEventStore : EventStore {
    var epoch = 0
    var cursor = 0L
    val events = sortedMapOf<Pair<Int, Long>, BoardEvent>(compareBy({ it.first }, { it.second }))
    val gaps = mutableListOf<Triple<Long, Long, String>>()

    override suspend fun state() = SyncState(epoch, cursor)
    override suspend fun saveSynced(epoch: Int, events: List<BoardEvent>, cursor: Long) {
        events.forEach { this.events.putIfAbsent(epoch to it.seq, it) }
        this.cursor = cursor
    }

    override suspend fun saveLive(event: BoardEvent) {
        if (event.isLogged) events.putIfAbsent(epoch to event.seq, event)
    }

    override suspend fun addGap(epoch: Int, fromSeq: Long, toSeq: Long, reason: String) {
        gaps += Triple(fromSeq, toSeq, reason)
    }

    override suspend fun newEpoch(): Int {
        epoch++
        cursor = 0
        return epoch
    }

    fun seqs() = events.keys.filter { it.first == epoch }.map { it.second }
}

class EventSyncerTest {
    private val store = FakeEventStore()
    private val log = FakeLog()
    private val syncer = EventSyncer(store, pageSize = 3)

    @Test
    fun firstSyncReadsEverythingAndMovesCursorToLastSeq() = runTest {
        log.add(1, 2, 4, 5) // 3 was a filtered/health event
        log.lastSeq = 7 // 6 and 7 were health events, not in the log
        val r = syncer.sync(log)
        assertEquals(listOf(1L, 2, 4, 5), store.seqs())
        assertEquals(4, r.added)
        assertEquals(7, store.cursor)
        assertTrue(store.gaps.isEmpty())
    }

    @Test
    fun pagesUntilMoreIsFalse() = runTest {
        log.add(1, 2, 3, 4, 5, 6, 7)
        syncer.sync(log)
        assertEquals((1L..7).toList(), store.seqs())
        assertEquals(listOf(0L to 3, 3L to 3, 6L to 3), log.calls)
        assertEquals(7, store.cursor)
    }

    @Test
    fun resumesFromCursorAfterReconnect() = runTest {
        log.add(1, 2)
        syncer.sync(log)
        log.add(3, 4)
        log.calls.clear()
        val r = syncer.sync(log)
        assertEquals(listOf(2L to 3), log.calls)
        assertEquals(2, r.added)
        assertEquals(4, store.cursor)
    }

    @Test
    fun liveEventsDoNotMoveTheCursor() = runTest {
        log.add(1, 2)
        syncer.sync(log)
        // While the app was away, 3 and 4 happened; on reconnect, 5 arrives live before the sync runs.
        log.add(3, 4, 5)
        store.saveLive(log.ring.last())
        assertEquals(2, store.cursor)
        syncer.sync(log)
        assertEquals(listOf(1L, 2, 3, 4, 5), store.seqs())
    }

    @Test
    fun notesGapWhenTheRingDroppedEvents() = runTest {
        log.add(1, 2)
        syncer.sync(log)
        // The board logged 3..20 while we were away, but the ring only kept 15..20.
        log.ring.clear()
        log.add(15, 16, 17, 18, 19, 20)
        val r = syncer.sync(log)
        assertEquals(listOf(Triple(3L, 14L, GapReason.LOST)), store.gaps)
        assertEquals(1, r.gaps)
        assertEquals(listOf(1L, 2, 15, 16, 17, 18, 19, 20), store.seqs())
    }

    @Test
    fun noGapWhenSinceIsJustBeforeOldest() = runTest {
        log.add(5, 6)
        store.cursor = 4
        syncer.sync(log)
        assertTrue(store.gaps.isEmpty())
    }

    @Test
    fun boardLogRestartStartsANewEpoch() = runTest {
        log.add(1, 2, 3, 4, 5)
        syncer.sync(log)
        val reset = FakeLog().apply { add(1, 2) }
        val r = syncer.sync(reset)
        assertTrue(r.restarted)
        assertEquals(1, store.epoch)
        assertEquals(listOf(1L, 2), store.seqs())
        assertEquals(GapReason.RESTARTED, store.gaps.single().third)
        assertEquals(2, store.cursor)
    }

    @Test
    fun ignoresHealthAndDuplicates() = runTest {
        log.add(1)
        log.add(2, key = "health") // the board never logs health; be safe if it appears
        log.add(3)
        syncer.sync(log)
        assertEquals(listOf(1L, 3), store.seqs())
        syncer.sync(log)
        assertEquals(listOf(1L, 3), store.seqs())
    }

    @Test
    fun lostRangeRules() {
        fun page(oldest: Long, last: Long, empty: Boolean = false) =
            EventsPage(if (empty) emptyList() else listOf(JsonObject(emptyMap())), last, oldest, false)
        assertNull(EventSyncer.lostRange(0, page(oldest = 1, last = 10)))
        assertEquals(1L to 1332L, EventSyncer.lostRange(0, page(oldest = 1333, last = 1832)))
        assertEquals(101L to 1332L, EventSyncer.lostRange(100, page(oldest = 1333, last = 1832)))
        assertNull(EventSyncer.lostRange(1332, page(oldest = 1333, last = 1832)))
        // An empty log (oldest = last + 1) lost nothing.
        assertNull(EventSyncer.lostRange(0, page(oldest = 6, last = 5, empty = true)))
        assertFalse(EventSyncer.lostRange(1840, page(oldest = 1333, last = 1832)) != null)
    }
}
