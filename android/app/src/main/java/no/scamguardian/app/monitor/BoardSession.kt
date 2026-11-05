package no.scamguardian.app.monitor

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull
import no.scamguardian.app.board.BoardClient
import no.scamguardian.app.board.BoardException
import no.scamguardian.app.board.HttpBoardClient
import no.scamguardian.app.board.MockBoardClient
import no.scamguardian.app.data.BoardKind
import no.scamguardian.app.data.BoardSessionInfo
import no.scamguardian.app.data.BoardStore
import okhttp3.OkHttpClient

/** The [BoardClient] for the paired board, rebuilt whenever the pairing or the board's address changes. */
class BoardSession(
    store: BoardStore,
    private val okHttp: OkHttpClient,
    private val simulated: () -> MockBoardClient,
    scope: CoroutineScope,
) {
    val info: StateFlow<BoardSessionInfo?> = store.session.stateIn(scope, SharingStarted.Eagerly, null)

    val client: StateFlow<BoardClient?> = store.session.map { s ->
        when {
            s == null -> null
            s.board.kind == BoardKind.SIMULATED -> simulated()
            s.endpoint == null -> null
            else -> HttpBoardClient(s.endpoint, okHttp)
        }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The current client, waiting briefly for DataStore on a cold start. */
    suspend fun requireClient(): BoardClient =
        client.value ?: withTimeoutOrNull(3_000) { client.filterNotNull().first() }
            ?: throw BoardException.Protocol("No board is paired")
}
