package dev.secondscreen.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Settings(
    val icsUrls: List<String> = emptyList(),
    val city: String = "Paris",
    val lat: Double = 48.8566,
    val lon: Double = 2.3522,
    val rssUrl: String = "https://www.lemonde.fr/rss/une.xml",
    val nightStartHour: Int = 23,
    val nightEndHour: Int = 7,
    /** Address of the Mac running mac-helper/nowplaying_server.py, e.g. 192.168.1.40:8765. */
    val macHost: String = "",
    /** Album/podcast covers in colour (tap a cover to toggle) instead of greyscale. */
    val coverInColor: Boolean = false,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object K {
        val ics = stringPreferencesKey("ics_urls")
        val city = stringPreferencesKey("city")
        val lat = doublePreferencesKey("lat")
        val lon = doublePreferencesKey("lon")
        val rss = stringPreferencesKey("rss_url")
        val nightStart = intPreferencesKey("night_start")
        val nightEnd = intPreferencesKey("night_end")
        val macHost = stringPreferencesKey("mac_host")
        val coverInColor = booleanPreferencesKey("cover_in_color")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        val d = Settings()
        Settings(
            icsUrls = p[K.ics]?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: d.icsUrls,
            city = p[K.city] ?: d.city,
            lat = p[K.lat] ?: d.lat,
            lon = p[K.lon] ?: d.lon,
            rssUrl = p[K.rss] ?: d.rssUrl,
            nightStartHour = p[K.nightStart] ?: d.nightStartHour,
            nightEndHour = p[K.nightEnd] ?: d.nightEndHour,
            macHost = p[K.macHost] ?: d.macHost,
            coverInColor = p[K.coverInColor] ?: d.coverInColor,
        )
    }

    suspend fun setCoverInColor(value: Boolean) {
        context.dataStore.edit { it[K.coverInColor] = value }
    }

    suspend fun save(s: Settings) {
        context.dataStore.edit { p ->
            p[K.ics] = s.icsUrls.joinToString("\n")
            p[K.city] = s.city
            p[K.lat] = s.lat
            p[K.lon] = s.lon
            p[K.rss] = s.rssUrl
            p[K.nightStart] = s.nightStartHour
            p[K.nightEnd] = s.nightEndHour
            p[K.macHost] = s.macHost
            p[K.coverInColor] = s.coverInColor
        }
    }
}
