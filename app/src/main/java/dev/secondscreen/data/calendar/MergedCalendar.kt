package dev.secondscreen.data.calendar

import dev.secondscreen.data.Loadable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Phone calendars + optional iCal feeds, as one sorted list of events. */
class MergedCalendar(phone: PhoneCalendarSource, ics: CalendarSource, scope: CoroutineScope) {
    val state: StateFlow<Loadable<List<CalEvent>>> = combine(phone.state, ics.state) { p, i ->
        val sources = listOf(p, i).filter { it.data != null }
        if (sources.isEmpty()) {
            // Neither has data yet: surface the phone's message (permission) unless still loading.
            Loadable(error = p.error)
        } else {
            val events = sources.flatMap { it.data!! }
                .distinctBy { Triple(it.title, it.start, it.allDay) }
                .sortedBy { it.start }
            Loadable(
                data = events,
                updatedAt = sources.mapNotNull { it.updatedAt }.minOrNull(),
                // An unconfigured iCal feed is not an error worth flagging.
                error = sources.firstNotNullOfOrNull { it.error },
            )
        }
    }.stateIn(scope, SharingStarted.Eagerly, Loadable())
}
