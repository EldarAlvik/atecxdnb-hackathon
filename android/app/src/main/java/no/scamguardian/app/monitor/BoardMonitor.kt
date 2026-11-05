package no.scamguardian.app.monitor

import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import no.scamguardian.app.board.BoardClient
import no.scamguardian.app.board.BoardEvent
import no.scamguardian.app.board.BoardException
import no.scamguardian.app.board.LiveMessage
import no.scamguardian.app.data.BoardKind
import no.scamguardian.app.data.BoardStore
import no.scamguardian.app.data.EventStore
import no.scamguardian.app.data.EventSyncer
import no.scamguardian.app.data.SyncResult
import no.scamguardian.app.discovery.BoardDiscovery

/**
 * Keeps one live connection to the board while it is started (by [MonitorService]):
 *
 * - the WebSocket for live events, reconnecting with backoff;
 * - GET /status every 15 s (5 s while the WebSocket is down) as the fallback and liveness check;
 * - GET /events catch-up on start and after every WebSocket reconnect ([EventSyncer]);
 * - "board offline" after [OFFLINE_AFTER_MS] without any answer, and NSD to find a moved board;
 * - notifications for a red verdict and a family-word match.
 */
class BoardMonitor(
    private val session: BoardSession,
    private val store: BoardStore,
    private val events: EventStore,
    private val syncer: EventSyncer,
    private val notifier: AlertSink,
    private val discovery: BoardDiscovery,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val _connection = MutableStateFlow(Connection.IDLE)
    val connection: StateFlow<Connection> = _connection.asStateFlow()

    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()

    private val _lastSync = MutableStateFlow<SyncResult?>(null)
    val lastSync: StateFlow<SyncResult?> = _lastSync.asStateFlow()

    @Volatile private var lastContact = 0L
    @Volatile private var wsLive = false
    @Volatile private var unauthorized = false
    private var offlineNotified = false
    private var lastClient: BoardClient? = null
    private var job: Job? = null
    private var users = 0

    @Synchronized
    fun start() {
        if (users++ == 0) job = scope.launch { run() }
    }

    @Synchronized
    fun stop() {
        if (users == 0) return
        if (--users == 0) {
            job?.cancel()
            job = null
            wsLive = false
            _connection.value = Connection.IDLE
            if (offlineNotified) notifier.cancelOffline()
            offlineNotified = false
        }
    }

    private suspend fun run() {
        session.client.collectLatest { c ->
            wsLive = false
            unauthorized = false
            if (c == null) {
                _connection.value = Connection.IDLE
                return@collectLatest
            }
            if (c !== lastClient) {
                if (lastClient != null) _live.value = LiveState()
                lastClient = c
            }
            lastContact = clock()
            updateConnection()
            coroutineScope {
                launch { webSocketLoop(c) }
                launch { pollLoop(c) }
                launch { watchdog() }
                launch { locate(onlyIfMoved = true) }
            }
        }
    }

    private suspend fun webSocketLoop(c: BoardClient) = coroutineScope {
        var failures = 0
        while (true) {
            var refusedToken = false
            try {
                c.live().collect { m ->
                    contact()
                    when (m) {
                        is LiveMessage.Hello -> {
                            failures = 0
                            wsLive = true
                            updateConnection()
                            launch { sync(c) } // catch up on what happened while we were away
                        }
                        is LiveMessage.Event -> onEvent(m.event)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: BoardException.Unauthorized) {
                refusedToken = true
            } catch (_: Exception) {
            }
            wsLive = false
            updateConnection()
            failures++
            delay(if (refusedToken) 30_000 else backoffMs(failures))
        }
    }

    private suspend fun pollLoop(c: BoardClient) {
        var first = true
        var lastSeenSeq = -1L
        while (true) {
            try {
                val s = c.status()
                contact()
                unauthorized = false
                setLive(_live.value.withStatus(s))
                // On start, and while the WebSocket is down, pick up new events from /events.
                if (first || (!wsLive && s.lastSeq != lastSeenSeq)) sync(c)
                first = false
                lastSeenSeq = s.lastSeq
            } catch (e: CancellationException) {
                throw e
            } catch (e: BoardException.Unauthorized) {
                contact()
                unauthorized = true
            } catch (e: BoardException.Unreachable) {
            } catch (e: BoardException) {
                contact() // it answered, just not with a status (low memory, 429, ...)
            }
            updateConnection()
            delay(if (wsLive) POLL_LIVE_MS else POLL_FALLBACK_MS)
        }
    }

    private suspend fun watchdog() {
        var lastLocate = 0L
        while (true) {
            delay(1_000)
            val state = updateConnection()
            if (state == Connection.OFFLINE) {
                if (!offlineNotified) {
                    offlineNotified = true
                    notifier.boardOffline((clock() - lastContact) / 1000)
                }
                if (clock() - lastLocate > LOCATE_EVERY_MS) {
                    lastLocate = clock()
                    locate(onlyIfMoved = false)
                }
            } else if (offlineNotified) {
                offlineNotified = false
                notifier.cancelOffline()
            }
        }
    }

    /** Finds the board with NSD by its device id; falls back to the setup IP. Only for BLE pairings. */
    private suspend fun locate(onlyIfMoved: Boolean) {
        val board = store.current() ?: return
        if (board.kind != BoardKind.BLE) return
        val found = runCatching { discovery.find(board.deviceId) }.getOrNull()
        when {
            found != null && (found.host != board.host || found.port != board.port || found.wsPort != board.wsPort) ->
                store.updateLocation(found.host, found.port, found.wsPort)
            found == null && !onlyIfMoved && board.setupIp.isNotEmpty() && board.host != board.setupIp ->
                store.updateLocation(board.setupIp, board.port, board.wsPort)
        }
    }

    private suspend fun onEvent(e: BoardEvent) {
        setLive(_live.value.apply(e))
        if (e.isLogged) runCatching { events.saveLive(e) }
    }

    private fun setLive(after: LiveState) {
        val before = _live.value
        _live.value = after
        alertsFor(before.takeIf { it.known }, after).forEach(notifier::alert)
    }

    private suspend fun sync(c: BoardClient) {
        try {
            _lastSync.value = syncer.sync(c)
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
        }
    }

    private fun contact() {
        lastContact = clock()
    }

    private fun updateConnection(): Connection {
        val state = when {
            unauthorized -> Connection.UNAUTHORIZED
            clock() - lastContact > OFFLINE_AFTER_MS -> Connection.OFFLINE
            wsLive -> Connection.LIVE
            else -> Connection.RECONNECTING
        }
        _connection.value = state
        return state
    }

    /** Turns guarding on or off and mirrors it locally right away. */
    suspend fun setGuard(on: Boolean): Boolean {
        val c = session.requireClient()
        val guarding = c.setGuard(on)
        contact()
        val cur = _live.value
        if (cur.guarding != guarding) {
            _live.value = if (guarding) {
                cur.copy(guarding = true, verdict = "none", reason = "", latched = false, transcript = "", wordMatch = null)
            } else {
                cur.copy(guarding = false, latched = false)
            }
        }
        return guarding
    }

    suspend fun syncNow(): SyncResult = syncer.sync(session.requireClient()).also { _lastSync.value = it }

    companion object {
        const val OFFLINE_AFTER_MS = 30_000L
        const val POLL_LIVE_MS = 15_000L
        const val POLL_FALLBACK_MS = 5_000L
        const val LOCATE_EVERY_MS = 60_000L

        fun backoffMs(failures: Int): Long = (1_000L shl (failures - 1).coerceIn(0, 4)).coerceAtMost(15_000)
    }
}

/** Where the monitor sends alerts. [Notifier] in the app. */
interface AlertSink {
    fun alert(alert: Alert)
    fun boardOffline(seconds: Long)
    fun cancelOffline()
}
