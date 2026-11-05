package no.scamguardian.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.scamguardian.app.board.BoardClient
import no.scamguardian.app.board.BoardEvent
import no.scamguardian.app.board.EventsPage
import no.scamguardian.app.data.db.EventDao
import no.scamguardian.app.data.db.EventEntity
import no.scamguardian.app.data.db.GapEntity
import no.scamguardian.app.data.db.SyncStateEntity

data class SyncState(val epoch: Int, val cursor: Long)

object GapReason {
    /** `since < oldest_seq - 1`: the board's ring buffer dropped events before the phone read them. */
    const val LOST = "lost"

    /** The board's `last_seq` went below the phone's cursor: its event log started over. */
    const val RESTARTED = "restarted"
}

/** Where synced and live events go. Room in the app ([RoomEventStore]), a fake in tests. */
interface EventStore {
    suspend fun state(): SyncState

    /** Stores [events] (duplicates ignored) and moves the cursor, in one transaction. */
    suspend fun saveSynced(epoch: Int, events: List<BoardEvent>, cursor: Long)

    /** Stores one event from the WebSocket. Does not move the cursor. */
    suspend fun saveLive(event: BoardEvent)

    suspend fun addGap(epoch: Int, fromSeq: Long, toSeq: Long, reason: String)

    /** Starts a new epoch with cursor 0 and returns it. */
    suspend fun newEpoch(): Int
}

data class SyncResult(val added: Int, val gaps: Int, val restarted: Boolean)

/**
 * Catches up with GET /events (protocol section 2). Runs on start and after every WebSocket
 * reconnect. The cursor is separate from the newest stored event: live events are stored as they
 * come, but only a completed /events read moves the cursor, so a reconnect never skips anything.
 */
class EventSyncer(
    private val store: EventStore,
    private val pageSize: Int = 200,
    private val maxPages: Int = 100,
) {
    private val mutex = Mutex()

    suspend fun sync(client: BoardClient): SyncResult = mutex.withLock {
        var (epoch, cursor) = store.state()
        var gaps = 0
        var added = 0
        var restarted = false

        var page = client.events(cursor, pageSize)
        if (page.lastSeq < cursor) {
            // The board has fewer events than we already read: its log started over (or another board).
            epoch = store.newEpoch()
            store.addGap(epoch, 0, 0, GapReason.RESTARTED)
            gaps++
            restarted = true
            cursor = 0
            page = client.events(cursor, pageSize)
        }
        lostRange(cursor, page)?.let { (from, to) ->
            store.addGap(epoch, from, to, GapReason.LOST)
            gaps++
        }

        var pages = 0
        while (true) {
            val events = page.events.mapNotNull(BoardEvent::parse).filter { it.seq > cursor && it.isLogged }
            val newCursor = if (page.more) {
                events.maxOfOrNull { it.seq } ?: cursor
            } else {
                maxOf(cursor, events.maxOfOrNull { it.seq } ?: cursor, page.lastSeq)
            }
            store.saveSynced(epoch, events, newCursor)
            added += events.size
            pages++
            if (!page.more || newCursor == cursor || pages >= maxPages) break
            cursor = newCursor
            page = client.events(cursor, pageSize)
        }
        SyncResult(added, gaps, restarted)
    }

    companion object {
        /**
         * Seqs the board dropped before we read them, or null. The protocol's rule is
         * `since < oldest_seq - 1`. Two cases are left out because nothing the phone had a
         * claim to was lost: the very first sync (`since == 0`; filtered events such as
         * `startup` also use up numbers, so the oldest logged seq is often above 1), and an
         * empty log (no events, `oldest_seq > last_seq`).
         */
        fun lostRange(since: Long, page: EventsPage): Pair<Long, Long>? {
            val logEmpty = page.events.isEmpty() && page.oldestSeq > page.lastSeq
            if (since == 0L || logEmpty || since >= page.oldestSeq - 1) return null
            return (since + 1) to (page.oldestSeq - 1)
        }
    }
}

/** [EventStore] on Room. Keeps at most [maxEvents] events. */
class RoomEventStore(
    private val dao: EventDao,
    private val maxEvents: Int = 5000,
    private val now: () -> Long = System::currentTimeMillis,
) : EventStore {
    private val lock = Mutex()

    override suspend fun state(): SyncState =
        dao.syncState()?.let { SyncState(it.epoch, it.cursor) } ?: SyncState(0, 0)

    override suspend fun saveSynced(epoch: Int, events: List<BoardEvent>, cursor: Long) = lock.withLock {
        dao.saveSynced(events.map { it.toEntity(epoch) }, SyncStateEntity(epoch = epoch, cursor = cursor))
        prune()
    }

    override suspend fun saveLive(event: BoardEvent) = lock.withLock {
        if (!event.isLogged) return@withLock
        dao.insert(listOf(event.toEntity(state().epoch)))
        prune()
    }

    override suspend fun addGap(epoch: Int, fromSeq: Long, toSeq: Long, reason: String) {
        dao.insertGap(GapEntity(epoch = epoch, fromSeq = fromSeq, toSeq = toSeq, reason = reason, detectedAt = now()))
    }

    override suspend fun newEpoch(): Int = lock.withLock {
        val next = state().epoch + 1
        dao.putSyncState(SyncStateEntity(epoch = next, cursor = 0))
        next
    }

    suspend fun clear() = lock.withLock { dao.clearAll() }

    private suspend fun prune() {
        val extra = dao.count() - maxEvents
        if (extra > 0) dao.deleteOldest(extra)
    }

    private fun BoardEvent.toEntity(epoch: Int) = EventEntity(
        epoch = epoch, seq = seq, ts = ts, key = key, eventType = eventType,
        valueJson = value.toString(), receivedAt = now(),
    )
}
