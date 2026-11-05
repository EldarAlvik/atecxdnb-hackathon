package no.scamguardian.app.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import no.scamguardian.app.board.BoardConfig
import no.scamguardian.app.board.ConfigRules
import no.scamguardian.app.data.BoardKind
import no.scamguardian.app.data.BoardStore
import no.scamguardian.app.data.PairedBoard
import no.scamguardian.app.data.Pairing
import no.scamguardian.app.monitor.BoardSession
import no.scamguardian.app.monitor.MonitorService
import no.scamguardian.app.ui.errorMessage

data class SettingsUi(
    val saved: BoardConfig? = null,
    val draft: BoardConfig? = null,
    val errors: Map<String, String> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val board: PairedBoard? = null,
    val background: Boolean = true,
) {
    val dirty: Boolean get() = saved != null && draft != null && ConfigRules.patch(saved, draft).isNotEmpty()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val session: BoardSession,
    private val store: BoardStore,
    private val pairing: Pairing,
) : ViewModel() {
    private val local = MutableStateFlow(SettingsUi())

    val ui: StateFlow<SettingsUi> = combine(local, store.paired, store.backgroundMonitoring) { l, b, bg ->
        l.copy(board = b, background = bg)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUi())

    init {
        load()
    }

    fun load() {
        local.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            runCatching { session.requireClient().config() }
                .onSuccess { c -> local.update { it.copy(saved = c, draft = c, busy = false, errors = emptyMap()) } }
                .onFailure { e -> local.update { it.copy(busy = false, error = errorMessage(e)) } }
        }
    }

    fun edit(change: (BoardConfig) -> BoardConfig) = local.update { s ->
        val d = s.draft?.let(change) ?: return@update s
        s.copy(draft = d, errors = ConfigRules.validate(d), message = null)
    }

    fun save() {
        val s = local.value
        val saved = s.saved ?: return
        val draft = s.draft ?: return
        val errors = ConfigRules.validate(draft)
        if (errors.isNotEmpty()) {
            local.update { it.copy(errors = errors) }
            return
        }
        val patch = ConfigRules.patch(saved, draft)
        if (patch.isEmpty()) return
        local.update { it.copy(busy = true, error = null, message = null) }
        viewModelScope.launch {
            runCatching { session.requireClient().updateConfig(patch) }
                .onSuccess { c -> local.update { it.copy(saved = c, draft = c, busy = false, message = "Saved on the board") } }
                .onFailure { e -> local.update { it.copy(busy = false, error = errorMessage(e)) } }
        }
    }

    fun revert() = local.update { it.copy(draft = it.saved, errors = emptyMap()) }

    fun setBackground(on: Boolean) {
        viewModelScope.launch { store.setBackgroundMonitoring(on) }
    }

    /** POST /setup (the board restarts into BLE setup), then [onDone] opens onboarding. */
    fun rerunSetup(onDone: () -> Unit) {
        val board = ui.value.board
        if (board?.kind == BoardKind.SIMULATED) {
            onDone()
            return
        }
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { session.requireClient().startSetup() }
                .onSuccess { local.update { it.copy(busy = false) }; onDone() }
                .onFailure { e -> local.update { it.copy(busy = false, error = errorMessage(e)) } }
        }
    }

    fun forget(context: Context) {
        viewModelScope.launch {
            MonitorService.stop(context.applicationContext)
            pairing.forget()
        }
    }
}

