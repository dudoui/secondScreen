package dev.secondscreen.data.calendar

import dev.secondscreen.data.Http
import dev.secondscreen.data.PollingSource
import dev.secondscreen.settings.Settings
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class CalendarSource(cacheDir: File) : PollingSource<List<String>, List<CalEvent>>(
    name = "calendar",
    cacheDir = cacheDir,
    intervalMs = 5 * 60_000L,
) {
    override val notConfiguredMessage = "Aucun flux iCal"

    override fun keyOf(s: Settings) = s.icsUrls.takeIf { it.isNotEmpty() }

    /** Last text successfully fetched per feed URL, reused when that feed fails. */
    private val lastGood = ConcurrentHashMap<String, String>()

    override fun onCacheLoaded(raw: String, key: List<String>) {
        val parts = raw.split(SPLIT)
        if (parts.size == key.size) key.forEachIndexed { i, url -> lastGood.putIfAbsent(url, parts[i]) }
    }

    /**
     * Feeds are fetched independently: one that fails keeps its last known content instead of
     * blocking the others. Only when every feed fails is the refresh reported as failed.
     */
    override suspend fun fetch(key: List<String>): String {
        var firstError: Exception? = null
        var succeeded = 0
        val texts = key.map { url ->
            try {
                Http.get(url).also { lastGood[url] = it; succeeded++ }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (firstError == null) firstError = e
                lastGood[url].orEmpty()
            }
        }
        firstError?.let { if (succeeded == 0) throw it }
        lastGood.keys.retainAll(key.toSet())
        return texts.joinToString(SPLIT)
    }

    override fun parse(raw: String, key: List<String>): List<CalEvent> {
        val zone = ZoneId.systemDefault()
        val from = Instant.now().atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        val to = from.plus(15, ChronoUnit.DAYS)
        return raw.split(SPLIT).flatMapIndexed { i, text ->
            IcsParser.parse(text, from, to, zone, calendarIndex = i)
        }.sortedBy { it.start }
    }

    private companion object {
        const val SPLIT = "\n#--SECONDSCREEN-CALENDAR-SPLIT--\n"
    }
}
