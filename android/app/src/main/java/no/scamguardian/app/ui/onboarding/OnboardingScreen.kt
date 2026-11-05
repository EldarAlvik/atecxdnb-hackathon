package no.scamguardian.app.ui.onboarding

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import no.scamguardian.app.BuildConfig
import no.scamguardian.app.ble.BleBoardProvisioner

@Composable
fun OnboardingScreen(onFinished: () -> Unit, onCancel: (() -> Unit)?, vm: OnboardingViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(ui.done) {
        if (ui.done) {
            vm.consumeDone()
            onFinished()
        }
    }

    var permissionError by remember { mutableStateOf<String?>(null) }
    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (vm.bluetoothReady()) vm.startScan() else permissionError = "Bluetooth must be on to set up the board."
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val bleGranted = BleBoardProvisioner.requiredPermissions().all { result[it] == true }
        if (!bleGranted) {
            permissionError = "Without the Bluetooth permission the app cannot find the board. You can still enter its address by hand."
        } else if (vm.bluetoothReady()) {
            vm.startScan()
        } else {
            enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }
    }
    val startBle = {
        permissionError = null
        if (ui.simulateBle) {
            vm.startScan()
        } else {
            // POST_NOTIFICATIONS is asked here too so alerts work right after setup.
            val asks = BleBoardProvisioner.requiredPermissions() +
                (if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList())
            permissions.launch(asks.toTypedArray())
        }
    }

    Column(
        Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Set up Scam Call Guardian", style = MaterialTheme.typography.headlineSmall)
        when (ui.step) {
            Step.WELCOME -> Welcome(ui, startBle, vm, onCancel)
            Step.SCAN -> ScanStep(ui, vm)
            Step.CONNECTING -> Busy(
                "Connecting to ${ui.boardName}…",
                "When Android asks to pair, type the 6-digit code shown on the board's screen. The code is new every time the board enters setup.",
            )
            Step.WIFI -> WifiStep(ui, vm)
            Step.JOINING -> Busy("The board is joining ${ui.joiningSsid}…", "This takes up to 20 seconds.")
            Step.FINISHING -> Busy(ui.progress, null)
            Step.MANUAL -> ManualStep(ui, vm)
            Step.ERROR -> {
                Text(ui.error ?: "Something went wrong", color = MaterialTheme.colorScheme.error)
                Button(onClick = { vm.startScan() }) { Text("Try again") }
                TextButton(onClick = vm::back) { Text("Back") }
            }
        }
        permissionError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (ui.step != Step.WELCOME && ui.step != Step.ERROR && ui.step != Step.FINISHING) {
            TextButton(onClick = vm::back) { Text("Back to start") }
        }
    }
}

@Composable
private fun Welcome(ui: OnboardingUi, startBle: () -> Unit, vm: OnboardingViewModel, onCancel: (() -> Unit)?) {
    Text(
        "The board listens to phone calls on speaker and warns you when a caller sounds like a scammer. " +
            "This phone connects to it over Bluetooth once, gives it your Wi-Fi, and then talks to it over Wi-Fi.",
    )
    Text(
        "Put the board in setup mode: it starts in setup mode when it has no Wi-Fi, or press its button 4 times quickly (or hold it for 5 seconds). " +
            "Its screen then shows a 6-digit code.",
        style = MaterialTheme.typography.bodyMedium,
    )
    if (Build.VERSION.SDK_INT <= 30) {
        Text("On this Android version, Location must be switched on for Bluetooth scanning.", style = MaterialTheme.typography.bodySmall)
    }
    Button(onClick = startBle, modifier = Modifier.fillMaxWidth()) { Text("Set up with Bluetooth") }
    onCancel?.let { OutlinedButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("Cancel, keep the current board") } }

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("Developer options", style = MaterialTheme.typography.titleSmall)
    OutlinedButton(onClick = vm::openManual, modifier = Modifier.fillMaxWidth()) { Text("Enter host and token by hand") }
    if (BuildConfig.DEBUG) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Simulate Bluetooth")
                Text(
                    "A pretend board walks through setup and hands over the mock board at 10.0.2.2 (for the emulator).",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(checked = ui.simulateBle, onCheckedChange = vm::setSimulateBle)
        }
        OutlinedButton(onClick = vm::useSimulatedBoard, modifier = Modifier.fillMaxWidth()) {
            Text("Use a simulated board (no hardware, no mock)")
        }
    }
}

