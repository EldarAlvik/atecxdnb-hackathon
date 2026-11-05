package no.scamguardian.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import no.scamguardian.app.board.BoardEndpoint

enum class BoardKind {
    /** Paired over Bluetooth; found again with NSD. */
    BLE,

    /** Host, ports and token typed in by hand (developer option); no NSD. */
    MANUAL,

    /** The in-process [no.scamguardian.app.board.MockBoardClient]. */
    SIMULATED,
}

/** The board this phone talks to. Nothing secret: the token is kept apart, encrypted. */
data class PairedBoard(
    val kind: BoardKind,
    val deviceId: String,
    /** Where the board was last found (NSD result, or the setup IP, or the manual host). */
    val host: String,
    val port: Int,
    val wsPort: Int,
    /** The IP from the `connected` setup notification, used when NSD finds nothing. */
    val setupIp: String,
)

/** A paired board together with its decrypted token, or the reason the token cannot be used. */
data class BoardSessionInfo(val board: PairedBoard, val endpoint: BoardEndpoint?, val tokenError: Boolean)

/**
 * The phone's settings in DataStore. The device token is stored only as AES-GCM ciphertext from
 * [TokenCipher] (Keystore key). Family words are never stored here.
 */
class BoardStore(
    private val store: DataStore<Preferences>,
    private val cipher: TokenCipher,
) {
    val paired: Flow<PairedBoard?> = store.data.map(::readBoard).distinctUntilChanged()

    val session: Flow<BoardSessionInfo?> = store.data.map { prefs ->
        val board = readBoard(prefs) ?: return@map null
        if (board.kind == BoardKind.SIMULATED) return@map BoardSessionInfo(board, null, false)
        val token = prefs[TOKEN]?.let { runCatching { cipher.decrypt(it) }.getOrNull() }
        BoardSessionInfo(
            board,
            token?.let { BoardEndpoint(board.host, board.port, board.wsPort, it) },
            tokenError = token == null,
        )
    }.distinctUntilChanged()

    val backgroundMonitoring: Flow<Boolean> = store.data.map { it[BACKGROUND] ?: true }.distinctUntilChanged()

    suspend fun current(): PairedBoard? = paired.first()

    suspend fun save(board: PairedBoard, token: String?) {
        val ct = token?.let(cipher::encrypt)
        store.edit { p ->
            p[KIND] = board.kind.name
            p[DEVICE] = board.deviceId
            p[HOST] = board.host
            p[PORT] = board.port
            p[WS_PORT] = board.wsPort
            p[SETUP_IP] = board.setupIp
            if (ct != null) p[TOKEN] = ct else p.remove(TOKEN)
        }
    }

    /** NSD found the board somewhere else. */
    suspend fun updateLocation(host: String, port: Int, wsPort: Int) {
        store.edit { p ->
            if (p[DEVICE] == null) return@edit
            p[HOST] = host
            p[PORT] = port
            p[WS_PORT] = wsPort
        }
    }

    suspend fun setBackgroundMonitoring(on: Boolean) {
        store.edit { it[BACKGROUND] = on }
    }

    suspend fun forget() {
        store.edit { p -> listOf(KIND, DEVICE, HOST, SETUP_IP, TOKEN).forEach { p.remove(it) }; p.remove(PORT); p.remove(WS_PORT) }
    }

    private fun readBoard(p: Preferences): PairedBoard? {
        val kind = p[KIND]?.let { runCatching { BoardKind.valueOf(it) }.getOrNull() } ?: return null
        return PairedBoard(
            kind = kind,
            deviceId = p[DEVICE] ?: "",
            host = p[HOST] ?: "",
            port = p[PORT] ?: 80,
            wsPort = p[WS_PORT] ?: 81,
            setupIp = p[SETUP_IP] ?: "",
        )
    }

    private companion object {
        val KIND = stringPreferencesKey("board_kind")
        val DEVICE = stringPreferencesKey("board_device")
        val HOST = stringPreferencesKey("board_host")
        val PORT = intPreferencesKey("board_port")
        val WS_PORT = intPreferencesKey("board_ws_port")
        val SETUP_IP = stringPreferencesKey("board_setup_ip")
        val TOKEN = stringPreferencesKey("board_token_ciphertext")
        val BACKGROUND = booleanPreferencesKey("background_monitoring")
    }
}
