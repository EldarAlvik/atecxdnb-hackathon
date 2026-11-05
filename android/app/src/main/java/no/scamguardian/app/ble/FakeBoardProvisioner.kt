package no.scamguardian.app.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * A pretend board for the emulator (no Bluetooth there) and tests. It walks through the real
 * notification sequence and hands out [connectedAs] as the result, which by default points at the
 * Python mock board on the emulator's host (10.0.2.2:8787/8788, token `mock-token`).
 *
 * Password `wrong` gives `auth`, SSID `Nowhere` gives `no_ssid`, password `slow` gives `timeout`.
 */
class FakeBoardProvisioner(
    private val connectedAs: SetupStatus = SetupStatus(
        state = "connected", ssid = "", ip = "10.0.2.2", host = "guardian.local", port = 8787, wsPort = 8788,
        device = "guardian-m0c4", token = "mock-token",
    ),
    private val pace: Long = 700,
) : BoardProvisioner {

    override fun scan(): Flow<List<FoundBoard>> = flow {
        delay(pace)
        emit(listOf(FoundBoard("02:00:00:00:4D:30", "Guardian-4D30", -58)))
    }

    override suspend fun connect(board: FoundBoard): ProvisioningSession {
        delay(pace)
        return Session()
    }

    private inner class Session : ProvisioningSession {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val flow = MutableSharedFlow<SetupStatus>(replay = 1, extraBufferCapacity = 16)
        private var savedSsid = ""
        override val status: Flow<SetupStatus> = flow

        init {
            flow.tryEmit(SetupStatus(state = "ready", device = connectedAs.device, fw = "2.0.0-fake", proto = 2, savedSsid = ""))
        }

        override suspend fun send(command: SetupCommand) {
            scope.launch {
                when (command) {
                    SetupCommand.Scan -> {
                        flow.emit(SetupStatus(state = "scanning"))
                        delay(2 * pace)
                        flow.emit(SetupStatus(state = "scanned", count = NETWORKS.size))
                    }
                    is SetupCommand.Wifi -> join(command.ssid, command.pass)
                    SetupCommand.Pair -> if (savedSsid.isEmpty()) {
                        flow.emit(SetupStatus(state = "failed", error = "no_saved_wifi"))
                    } else {
                        join(savedSsid, null)
                    }
                    SetupCommand.Done -> flow.emit(SetupStatus(state = "bye"))
                }
            }
        }

        private suspend fun join(ssid: String, pass: String?) {
            flow.emit(SetupStatus(state = "connecting", ssid = ssid))
            delay(3 * pace)
            val error = when {
                ssid == "Nowhere" -> "no_ssid"
                pass == "wrong" -> "auth"
                pass == "slow" -> "timeout"
                else -> null
            }
            if (error != null) {
                flow.emit(SetupStatus(state = "failed", error = error))
            } else {
                savedSsid = ssid
                flow.emit(connectedAs.copy(ssid = ssid))
            }
        }

        override suspend fun readNetworks() = NETWORKS

        override fun close() = scope.cancel()
    }

    companion object {
        val NETWORKS = listOf(
            WifiNetwork("HomeNet", -48, true),
            WifiNetwork("Nowhere", -70, true),
            WifiNetwork("Cafe Guest", -77, false),
        )
    }
}
