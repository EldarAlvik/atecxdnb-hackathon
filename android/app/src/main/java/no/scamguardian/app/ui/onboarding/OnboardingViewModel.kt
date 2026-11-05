package no.scamguardian.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import no.scamguardian.app.BuildConfig
import no.scamguardian.app.ble.BleBoardProvisioner
import no.scamguardian.app.ble.BoardProvisioner
import no.scamguardian.app.ble.FakeBoardProvisioner
import no.scamguardian.app.ble.FoundBoard
import no.scamguardian.app.ble.ProvisionException
import no.scamguardian.app.ble.ProvisioningSession
import no.scamguardian.app.ble.SetupCommand
import no.scamguardian.app.ble.SetupStatus
import no.scamguardian.app.ble.WifiNetwork
import no.scamguardian.app.board.BoardEndpoint
import no.scamguardian.app.board.HttpBoardClient
import no.scamguardian.app.board.MockBoardClient
import no.scamguardian.app.data.BoardKind
import no.scamguardian.app.data.BoardStore
import no.scamguardian.app.data.PairedBoard
import no.scamguardian.app.data.Pairing
import no.scamguardian.app.discovery.BoardDiscovery
import no.scamguardian.app.ui.errorMessage
import okhttp3.OkHttpClient

enum class Step { WELCOME, SCAN, CONNECTING, WIFI, JOINING, FINISHING, MANUAL, ERROR }

data class ManualFields(
    val host: String = if (BuildConfig.DEBUG) "10.0.2.2" else "",
    val port: String = if (BuildConfig.DEBUG) "8787" else "80",
    val wsPort: String = if (BuildConfig.DEBUG) "8788" else "81",
    val token: String = if (BuildConfig.DEBUG) "mock-token" else "",
) {
    override fun toString() = "ManualFields(host=$host, port=$port, wsPort=$wsPort, token=<redacted>)"
}

data class OnboardingUi(
    val step: Step = Step.WELCOME,
    val simulateBle: Boolean = false,
    val boards: List<FoundBoard> = emptyList(),
    val scanning: Boolean = false,
    val boardName: String = "",
    val deviceId: String = "",
    val savedSsid: String = "",
    val networks: List<WifiNetwork> = emptyList(),
    val networksScanning: Boolean = false,
    val joiningSsid: String = "",
    val progress: String = "",
    val error: String? = null,
    val manual: ManualFields = ManualFields(),
    val busy: Boolean = false,
    val done: Boolean = false,
)

