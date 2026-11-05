package no.scamguardian.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import no.scamguardian.app.board.BoardEvent
import no.scamguardian.app.board.EventKeys
import no.scamguardian.app.board.Verdict
import no.scamguardian.app.board.describe
import no.scamguardian.app.board.parseValueJson
import no.scamguardian.app.data.GapReason
import no.scamguardian.app.data.db.EventDao
import no.scamguardian.app.data.db.EventEntity
import no.scamguardian.app.data.db.GapEntity
import no.scamguardian.app.monitor.BoardMonitor
import no.scamguardian.app.monitor.Connection
import no.scamguardian.app.ui.ConnectionChip
import no.scamguardian.app.ui.errorMessage
import no.scamguardian.app.ui.formatTime
import no.scamguardian.app.ui.formatTimeMs
import no.scamguardian.app.ui.theme.GuardianColors

sealed interface FeedItem {
    val sortEpoch: Int
    val sortSeq: Double

    data class Event(val entity: EventEntity) : FeedItem {
        val event = BoardEvent(entity.seq, entity.ts, entity.key, entity.eventType, parseValueJson(entity.valueJson))
        override val sortEpoch get() = entity.epoch
        override val sortSeq get() = entity.seq.toDouble()
    }

    data class Gap(val gap: GapEntity) : FeedItem {
        override val sortEpoch get() = gap.epoch
        override val sortSeq get() = gap.toSeq + 0.5
    }
}

/** Newest first. A gap sits just above the oldest event the board still had. */
fun buildFeed(events: List<EventEntity>, gaps: List<GapEntity>, callOnly: Boolean): List<FeedItem> {
    val callKeys = setOf(EventKeys.GUARD, EventKeys.TRANSCRIPT, EventKeys.VERDICT, EventKeys.WORD_MATCH)
    val ev = events.filter { !callOnly || it.key in callKeys }.map { FeedItem.Event(it) }
    return (ev + gaps.map { FeedItem.Gap(it) })
        .sortedWith(compareByDescending<FeedItem> { it.sortEpoch }.thenByDescending { it.sortSeq })
}

data class EventsUi(
    val items: List<FeedItem> = emptyList(),
    val callOnly: Boolean = false,
    val connection: Connection = Connection.IDLE,
    val syncing: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class EventsViewModel @Inject constructor(
    dao: EventDao,
    private val monitor: BoardMonitor,
) : ViewModel() {
    private val local = MutableStateFlow(EventsUi())

    val ui: StateFlow<EventsUi> = combine(dao.recent(1000), dao.gaps(200), monitor.connection, local) { ev, gaps, c, loc ->
        loc.copy(items = buildFeed(ev, gaps, loc.callOnly), connection = c)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUi())

    fun setCallOnly(on: Boolean) = local.update { it.copy(callOnly = on) }

    fun syncNow() {
        local.update { it.copy(syncing = true, message = null) }
        viewModelScope.launch {
            val r = runCatching { monitor.syncNow() }
            local.update {
                it.copy(
                    syncing = false,
                    message = r.fold(
                        { s -> if (s.added == 0) "Up to date" else "${s.added} new event(s) from the board" },
                        { e -> errorMessage(e) },
                    ),
                )
            }
        }
    }
}

@Composable
fun EventsScreen(vm: EventsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Events", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            ConnectionChip(ui.connection)
            IconButton(onClick = vm::syncNow) { Icon(Icons.Filled.Refresh, "Sync from the board") }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !ui.callOnly, onClick = { vm.setCallOnly(false) }, label = { Text("All") })
            FilterChip(selected = ui.callOnly, onClick = { vm.setCallOnly(true) }, label = { Text("Calls only") })
        }
        if (ui.syncing) LinearProgressIndicator(Modifier.fillMaxWidth())
        ui.message?.let { Text(it, Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall) }
        if (ui.items.isEmpty()) {
            Text("No events yet. They appear here live and are fetched from the board's history.", Modifier.padding(16.dp))
        }
        LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            items(ui.items, key = {
                when (it) {
                    is FeedItem.Event -> "e${it.entity.epoch}_${it.entity.seq}"
                    is FeedItem.Gap -> "g${it.gap.id}"
                }
            }) { item ->
                when (item) {
                    is FeedItem.Event -> EventRow(item)
                    is FeedItem.Gap -> GapRow(item.gap)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun EventRow(item: FeedItem.Event) {
    val e = item.event
    val text = e.describe()
    val color = when (e.key) {
        EventKeys.VERDICT -> GuardianColors.verdict(e.str("verdict") ?: Verdict.NONE)
        EventKeys.WORD_MATCH -> GuardianColors.Safe
        EventKeys.ERROR, EventKeys.DEGRADED -> GuardianColors.Warn
        EventKeys.GUARD -> MaterialTheme.colorScheme.primary
        else -> Color.LightGray
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 5.dp).size(10.dp).background(color, CircleShape))
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(text.title, style = MaterialTheme.typography.bodyLarge)
            text.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                (formatTime(e.ts).ifEmpty { "received " + formatTimeMs(item.entity.receivedAt) }) + " · #${e.seq}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GapRow(g: GapEntity) {
    Box(Modifier.fillMaxWidth().background(GuardianColors.Warn.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(
            when (g.reason) {
                GapReason.RESTARTED -> "The board's event history started over here (new or reset board)."
                else -> if (g.fromSeq == g.toSeq) {
                    "Event #${g.fromSeq} was gone from the board before the phone could fetch it."
                } else {
                    "Events #${g.fromSeq}–#${g.toSeq} were gone from the board before the phone could fetch them."
                }
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
