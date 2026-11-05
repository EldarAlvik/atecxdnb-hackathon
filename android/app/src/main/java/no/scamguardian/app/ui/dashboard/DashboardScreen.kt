package no.scamguardian.app.ui.dashboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.scamguardian.app.board.Verdict
import no.scamguardian.app.board.verdictLabel
import no.scamguardian.app.monitor.BoardMonitor
import no.scamguardian.app.monitor.BoardSession
import no.scamguardian.app.monitor.Connection
import no.scamguardian.app.monitor.LiveState
import no.scamguardian.app.ui.ConnectionChip
import no.scamguardian.app.ui.errorMessage
import no.scamguardian.app.ui.theme.GuardianColors

data class DashboardUi(
    val connection: Connection = Connection.IDLE,
    val live: LiveState = LiveState(),
    val boardName: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val monitor: BoardMonitor,
    session: BoardSession,
) : ViewModel() {
    private val local = MutableStateFlow(DashboardUi())

    val ui: StateFlow<DashboardUi> = combine(monitor.connection, monitor.live, session.info, local) { c, l, info, loc ->
        loc.copy(connection = c, live = l, boardName = l.device.ifEmpty { info?.board?.deviceId.orEmpty() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUi())

    fun setGuarding(on: Boolean) {
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val err = runCatching { monitor.setGuard(on) }.exceptionOrNull()
            local.update { it.copy(busy = false, error = err?.let(::errorMessage)) }
        }
    }
}

@Composable
fun DashboardScreen(vm: DashboardViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Dashboard(ui, vm::setGuarding)
}

@Composable
fun Dashboard(ui: DashboardUi, onGuard: (Boolean) -> Unit) {
    val live = ui.live
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Scam Call Guardian", style = MaterialTheme.typography.titleLarge)
                if (ui.boardName.isNotEmpty()) Text(ui.boardName, style = MaterialTheme.typography.bodySmall)
            }
            ConnectionChip(ui.connection)
        }

        when (ui.connection) {
            Connection.UNAUTHORIZED -> Banner(
                "This phone is no longer accepted by the board (it was set up with another phone). Run setup again from Settings.",
                GuardianColors.Scam,
            )
            Connection.OFFLINE -> Banner("The board does not answer. Check that it has power and Wi-Fi.", GuardianColors.Warn)
            else -> {}
        }

        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Guarding", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (live.guarding) "Listening to calls near the board" else "Off: the board is not listening",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = live.guarding,
                    onCheckedChange = onGuard,
                    enabled = !ui.busy && live.known && ui.connection != Connection.OFFLINE,
                )
            }
        }
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        VerdictLight(live)

        live.wordMatch?.let { wm ->
            Banner("Family word heard: ${wm.member.ifEmpty { "a family member" }}", GuardianColors.Safe)
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Latest words", style = MaterialTheme.typography.titleSmall)
                Text(
                    live.transcript.ifEmpty { if (live.guarding) "Waiting for speech…" else "Nothing yet" },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Board", style = MaterialTheme.typography.titleSmall)
                val wifi = live.wifi
                InfoRow(
                    "Wi-Fi",
                    if (wifi == null || wifi.ssid.isEmpty()) "Not connected"
                    else "${wifi.ssid} · ${wifi.rssi} dBm (${signal(wifi.rssi)}) · ${if (wifi.source == "fallback") "built-in network" else "saved network"}",
                )
                InfoRow(
                    "Speech-to-text",
                    when (live.stt) {
                        "connected" -> "Connected"
                        "connecting" -> "Connecting"
                        else -> "Closed"
                    },
                )
                if (live.fw.isNotEmpty()) InfoRow("Firmware", live.fw)
                if (live.degraded.isNotEmpty()) {
                    InfoRow("Switched off (low memory)", live.degraded.joinToString { degradedLabel(it) }, GuardianColors.Warn)
                }
            }
        }
    }
}

@Composable
private fun VerdictLight(live: LiveState) {
    val target = if (live.guarding || live.verdict != Verdict.NONE) GuardianColors.verdict(live.verdict) else GuardianColors.None
    val color by animateColorAsState(target, label = "verdict")
    val label = verdictLabel(live.verdict)
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors()) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(150.dp).background(color, CircleShape).semantics { contentDescription = "Verdict: $label" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (live.verdict) {
                        Verdict.SCAM -> "SCAM"
                        Verdict.SAFE -> "SAFE"
                        Verdict.UNSURE -> "UNSURE"
                        Verdict.AI_ERROR -> "AI ERROR"
                        else -> if (live.guarding) "LISTENING" else "OFF"
                    },
                    color = if (live.verdict == Verdict.UNSURE || live.verdict == Verdict.AI_ERROR) Color.Black else Color.White,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (live.reason.isNotEmpty()) {
                Text(live.reason, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            }
            if (live.latched && live.guarding) {
                Text(
                    "Stays red until guarding is turned off",
                    style = MaterialTheme.typography.labelMedium,
                    color = GuardianColors.Scam,
                )
            }
            if (live.verdict == Verdict.SCAM) {
                Text(
                    "Do not give out codes or move money. Hang up and call back on a number you know.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Banner(text: String, color: Color) {
    Box(Modifier.fillMaxWidth().background(color.copy(alpha = 0.15f), MaterialTheme.shapes.medium).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(color, CircleShape))
            Spacer(Modifier.size(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, color: Color = Color.Unspecified) {
    Row {
        Text(label, Modifier.weight(0.4f), style = MaterialTheme.typography.bodySmall)
        Text(value, Modifier.weight(0.6f), style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

private fun signal(rssi: Int) = when {
    rssi >= -55 -> "strong"
    rssi >= -67 -> "good"
    rssi >= -75 -> "weak"
    else -> "very weak"
}

private fun degradedLabel(feature: String) = when (feature) {
    "ws_server" -> "new live connections"
    "http" -> "app requests"
    "event_log" -> "event history"
    else -> feature
}
