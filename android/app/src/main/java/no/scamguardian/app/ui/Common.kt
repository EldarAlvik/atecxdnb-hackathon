package no.scamguardian.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date
import no.scamguardian.app.board.BoardException
import no.scamguardian.app.monitor.Connection
import no.scamguardian.app.ui.theme.GuardianColors

@Composable
fun ConnectionChip(connection: Connection, modifier: Modifier = Modifier) {
    val (label, color) = when (connection) {
        Connection.LIVE -> "Live" to GuardianColors.Live
        Connection.RECONNECTING -> "Reconnecting" to GuardianColors.Unsure
        Connection.OFFLINE -> "Offline" to GuardianColors.Scam
        Connection.UNAUTHORIZED -> "Not accepted" to GuardianColors.Scam
        Connection.IDLE -> "Waiting" to GuardianColors.None
    }
    Surface(shape = MaterialTheme.shapes.small, tonalElevation = 2.dp, modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** A message for the user. Never includes request bodies (which may hold family words). */
fun errorMessage(e: Throwable): String = when (e) {
    is BoardException.Unauthorized -> "The board did not accept this phone. Run setup again from Settings."
    is BoardException.Unreachable -> "Cannot reach the board. Is the phone on the same Wi-Fi?"
    is BoardException -> e.message ?: "The board refused the request"
    else -> e.message ?: e.javaClass.simpleName
}

fun formatTime(unixSeconds: Long): String =
    if (unixSeconds <= 0) "" else DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(unixSeconds * 1000))

fun formatTimeMs(ms: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(ms))