/**
 * First setup (protocol section 1): scan, connect and bond (the user types the board's passkey in
 * Android's dialog), optional Wi-Fi scan, `wifi` or `pair`, token from `connected`, store it,
 * `done`, then NSD. Also the developer paths: manual host/token and the simulated board.
 *
 * The Wi-Fi password and the token pass through here once and are not kept in [OnboardingUi]
 * (except the manual token the user is typing).
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val ble: BleBoardProvisioner,
    private val fake: FakeBoardProvisioner,
    private val pairing: Pairing,
    private val store: BoardStore,
    private val discovery: BoardDiscovery,
    private val okHttp: OkHttpClient,
) : ViewModel() {
    private val _ui = MutableStateFlow(OnboardingUi())
    val ui: StateFlow<OnboardingUi> = _ui.asStateFlow()

    private var session: ProvisioningSession? = null
    private var scanJob: Job? = null
    private var statusJob: Job? = null
    private var joinTimeout: Job? = null

    private val provisioner: BoardProvisioner get() = if (_ui.value.simulateBle) fake else ble

    fun setSimulateBle(on: Boolean) = _ui.update { it.copy(simulateBle = on) }

    fun bluetoothReady(): Boolean = _ui.value.simulateBle || (ble.hasPermissions() && ble.isBluetoothOn())

    fun startScan() {
        closeSession()
        _ui.update { it.copy(step = Step.SCAN, boards = emptyList(), scanning = true, error = null) }
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            try {
                withTimeoutOrNull(SCAN_MS) {
                    provisioner.scan().collect { list -> _ui.update { it.copy(boards = list) } }
                }
            } catch (e: ProvisionException) {
                _ui.update { it.copy(error = e.message) }
            } catch (e: SecurityException) {
                _ui.update { it.copy(error = "Bluetooth permission is missing") }
            } finally {
                _ui.update { it.copy(scanning = false) }
            }
        }
    }

    fun pick(board: FoundBoard) {
        scanJob?.cancel()
        _ui.update { it.copy(step = Step.CONNECTING, boardName = board.name, error = null) }
        viewModelScope.launch {
            try {
                val s = provisioner.connect(board)
                session = s
                _ui.update { it.copy(step = Step.WIFI) }
                statusJob = launch { s.status.collect(::onStatus) }
            } catch (e: ProvisionException) {
                fail(e.message ?: "Could not connect")
            } catch (e: SecurityException) {
                fail("Bluetooth permission is missing")
            }
        }
    }

    private fun onStatus(s: SetupStatus) {
        when (s.state) {
            "ready" -> _ui.update { it.copy(deviceId = s.device.orEmpty(), savedSsid = s.savedSsid.orEmpty()) }
            "scanning" -> _ui.update { it.copy(networksScanning = true) }
            "scanned" -> viewModelScope.launch {
                val list = runCatching { session?.readNetworks().orEmpty() }.getOrDefault(emptyList())
                _ui.update { it.copy(networksScanning = false, networks = list) }
            }
            "connecting" -> _ui.update { it.copy(step = Step.JOINING, joiningSsid = s.ssid ?: it.joiningSsid) }
            "connected" -> onConnected(s)
            "failed" -> {
                joinTimeout?.cancel()
                _ui.update { it.copy(step = Step.WIFI, networksScanning = false, error = failureText(s.error)) }
            }
            SetupStatus.DISCONNECTED -> if (_ui.value.step != Step.FINISHING && !_ui.value.done) {
                fail("Lost the Bluetooth connection to the board. Hold its button for 5 s to restart setup if it left setup mode.")
            }
        }
    }

    fun scanNetworks() = send(SetupCommand.Scan) { _ui.update { it.copy(networksScanning = true, error = null) } }

    fun joinWifi(ssid: String, pass: String) {
        if (ssid.isBlank()) {
            _ui.update { it.copy(error = "Choose or type a network name") }
            return
        }
        startJoin(ssid)
        send(SetupCommand.Wifi(ssid, pass), null)
    }

    fun useSavedWifi() {
        startJoin(_ui.value.savedSsid)
        send(SetupCommand.Pair, null)
    }

    /** Debug builds: skip choosing a network. The board joins its saved network, or the
     *  one compiled in from .env, and still hands over the token (`pair`). */
    fun skipWifiDebug() {
        startJoin(_ui.value.savedSsid.ifEmpty { "the board's own network" })
        send(SetupCommand.Pair, null)
    }

    private fun startJoin(ssid: String) {
        _ui.update { it.copy(step = Step.JOINING, joiningSsid = ssid, error = null) }
        joinTimeout?.cancel()
        // The board tries for up to 30 s and then notifies; this only catches a lost notification.
        joinTimeout = viewModelScope.launch {
            delay(60_000)
            if (_ui.value.step == Step.JOINING) _ui.update { it.copy(step = Step.WIFI, error = failureText("timeout")) }
        }
    }

    private fun send(cmd: SetupCommand, before: (() -> Unit)?) {
        val s = session ?: return fail("Not connected to the board")
        before?.invoke()
        viewModelScope.launch {
            try {
                s.send(cmd)
            } catch (e: ProvisionException) {
                joinTimeout?.cancel()
                _ui.update { it.copy(step = Step.WIFI, networksScanning = false, error = e.message) }
            }
        }
    }

    private fun onConnected(s: SetupStatus) {
        joinTimeout?.cancel()
        val token = s.token
        val ip = s.ip.orEmpty()
        if (token.isNullOrBlank() || ip.isEmpty()) {
            _ui.update { it.copy(step = Step.WIFI, error = "The board joined Wi-Fi but sent no token or address. Try again.") }
            return
        }
        val device = s.device ?: _ui.value.deviceId
        _ui.update { it.copy(step = Step.FINISHING, progress = "Saving the board's key on this phone…", error = null) }
        viewModelScope.launch {
            val board = PairedBoard(BoardKind.BLE, device, ip, s.port ?: 80, s.wsPort ?: 81, setupIp = ip)
            pairing.save(board, token) // the token is stored (encrypted) before telling the board `done`
            _ui.update { it.copy(progress = "Finishing setup on the board…") }
            session?.let { runCatching { it.send(SetupCommand.Done) } }
            closeSession()
            _ui.update { it.copy(progress = "Looking for the board on your Wi-Fi…") }
            discovery.find(device, 8_000)?.let { store.updateLocation(it.host, it.port, it.wsPort) }
            _ui.update { it.copy(done = true) }
        }
    }

    fun openManual() = _ui.update { it.copy(step = Step.MANUAL, error = null) }

    fun editManual(change: (ManualFields) -> ManualFields) = _ui.update { it.copy(manual = change(it.manual), error = null) }

    fun connectManual() {
        val m = _ui.value.manual
        val port = m.port.toIntOrNull()
        val wsPort = m.wsPort.toIntOrNull()
        if (m.host.isBlank() || port == null || port !in 1..65535 || wsPort == null || wsPort !in 1..65535 || m.token.isBlank()) {
            _ui.update { it.copy(error = "Fill in host, both ports (1-65535) and the token") }
            return
        }
        _ui.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val endpoint = BoardEndpoint(m.host.trim(), port, wsPort, m.token.trim())
            runCatching { HttpBoardClient(endpoint, okHttp).status() }
                .onSuccess { st ->
                    pairing.save(PairedBoard(BoardKind.MANUAL, st.device, endpoint.host, port, wsPort, endpoint.host), endpoint.token)
                    _ui.update { it.copy(busy = false, done = true) }
                }
                .onFailure { e -> _ui.update { it.copy(busy = false, error = errorMessage(e)) } }
        }
    }

    fun useSimulatedBoard() {
        viewModelScope.launch {
            pairing.save(PairedBoard(BoardKind.SIMULATED, MockBoardClient.DEVICE, "", 0, 0, ""), null)
            _ui.update { it.copy(done = true) }
        }
    }

    fun back() {
        closeSession()
        scanJob?.cancel()
        _ui.update { OnboardingUi(simulateBle = it.simulateBle, manual = it.manual) }
    }

    /** Called once the screen has acted on [OnboardingUi.done], so the next setup starts fresh. */
    fun consumeDone() {
        _ui.value = OnboardingUi(simulateBle = _ui.value.simulateBle)
    }

    private fun fail(message: String) {
        closeSession()
        _ui.update { it.copy(step = Step.ERROR, error = message) }
    }

    private fun closeSession() {
        statusJob?.cancel()
        joinTimeout?.cancel()
        session?.close()
        session = null
    }

    override fun onCleared() {
        closeSession()
    }

    companion object {
        const val SCAN_MS = 20_000L

        fun failureText(error: String?): String = when (error) {
            "auth" -> "Wrong Wi-Fi password. Check it and try again."
            "no_ssid" -> "The board cannot find that network. It needs a 2.4 GHz network within reach."
            "timeout" -> "The board could not join in time. It needs a 2.4 GHz network: for a phone " +
                "hotspot, turn on \"extend compatibility\" or 2.4 GHz, keep it on, and try again."
            "no_saved_wifi" -> "The board has no saved network. Choose one below."
            "storage" -> "The board joined but could not save the network. Try again."
            "bad_request" -> "The board did not understand the request. Try again."
            null, "" -> "Joining failed. Try again."
            else -> "Joining failed ($error). Try again."
        }
    }
}
