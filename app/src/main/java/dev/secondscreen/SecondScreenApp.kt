package dev.secondscreen

import android.app.Application
import android.content.Context
import dev.secondscreen.config.LayoutStore
import dev.secondscreen.data.calendar.CalendarSource
import dev.secondscreen.data.calendar.MergedCalendar
import dev.secondscreen.data.calendar.PhoneCalendarSource
import dev.secondscreen.data.nowplaying.NowPlayingSource
import dev.secondscreen.data.rss.RssSource
import dev.secondscreen.data.weather.WeatherSource
import dev.secondscreen.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

class SecondScreenApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Holds the long-lived data sources. They keep polling for as long as the process lives. */
class AppContainer(context: Context) {
    val settingsStore = SettingsStore(context)
    val layoutStore = LayoutStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val cacheDir = File(context.filesDir, "cache")

    val phoneCalendar = PhoneCalendarSource(context)
    private val icsCalendar = CalendarSource(cacheDir)
    /** What the widgets read: phone calendars + any iCal feeds. */
    val calendar = MergedCalendar(phoneCalendar, icsCalendar, scope)
    val weather = WeatherSource(cacheDir)
    val news = RssSource(cacheDir)
    val nowPlaying = NowPlayingSource()

    init {
        phoneCalendar.start(scope)
        icsCalendar.start(scope, settingsStore.flow)
        weather.start(scope, settingsStore.flow)
        news.start(scope, settingsStore.flow)
        nowPlaying.start(scope, settingsStore.flow)
    }
}