@Composable
fun SettingsScreen(onRerunSetup: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmForget by remember { mutableStateOf(false) }
    var confirmSetup by remember { mutableStateOf(false) }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge)
        if (ui.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        ui.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Board settings", style = MaterialTheme.typography.titleMedium)
                val d = ui.draft
                if (d == null) {
                    Text("Not loaded.")
                    OutlinedButton(onClick = vm::load) { Text("Load from the board") }
                } else {
                    ConfigEditor(d, ui.errors, vm::edit)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = vm::save, enabled = ui.dirty && ui.errors.isEmpty() && !ui.busy) { Text("Save") }
                        OutlinedButton(onClick = vm::revert, enabled = ui.dirty) { Text("Undo changes") }
                        TextButton(onClick = vm::load) { Text("Reload") }
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This phone", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Keep watching in the background")
                        Text(
                            "Keeps the live connection while you are in a call or another app, so scam alerts arrive in time. Shows a permanent notification.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = ui.background, onCheckedChange = vm::setBackground)
                }
                HorizontalDivider()
                ui.board?.let { b ->
                    Text("Board: ${b.deviceId.ifEmpty { "unknown" }}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        when (b.kind) {
                            BoardKind.BLE -> "Paired with Bluetooth · ${b.host}:${b.port} (live ${b.wsPort})"
                            BoardKind.MANUAL -> "Entered by hand · ${b.host}:${b.port} (live ${b.wsPort})"
                            BoardKind.SIMULATED -> "Simulated board inside the app"
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton(onClick = { confirmSetup = true }, enabled = !ui.busy) { Text("Re-run setup") }
                OutlinedButton(onClick = { confirmForget = true }) { Text("Forget board") }
            }
        }
    }

    if (confirmSetup) {
        AlertDialog(
            onDismissRequest = { confirmSetup = false },
            title = { Text("Re-run setup?") },
            text = {
                Text(
                    "The board restarts into Bluetooth setup mode and stops guarding until setup is done. " +
                        "Use this to change its Wi-Fi network or pair a new phone.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmSetup = false; vm.rerunSetup(onRerunSetup) }) { Text("Restart into setup") } },
            dismissButton = { TextButton(onClick = { confirmSetup = false }) { Text("Cancel") } },
        )
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget this board?") },
            text = { Text("The phone deletes the board's address, its token and the event history. The board itself keeps its settings and family words.") },
            confirmButton = { TextButton(onClick = { confirmForget = false; vm.forget(context) }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ConfigEditor(c: BoardConfig, errors: Map<String, String>, edit: ((BoardConfig) -> BoardConfig) -> Unit) {
    IntSlider("Microphone gain", c.micGain, ConfigRules.MIC_GAIN, "×", errors["mic_gain"]) { v -> edit { it.copy(micGain = v) } }
    IntSlider("Time between AI verdicts", c.aiIntervalS, ConfigRules.AI_INTERVAL_S, " s", errors["ai_interval_s"]) { v ->
        edit { it.copy(aiIntervalS = v) }
    }
    IntSlider("Screen brightness", c.screenBrightness, ConfigRules.SCREEN_BRIGHTNESS, " %", errors["screen_brightness"]) { v ->
        edit { it.copy(screenBrightness = v) }
    }

    Text("Call language (from the next guard-on)", style = MaterialTheme.typography.bodyMedium)
    val labels = mapOf("multi" to "Norwegian + English", "no" to "Norwegian", "en" to "English")
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ConfigRules.LANGUAGES.forEachIndexed { i, lang ->
            SegmentedButton(
                selected = c.language == lang,
                onClick = { edit { it.copy(language = lang) } },
                shape = SegmentedButtonDefaults.itemShape(i, ConfigRules.LANGUAGES.size),
                label = { Text(labels[lang] ?: lang, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
    errors["language"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Keep red until guarding stops")
            Text("A scam verdict stays red even if later parts of the call sound fine.", style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = c.redLatch, onCheckedChange = { v -> edit { it.copy(redLatch = v) } })
    }

    Text("Events sent to the app and kept in the board's log", style = MaterialTheme.typography.bodyMedium)
    ConfigRules.DEFAULT_EVENT_KEYS.forEach { key ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = key in c.events,
                onCheckedChange = { on -> edit { it.copy(events = ConfigRules.toggleEvent(it.events, key, on)) } },
            )
            Text(eventLabel(key), style = MaterialTheme.typography.bodyMedium)
        }
    }
    val extra = c.events.filter { it !in ConfigRules.DEFAULT_EVENT_KEYS }
    if (extra.isNotEmpty()) Text("Also on: ${extra.joinToString()}", style = MaterialTheme.typography.bodySmall)
    errors["events"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
}

@Composable
private fun IntSlider(label: String, value: Int, range: IntRange, unit: String, error: String?, onChange: (Int) -> Unit) {
    Column {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text("$value$unit", style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.roundToInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

private fun eventLabel(key: String) = when (key) {
    "guard" -> "Guarding on/off"
    "wifi" -> "Wi-Fi changes"
    "ws" -> "Speech-to-text connection"
    "transcript" -> "Transcript (what was said)"
    "verdict" -> "Verdicts"
    "health" -> "Health (live only, never logged)"
    "word_match" -> "Family word heard"
    "setup" -> "Setup"
    "config" -> "Settings changes"
    "words" -> "Family word changes"
    "degraded" -> "Low-memory warnings"
    "error" -> "Errors"
    else -> key
}