@Composable
private fun ScanStep(ui: OnboardingUi, vm: OnboardingViewModel) {
    Text("Looking for boards in setup mode…", style = MaterialTheme.typography.titleMedium)
    if (ui.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
    ui.boards.forEach { b ->
        Card(Modifier.fillMaxWidth().clickable { vm.pick(b) }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(b.name, fontWeight = FontWeight.Bold)
                    Text("Signal ${b.rssi} dBm", style = MaterialTheme.typography.bodySmall)
                }
                Text("Connect", color = MaterialTheme.colorScheme.primary)
            }
        }
    }
    if (!ui.scanning && ui.boards.isEmpty()) {
        Text("No board found. Is it in setup mode (6-digit code on its screen) and close to the phone?")
    }
    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    if (!ui.scanning) OutlinedButton(onClick = vm::startScan) { Text("Scan again") }
}

@Composable
private fun WifiStep(ui: OnboardingUi, vm: OnboardingViewModel) {
    var ssid by remember { mutableStateOf(ui.joiningSsid) } // keep the name after a failed try
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    Text("Connected to ${ui.boardName}", style = MaterialTheme.typography.titleMedium)
    Text("Choose the Wi-Fi network the board should use. It must be 2.4 GHz.", style = MaterialTheme.typography.bodyMedium)
    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    if (ui.savedSsid.isNotEmpty()) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The board already knows \"${ui.savedSsid}\".")
                Button(onClick = vm::useSavedWifi) { Text("Use it and pair this phone") }
            }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = vm::scanNetworks, enabled = !ui.networksScanning) { Text("Scan for networks") }
        if (ui.networksScanning) CircularProgressIndicator(Modifier.padding(start = 12.dp))
    }
    ui.networks.forEach { n ->
        Row(
            Modifier.fillMaxWidth().clickable { ssid = n.ssid }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(n.ssid, Modifier.weight(1f), fontWeight = if (n.ssid == ssid) FontWeight.Bold else FontWeight.Normal)
            Text("${n.rssi} dBm" + if (n.secure) "" else " · open", style = MaterialTheme.typography.bodySmall)
        }
    }
    OutlinedTextField(value = ssid, onValueChange = { ssid = it }, label = { Text("Network name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(
        value = pass,
        onValueChange = { pass = it },
        label = { Text("Password (empty for an open network)") },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { vm.joinWifi(ssid.trim(), pass) }, enabled = ssid.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
        Text("Connect the board")
    }
}

@Composable
private fun ManualStep(ui: OnboardingUi, vm: OnboardingViewModel) {
    var show by remember { mutableStateOf(false) }
    val m = ui.manual
    Text("Connect by address", style = MaterialTheme.typography.titleMedium)
    Text(
        "For development: the board's (or mock's) address and its device token. On the emulator the mock board on this computer is 10.0.2.2, ports 8787/8788, token mock-token.",
        style = MaterialTheme.typography.bodySmall,
    )
    OutlinedTextField(m.host, { v -> vm.editManual { it.copy(host = v) } }, label = { Text("Host or IP") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            m.port, { v -> vm.editManual { it.copy(port = v.filter(Char::isDigit)) } }, label = { Text("HTTP port") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            m.wsPort, { v -> vm.editManual { it.copy(wsPort = v.filter(Char::isDigit)) } }, label = { Text("WebSocket port") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
    }
    OutlinedTextField(
        m.token, { v -> vm.editManual { it.copy(token = v) } }, label = { Text("Device token") }, singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
        modifier = Modifier.fillMaxWidth(),
    )
    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = vm::connectManual, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
        Text(if (ui.busy) "Checking…" else "Connect")
    }
}

@Composable
private fun Busy(title: String, detail: String?) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator()
        Text(title, style = MaterialTheme.typography.titleMedium)
        detail?.let {
            Card { Text(it, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium) }
        }
    }
}
