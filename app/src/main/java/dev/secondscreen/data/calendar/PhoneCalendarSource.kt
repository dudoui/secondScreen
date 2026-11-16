package dev.secondscreen.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import dev.secondscreen.data.Loadable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/**
 * Reads events from the calendars synced on the phone (e.g. the Google account), through Android's
 * calendar provider. Refreshes when the provider reports a change, and every 5 minutes anyway.
 */
class PhoneCalendarSource(private val context: Context) {
    private val _state = MutableStateFlow(Loadable<List<CalEvent>>())
    val state: StateFlow<Loadable<List<CalEvent>>> = _state.asStateFlow()

    private val wake = Channel<Unit>(Channel.CONFLATED)

    fun hasPermission() =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Ask for a re-read soon (e.g. right after the permission was granted). */
    fun refresh() {
        wake.trySend(Unit)
    }

    fun start(scope: CoroutineScope) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }
        var observing = false
        scope.launch(Dispatchers.IO) {
            while (true) {
                if (hasPermission()) {
                    if (!observing) {
                        runCatching {
                            context.contentResolver.registerContentObserver(Events.CONTENT_URI, true, observer)
                            observing = true
                        }
                    }
                    _state.value = try {
                        Loadable(query(), System.currentTimeMillis())
                    } catch (e: Exception) {
                        _state.value.copy(error = e.message ?: e.javaClass.simpleName)
                    }
                } else {
                    _state.value = Loadable(error = PERMISSION_MISSING)
                }
                withTimeoutOrNull(5 * 60_000L) { wake.receive() }
            }
        }
    }

    private fun query(): List<CalEvent> {
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now(zone).atStartOfDay(zone).toInstant()
        val to = from.plus(15, ChronoUnit.DAYS)
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from.minus(1, ChronoUnit.DAYS).toEpochMilli())
            ContentUris.appendId(it, to.toEpochMilli())
        }.build()
        val projection = arrayOf(
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.EVENT_LOCATION,
            Instances.CALENDAR_ID,
            Instances.STATUS,
            Instances.SELF_ATTENDEE_STATUS,
        )
        val selection = "${Instances.VISIBLE} = 1"
        val out = mutableListOf<CalEvent>()
        context.contentResolver.query(uri, projection, selection, null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                if (!c.isNull(6) && c.getInt(6) == Events.STATUS_CANCELED) continue
                if (!c.isNull(7) && c.getInt(7) == android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED) continue
                val allDay = c.getInt(3) == 1
                var start = Instant.ofEpochMilli(c.getLong(1))
                var end = Instant.ofEpochMilli(c.getLong(2))
                if (allDay) {
                    // All-day instances are stored as UTC midnights; re-anchor them to local midnights.
                    start = start.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant()
                    end = end.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant()
                }
                if (end <= start) end = if (allDay) start.plus(1, ChronoUnit.DAYS) else start
                if (end <= from || start >= to) continue
                out += CalEvent(
                    title = c.getString(0)?.trim().orEmpty().ifEmpty { "(sans titre)" },
                    start = start,
                    end = end,
                    allDay = allDay,
                    location = c.getString(4)?.trim()?.takeIf { it.isNotEmpty() },
                    calendarIndex = c.getInt(5),
                )
            }
        }
        return out
    }

    companion object {
        const val PERMISSION_MISSING = "Accès à l'agenda non autorisé (réglages Android → Applis → Second Screen)"
    }
}
