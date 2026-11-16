package dev.secondscreen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The whole palette. Greyscale, plus two accents used sparingly. */
object Ink {
    val Bg = Color(0xFF000000)
    val Card = Color(0xFF080808)
    val Line = Color(0xFF1E1E1E)
    val Fg = Color(0xFFEDEDED)
    val Dim = Color(0xFFEDEDED).copy(alpha = 0.62f)
    val Faint = Color(0xFFEDEDED).copy(alpha = 0.36f)
    val Ghost = Color(0xFFEDEDED).copy(alpha = 0.14f)

    /** Something needs attention now: event imminent/ongoing, storm, rain within the hour. */
    val Accent = Color(0xFFFF4D4D)
    /** Worth knowing: rain likely today, stale data. */
    val Warn = Color(0xFFFFB020)
}

object Type {
    private val tnum = "tnum"
    val label = TextStyle(fontSize = 11.sp, letterSpacing = 1.6.sp, fontWeight = FontWeight.Medium, color = Ink.Faint)
    val body = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, color = Ink.Fg)
    val bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, color = Ink.Dim)
    val num = TextStyle(fontFeatureSettings = tnum, fontWeight = FontWeight.Light, color = Ink.Fg)
    val numSmall = TextStyle(fontSize = 13.sp, fontFeatureSettings = tnum, color = Ink.Dim, fontFamily = FontFamily.Default)
}

@Composable
fun SecondScreenTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Ink.Fg,
            onPrimary = Ink.Bg,
            background = Ink.Bg,
            onBackground = Ink.Fg,
            surface = Ink.Bg,
            onSurface = Ink.Fg,
            surfaceVariant = Ink.Card,
            onSurfaceVariant = Ink.Dim,
            outline = Ink.Line,
            error = Ink.Accent,
        ),
    ) {
        Surface(color = Ink.Bg, contentColor = Ink.Fg, content = content)
    }
}
