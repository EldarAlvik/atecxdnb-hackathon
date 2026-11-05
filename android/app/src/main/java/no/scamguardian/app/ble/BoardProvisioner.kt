package no.scamguardian.app.ble

import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** BLE setup GATT layout (protocol section 1). */
object GuardianGatt {
    val SERVICE: UUID = UUID.fromString("47a10001-8b3c-4d2e-9f1a-5c6d7e8f9a0b")
    val COMMAND: UUID = UUID.fromString("47a10002-8b3c-4d2e-9f1a-5c6d7e8f9a0b")
    val STATUS: UUID = UUID.fromString("47a10003-8b3c-4d2e-9f1a-5c6d7e8f9a0b")
    val NETWORKS: UUID = UUID.fromString("47a10004-8b3c-4d2e-9f1a-5c6d7e8f9a0b")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    const val MTU = 247
}

/** A board seen advertising the setup service. */
data class FoundBoard(val address: String, val name: String, val rssi: Int)

/** One `status` notification. The token is only in `connected`; [toString] never prints it. */
@Serializable
data class SetupStatus(
    val state: String,
    val device: String? = null,
    val fw: String? = null,
    val proto: Int? = null,
    @SerialName("saved_ssid") val savedSsid: String? = null,
    val count: Int? = null,
    val ssid: String? = null,
    val ip: String? = null,
    val host: String? = null,
    val port: Int? = null,
    @SerialName("ws_port") val wsPort: Int? = null,
    val token: String? = null,
    val error: String? = null,
) {
    override fun toString() = "SetupStatus(state=$state, device=$device, ssid=$ssid, ip=$ip, error=$error)"

    companion object {
        /** Not from the board: the app lost the BLE link. */
        const val DISCONNECTED = "_disconnected"
    }
}

@Serializable
data class WifiNetwork(val ssid: String, val rssi: Int = 0, val secure: Boolean = true)

/** Commands written to the `command` characteristic. */
sealed class SetupCommand {
    data object Scan : SetupCommand()
    class Wifi(val ssid: String, val pass: String) : SetupCommand() {
        override fun toString() = "Wifi(ssid=$ssid, pass=<redacted>)"
    }
    data object Pair : SetupCommand()
    data object Done : SetupCommand()

    fun toJson(): JsonObject = buildJsonObject {
        when (this@SetupCommand) {
            Scan -> put("op", JsonPrimitive("scan"))
            is Wifi -> {
                put("op", JsonPrimitive("wifi"))
                put("ssid", JsonPrimitive(ssid))
                put("pass", JsonPrimitive(pass))
            }
            Pair -> put("op", JsonPrimitive("pair"))
            Done -> put("op", JsonPrimitive("done"))
        }
    }
}

sealed class ProvisionException(message: String) : Exception(message) {
    class BluetoothOff : ProvisionException("Bluetooth is off")
    class MissingPermission : ProvisionException("Bluetooth permission is missing")
    class ConnectFailed(detail: String) : ProvisionException("Could not connect to the board ($detail)")
    class NotAGuardian : ProvisionException("This device does not offer the Guardian setup service")
    class PairingFailed : ProvisionException(
        "Pairing failed. Check the 6-digit code on the board's screen and try again. " +
            "If this phone was paired with the board before, remove \"Guardian\" in the phone's Bluetooth settings first.",
    )
    class StaleBond : ProvisionException(
        "This phone still has an old pairing with the board, which the board has forgotten. " +
            "Open the phone's Bluetooth settings, forget \"Guardian\", then try again.",
    )
    class Disconnected : ProvisionException("The board disconnected")
    class Gatt(op: String, status: Int) : ProvisionException("Bluetooth $op failed (status $status)")
    class Timeout(op: String) : ProvisionException("The board did not answer ($op)")
}

/** BLE setup. [BleBoardProvisioner] on a phone, [FakeBoardProvisioner] in tests and on the emulator. */
interface BoardProvisioner {
    /** Boards advertising the setup service; the list grows while collected. */
    fun scan(): Flow<List<FoundBoard>>

    /** Connects, asks for MTU 247, bonds (system passkey dialog) and subscribes to `status`. */
    suspend fun connect(board: FoundBoard): ProvisioningSession
}

interface ProvisioningSession {
    /** `status` notifications, starting with the current value. */
    val status: Flow<SetupStatus>

    suspend fun send(command: SetupCommand)

    /** Reads `networks` (after `scanned`). */
    suspend fun readNetworks(): List<WifiNetwork>

    fun close()
}
