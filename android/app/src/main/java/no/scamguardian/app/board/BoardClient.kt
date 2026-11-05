package no.scamguardian.app.board

import java.io.IOException
import kotlinx.coroutines.flow.Flow

/**
 * The board's local API (protocol sections 2 and 3). [HttpBoardClient] talks to a real board or the
 * Python mock; [MockBoardClient] is an in-process fake.
 *
 * Every call throws [BoardException] on failure. Family words are passed straight through to the
 * board and never kept, logged or put into exception messages.
 */
interface BoardClient {
    suspend fun status(): BoardStatus
    suspend fun setGuard(on: Boolean): Boolean
    suspend fun events(since: Long, limit: Int = 200): EventsPage
    suspend fun config(): BoardConfig

    /** POST /config with only the changed keys ([ConfigRules.patch]); returns the full config. */
    suspend fun updateConfig(patch: kotlinx.serialization.json.JsonObject): BoardConfig
    suspend fun members(): WordsList
    suspend fun addMember(name: String, word: String?): Member
    suspend fun renameMember(id: Int, name: String): Member
    suspend fun setWord(id: Int, word: String): Member
    suspend fun clearWord(id: Int): Member
    suspend fun deleteMember(id: Int)

    /** POST /setup: the board restarts into BLE setup mode. */
    suspend fun startSetup()

    /**
     * The WebSocket. Each collection opens one connection; the flow completes when the board closes
     * it and throws [BoardException] when it fails. The first message is [LiveMessage.Hello].
     */
    fun live(): Flow<LiveMessage>
}

sealed class BoardException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    /** 401, or the WebSocket was closed before `hello`: the token is not (or no longer) accepted. */
    class Unauthorized : BoardException("The board did not accept this phone's token")
    class RateLimited : BoardException("Too many wrong tokens; the board refuses requests for 30 s")
    class BadRequest(val error: String) : BoardException("The board refused the request: $error")
    class NotFound(val error: String) : BoardException("Not found: $error")
    class LowMemory : BoardException("The board is low on memory and refused the request")
    class Http(val code: Int) : BoardException("Unexpected answer from the board (HTTP $code)")
    class Unreachable(cause: Throwable?) : BoardException("Cannot reach the board", cause)
    class Protocol(message: String) : BoardException(message)

    /** The WebSocket upgrade was accepted but the board closed it (busy: at most 2 clients, or low memory). */
    class Refused(val code: Int, val reason: String) : BoardException("The board closed the live connection ($code $reason)")
}
