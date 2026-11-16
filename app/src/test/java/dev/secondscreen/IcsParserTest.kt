package dev.secondscreen

import dev.secondscreen.data.calendar.IcsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone

class IcsParserTest {
    private val paris = ZoneId.of("Europe/Paris")

    @Before
    fun setUp() {
        TimeZone.setDefault(TimeZone.getTimeZone(paris))
    }

    private val ics = """
        BEGIN:VCALENDAR
        VERSION:2.0
        PRODID:-//Google Inc//Google Calendar 70.9054//EN
        X-WR-TIMEZONE:Europe/Paris
        BEGIN:VTIMEZONE
        TZID:Europe/Paris
        X-LIC-LOCATION:Europe/Paris
        BEGIN:DAYLIGHT
        TZOFFSETFROM:+0100
        TZOFFSETTO:+0200
        TZNAME:CEST
        DTSTART:19700329T020000
        RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=-1SU
        END:DAYLIGHT
        BEGIN:STANDARD
        TZOFFSETFROM:+0200
        TZOFFSETTO:+0100
        TZNAME:CET
        DTSTART:19701025T030000
        RRULE:FREQ=YEARLY;BYMONTH=10;BYDAY=-1SU
        END:STANDARD
        END:VTIMEZONE
        BEGIN:VEVENT
        DTSTART;TZID=Europe/Paris:20261005T090000
        DTEND;TZID=Europe/Paris:20261005T093000
        RRULE:FREQ=WEEKLY;BYDAY=MO
        EXDATE;TZID=Europe/Paris:20261109T090000
        UID:weekly@test
        SUMMARY:Standup
        END:VEVENT
        BEGIN:VEVENT
        DTSTART;TZID=Europe/Paris:20261123T100000
        DTEND;TZID=Europe/Paris:20261123T103000
        RECURRENCE-ID;TZID=Europe/Paris:20261123T090000
        UID:weekly@test
        SUMMARY:Standup (moved)
        END:VEVENT
        BEGIN:VEVENT
        DTSTART;VALUE=DATE:20261120
        DTEND;VALUE=DATE:20261122
        UID:allday@test
        SUMMARY:Weekend away
        END:VEVENT
        BEGIN:VEVENT
        DTSTART:20261118T140000Z
        DTEND:20261118T150000Z
        UID:utc@test
        SUMMARY:Call
        LOCATION:Zoom
        END:VEVENT
        BEGIN:VEVENT
        DTSTART:20261119T140000Z
        DTEND:20261119T150000Z
        UID:cancelled@test
        STATUS:CANCELLED
        SUMMARY:Cancelled thing
        END:VEVENT
        BEGIN:VEVENT
        DTSTART;TZID=Europe/Paris:20261231T090000
        DTEND;TZID=Europe/Paris:20261231T100000
        UID:outside@test
        SUMMARY:Out of window
        END:VEVENT
        END:VCALENDAR
    """.trimIndent().replace("\n", "\r\n")

    private fun parse() = IcsParser.parse(
        ics,
        from = LocalDate.of(2026, 11, 1).atStartOfDay(paris).toInstant(),
        to = LocalDate.of(2026, 12, 1).atStartOfDay(paris).toInstant(),
        zone = paris,
    )

    private fun local(i: java.time.Instant) = LocalDateTime.ofInstant(i, paris)

    @Test
    fun expandsWeeklyRecurrenceWithExdateAndOverride() {
        val standups = parse().filter { it.title.startsWith("Standup") }
        // Mondays in Nov 2026: 2, 9 (EXDATE), 16, 23 (moved to 10:00), 30
        assertEquals(
            listOf(
                LocalDateTime.of(2026, 11, 2, 9, 0),
                LocalDateTime.of(2026, 11, 16, 9, 0),
                LocalDateTime.of(2026, 11, 23, 10, 0),
                LocalDateTime.of(2026, 11, 30, 9, 0),
            ),
            standups.map { local(it.start) },
        )
        assertEquals("Standup (moved)", standups[2].title)
        assertTrue(standups.all { java.time.Duration.between(it.start, it.end).toMinutes() == 30L })
    }

    @Test
    fun handlesAllDayUtcAndCancelled() {
        val events = parse()
        val away = events.single { it.title == "Weekend away" }
        assertTrue(away.allDay)
        assertEquals(LocalDateTime.of(2026, 11, 20, 0, 0), local(away.start))
        assertEquals(LocalDateTime.of(2026, 11, 22, 0, 0), local(away.end))

        val call = events.single { it.title == "Call" }
        assertEquals(LocalDateTime.of(2026, 11, 18, 15, 0), local(call.start))
        assertEquals("Zoom", call.location)

        assertTrue(events.none { it.title == "Cancelled thing" })
        assertTrue(events.none { it.title == "Out of window" })
    }
}
