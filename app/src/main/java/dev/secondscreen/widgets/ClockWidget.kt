package dev.secondscreen.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.secondscreen.ui.LocalContainer
import dev.secondscreen.ui.LocalNow
import dev.secondscreen.ui.theme.Ink
import dev.secondscreen.ui.theme.Type
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")
private val LONG_DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
private val SHORT_DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

@Composable
fun ClockWidget(size: WidgetSize) {
    val now = LocalNow.current
    val time = now.format(HHMM)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth
        val h = maxHeight
        val centered = size != WidgetSize.Medium
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
        ) {
            val (timeShare, dateSize) = when (size) {
                WidgetSize.Small -> 0.55f to 13.sp
                WidgetSize.Medium -> 0.66f to 16.sp
                WidgetSize.Large -> 0.5f to 20.sp
                WidgetSize.Huge -> 0.6f to 24.sp
            }
            Text(time, style = Type.num.sized(fitFontSize(w, h * timeShare, time.length)), maxLines = 1)
            val date = now.format(if (size == WidgetSize.Small) SHORT_DATE else LONG_DATE)
                .replaceFirstChar { it.titlecase(Locale.FRENCH) }
            Text(date, fontSize = dateSize, color = Ink.Dim, maxLines = 1)
            if (size == WidgetSize.Large || size == WidgetSize.Huge) {
                Spacer(Modifier.height(if (size == WidgetSize.Huge) 20.dp else 12.dp))
                NextEventLine()
            }
        }
    }
}

/** One quiet line under the big clock: the next event, red when it is imminent. */
@Composable
private fun NextEventLine() {
    val state by LocalContainer.current.calendar.state.collectAsState()
    val events = state.data ?: return
    val now = LocalNow.current
    val next = upcoming(events, now.toInstant()).firstOrNull { !it.allDay } ?: return
    val urgent = isUrgent(next, now.toInstant())
    Text(
        "${whenLabel(next, now)} · ${next.title}",
        style = Type.body,
        color = if (urgent) Ink.Accent else Ink.Faint,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
