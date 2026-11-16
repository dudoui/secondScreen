package dev.secondscreen.data.calendar

import biweekly.Biweekly
import biweekly.component.VEvent
import biweekly.util.ICalDate
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.TimeZone
import dev.secondscreen.i18n.S

data class CalEvent(
    val title: String,
    val start: Instant,
    val end: Instant,
    val allDay: Boolean,
    val location: String? = null,
    val calendarIndex: Int = 0,
) {
    fun isOngoing(now: Instant) = start <= now && end > now
}

/** Parses an iCalendar feed and expands recurring events into concrete occurrences. */
object IcsParser {
    private const val MAX_OCCURRENCES_PER_EVENT = 400

    fun parse(
        text: String,
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
        calendarIndex: Int = 0,
    ): List<CalEvent> {
        val out = mutableListOf<CalEvent>()
        val localTz = TimeZone.getDefault() // biweekly reads DATE values in the JVM default zone
        for (ical in Biweekly.parse(text).all()) {
            val tzInfo = ical.timezoneInfo
            val events = ical.events

            // Occurrences replaced (or cancelled) by a RECURRENCE-ID override.
            val overridden = events.mapNotNull { ev ->
                val rid = ev.recurrenceId?.value ?: return@mapNotNull null
                occurrenceKey(ev.uid?.value, rid, localTz)
            }.toSet()

            for (ev in events) {
                if (ev.status?.isCancelled == true) continue
                val ds = ev.dateStart?.value ?: continue
                val allDay = !ds.hasTime()
                val title = ev.summary?.value?.trim().orEmpty().ifEmpty { S.untitledEvent }
                val location = ev.location?.value?.trim()?.takeIf { it.isNotEmpty() }

                fun emit(startDate: Date) {
                    val (s, e) = if (allDay) {
                        val day = startDate.toLocalDate(localTz)
                        val days = allDayLength(ev, ds, localTz)
                        day.atStartOfDay(zone).toInstant() to day.plusDays(days).atStartOfDay(zone).toInstant()
                    } else {
                        val s = startDate.toInstant()
                        s to s.plusMillis(timedLengthMs(ev, ds))
                    }
                    if (e > from && s < to) out += CalEvent(title, s, e, allDay, location, calendarIndex)
                }

                val recurring = ev.recurrenceRule != null || ev.recurrenceDates.isNotEmpty()
                if (!recurring || ev.recurrenceId != null) {
                    emit(ds)
                    continue
                }

                val tz = tzInfo.getTimezone(ev.dateStart)?.timeZone
                    ?: if (allDay || tzInfo.isFloating(ev.dateStart)) localTz else TimeZone.getTimeZone("UTC")
                val it = ev.getDateIterator(tz)
                // Jump close to the window (minus a generous margin for long events).
                it.advanceTo(Date.from(from.minusSeconds(40L * 24 * 3600)))
                var n = 0
                while (it.hasNext() && n++ < MAX_OCCURRENCES_PER_EVENT) {
                    val d = it.next()
                    if (!d.toInstant().isBefore(to)) break
                    if (occurrenceKey(ev.uid?.value, ICalDate(d, ds.hasTime()), localTz) in overridden) continue
                    emit(d)
                }
            }
        }
        return out.sortedWith(compareBy<CalEvent> { it.start }.thenBy { !it.allDay }.thenBy { it.title })
    }

    private fun occurrenceKey(uid: String?, date: ICalDate, localTz: TimeZone): String =
        if (date.hasTime()) "$uid|t|${date.time}" else "$uid|d|${date.toLocalDate(localTz)}"

    private fun allDayLength(ev: VEvent, start: ICalDate, localTz: TimeZone): Long {
        val end = ev.dateEnd?.value
        if (end != null) {
            val days = java.time.temporal.ChronoUnit.DAYS.between(start.toLocalDate(localTz), end.toLocalDate(localTz))
            return days.coerceAtLeast(1)
        }
        val dur = ev.duration?.value
        if (dur != null) return (dur.toMillis() / 86_400_000L).coerceAtLeast(1)
        return 1
    }

    private fun timedLengthMs(ev: VEvent, start: ICalDate): Long {
        ev.dateEnd?.value?.let { return (it.time - start.time).coerceAtLeast(0) }
        ev.duration?.value?.let { return it.toMillis() }
        return 0
    }

    private fun Date.toLocalDate(tz: TimeZone): LocalDate = toInstant().atZone(tz.toZoneId()).toLocalDate()
}
