package dev.secondscreen.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.secondscreen.data.calendar.CalEvent
import dev.secondscreen.ui.LocalContainer
import dev.secondscreen.ui.LocalNow
import dev.secondscreen.ui.theme.Ink
import dev.secondscreen.ui.theme.Type
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import dev.secondscreen.i18n.S
import dev.secondscreen.i18n.capitalize
import dev.secondscreen.i18n.formatter

private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

// ---- logic ---------------------------------------------------------------------------------

fun upcoming(events: List<CalEvent>, now: Instant) = events.filter { it.end > now }

/** Ongoing, or starting within 30 minutes. All-day events are never urgent. */
fun isUrgent(e: CalEvent, now: Instant): Boolean =
    !e.allDay && (e.isOngoing(now) || Duration.between(now, e.start).toMinutes() in 0..30)

fun whenLabel(e: CalEvent, now: ZonedDateTime): String {
    val zone = now.zone
    val today = now.toLocalDate()
    val startDay = e.start.atZone(zone).toLocalDate()
    if (e.allDay) return if (!startDay.isAfter(today)) S.today else dayName(startDay, today)
    val nowI = now.toInstant()
    if (e.isOngoing(nowI)) return S.ongoingUntil(e.end.atZone(zone).format(HHMM))
    val mins = Duration.between(nowI, e.start).toMinutes()
    if (mins < 60) return S.inMinutes(mins.coerceAtLeast(1))
    val time = e.start.atZone(zone).format(HHMM)
    return if (startDay == today) time else "${dayName(startDay, today)} $time"
}

private fun dayName(d: LocalDate, today: LocalDate): String = when (d) {
    today -> S.today
    today.plusDays(1) -> S.tomorrow
    else -> S.capitalize(d.format(S.formatter("EEE")))
}

private fun eventsOn(events: List<CalEvent>, day: LocalDate, now: ZonedDateTime): List<CalEvent> {
    val zone = now.zone
    val start = day.atStartOfDay(zone).toInstant()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant()
    return events.filter { e ->
        if (e.allDay) e.start < end && e.end > start
        else e.start.atZone(zone).toLocalDate() == day || (day == now.toLocalDate() && e.isOngoing(now.toInstant()))
    }.sortedWith(compareBy<CalEvent> { !it.allDay }.thenBy { it.start })
}

// ---- widgets -------------------------------------------------------------------------------

@Composable
fun NextEventWidget(size: WidgetSize) {
    val state by LocalContainer.current.calendar.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        WidgetLabel(S.nextLabel)
        Spacer(Modifier.height(8.dp))
        WithData(state) { events ->
            val now = LocalNow.current
            val next = upcoming(events, now.toInstant()).firstOrNull { !it.allDay }
            if (next == null) {
                Text(S.nothingPlanned, style = Type.bodySmall)
            } else {
                val urgent = isUrgent(next, now.toInstant())
                Column {
                    Text(
                        whenLabel(next, now),
                        style = Type.numSmall,
                        color = if (urgent) Ink.Accent else Ink.Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        next.title,
                        style = Type.body.copy(fontSize = if (size == WidgetSize.Small) 17.sp else 22.sp, lineHeight = if (size == WidgetSize.Small) 21.sp else 27.sp),
                        maxLines = if (size == WidgetSize.Small) 3 else 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                    next.location?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, style = Type.bodySmall, color = Ink.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun NextEventsWidget(count: Int, size: WidgetSize) {
    val state by LocalContainer.current.calendar.state.collectAsState()
    val n = when (size) {
        WidgetSize.Small -> minOf(count, 2)
        WidgetSize.Medium -> count
        WidgetSize.Large -> maxOf(count, 8)
        WidgetSize.Huge -> maxOf(count, 10)
    }
    Column(Modifier.fillMaxSize()) {
        WidgetLabel(S.upcomingLabel)
        Spacer(Modifier.height(10.dp))
        WithData(state) { events ->
            val now = LocalNow.current
            val list = upcoming(events, now.toInstant()).take(n)
            if (list.isEmpty()) {
                Text(S.nothingPlanned, style = Type.bodySmall)
            } else {
                Column(Modifier.clipToBounds(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    list.forEach { EventRow(it, now, showDay = true) }
                }
            }
        }
    }
}

@Composable
fun AgendaWidget(size: WidgetSize) {
    val state by LocalContainer.current.calendar.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        WidgetLabel(S.agendaWeekLabel)
        Spacer(Modifier.height(8.dp))
        WithData(state) { events ->
            val now = LocalNow.current
            val today = now.toLocalDate()
            val days = (0L until 7L).map { today.plusDays(it) }
                .map { it to eventsOn(events, it, now) }
                .filter { (d, list) -> d == today || list.isNotEmpty() }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                days.forEach { (day, list) ->
                    item(key = "h$day") {
                        val header = when (day) {
                            today -> S.today
                            today.plusDays(1) -> S.tomorrow
                            else -> S.capitalize(day.format(S.formatter(S.longDatePattern)))
                        }
                        Text(
                            header,
                            style = Type.label.copy(fontSize = 12.sp),
                            color = Ink.Dim,
                            modifier = Modifier.padding(top = if (day == today) 0.dp else 8.dp),
                        )
                    }
                    if (list.isEmpty()) {
                        item(key = "e$day") { Text(S.nothingPlanned, style = Type.bodySmall, color = Ink.Faint) }
                    }
                    items(list, key = { "$day${it.start}${it.title}" }) {
                        EventRow(it, now, showDay = false, large = size == WidgetSize.Huge)
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: CalEvent, now: ZonedDateTime, showDay: Boolean, large: Boolean = false) {
    val nowI = now.toInstant()
    val urgent = isUrgent(e, nowI)
    val past = !e.allDay && e.end <= nowI
    val zone = now.zone
    val startDay = e.start.atZone(zone).toLocalDate()
    val today = now.toLocalDate()
    val time = when {
        e.allDay -> S.allDayShort
        e.isOngoing(nowI) -> S.ongoingShort
        else -> e.start.atZone(zone).format(HHMM)
    }
    val dayPrefix = if (showDay && !startDay.isEqual(today) && !(e.allDay && startDay.isBefore(today))) {
        if (startDay == today.plusDays(1)) S.tomorrowShort else startDay.format(S.formatter("EEE"))
    } else null

    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.width(if (showDay) 76.dp else 50.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (urgent) {
                    Box(Modifier.size(5.dp).background(Ink.Accent, CircleShape))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    listOfNotNull(dayPrefix, time).joinToString(" "),
                    style = Type.numSmall,
                    color = when {
                        urgent -> Ink.Accent
                        past -> Ink.Faint
                        else -> Ink.Dim
                    },
                    maxLines = 1,
                )
            }
        }
        Text(
            e.title,
            style = if (large) Type.body.copy(fontSize = 17.sp) else Type.body,
            color = if (past) Ink.Faint else Ink.Fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
