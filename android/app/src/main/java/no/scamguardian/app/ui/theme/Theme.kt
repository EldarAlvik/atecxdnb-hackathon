package no.scamguardian.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import no.scamguardian.app.board.Verdict

object GuardianColors {
    val Safe = Color(0xFF2E7D32)
    val Unsure = Color(0xFFF9A825)
    val Scam = Color(0xFFD32F2F)
    val None = Color(0xFF9E9E9E)
    val Live = Color(0xFF2E7D32)
    val Warn = Color(0xFFEF6C00)

    fun verdict(v: String): Color = when (v) {
        Verdict.SAFE -> Safe
        Verdict.UNSURE, Verdict.AI_ERROR -> Unsure
        Verdict.SCAM -> Scam
        else -> None
    }
}

private val Light = lightColorScheme(
    primary = Color(0xFF00658E),
    secondary = Color(0xFF4E616D),
    tertiary = Color(0xFF2E7D32),
    error = Color(0xFFBA1A1A),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD0FF),
    secondary = Color(0xFFB5C9D7),
    tertiary = Color(0xFF8BD58F),
)

@Composable
fun GuardianTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
