package dev.secondscreen.data.weather

import dev.secondscreen.data.Http
import dev.secondscreen.data.PollingSource
import dev.secondscreen.settings.Settings
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.net.URLEncoder

class WeatherSource(cacheDir: File) : PollingSource<WeatherSource.Key, Weather>(
    name = "weather",
    cacheDir = cacheDir,
    intervalMs = 15 * 60_000L,
) {
    data class Key(val lat: Double, val lon: Double)

    override val notConfiguredMessage = "Lieu non configuré"
    override fun keyOf(s: Settings) = Key(s.lat, s.lon)
    override suspend fun fetch(key: Key) = Http.get(OpenMeteo.url(key.lat, key.lon))
    override fun parse(raw: String, key: Key) = OpenMeteo.parse(raw)
}

/** City name → coordinates, via Open-Meteo's free geocoding API. */
object Geocoder {
    data class Place(val label: String, val lat: Double, val lon: Double)

    @Serializable
    private data class Resp(val results: List<Item> = emptyList())

    @Serializable
    private data class Item(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val country: String? = null,
        val admin1: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun search(query: String): Place? {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val raw = Http.get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=fr&format=json")
        val it = json.decodeFromString<Resp>(raw).results.firstOrNull() ?: return null
        val label = listOfNotNull(it.name, it.admin1, it.country).distinct().joinToString(", ")
        return Place(label, it.latitude, it.longitude)
    }
}
