package no.scamguardian.app.data

import javax.inject.Inject
import javax.inject.Singleton

/** Saving and forgetting the paired board, with the event history that belongs to it. */
@Singleton
class Pairing @Inject constructor(
    private val store: BoardStore,
    private val events: RoomEventStore,
) {
    /** Saves a new pairing. A different board has its own sequence numbers, so its history starts empty. */
    suspend fun save(board: PairedBoard, token: String?) {
        val previous = store.current()
        if (previous == null || previous.deviceId != board.deviceId || previous.kind != board.kind) events.clear()
        store.save(board, token)
    }

    /** Removes the board, its token (and the Keystore key) and the event history. */
    suspend fun forget() {
        store.forget()
        events.clear()
        TokenCipher.deleteKeystoreKey()
    }
}
