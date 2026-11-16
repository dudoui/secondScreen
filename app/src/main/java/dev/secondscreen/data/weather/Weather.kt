package dev.secondscreen.data.weather

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

data class Weather(val current: Current, val hours: List<Hour>, val days: List<Day>) {
    data class Current(val time: LocalDateTime, val temp: Double, val feels: Double, val code: Int, val wind: Double, val isDay: Boolean)
    data class Hour(val time: LocalDateTime, val temp: Double, val precip: Int, val code: Int)
    data class Day(val date: LocalDate, val code: Int, val min: Double, val max: Double, val precip: Int, val sunrise: LocalDateTime?, val sunset: LocalDateTime?)

    fun upcomingHours(now: LocalDateTime): List<Hour> {
        val h = now.truncatedTo(ChronoUnit.HOURS)
        return hours.filter { !it.time.isBefore(h) }
    }

    fun today(now: LocalDateTime): Day? = days.firstOrNull { it.date == now.toLocalDate() } ?: days.firstOrNull()
}

object WeatherCodes {
    fun isStorm(code: Int) = code in 95..99
    fun isWet(code: Int) = code in 51..67 || code in 71..77 || code in 80..86 || code in 95..99

    fun label(code: Int): String = when (code) {
        0 -> "Ciel clair"
        1 -> "Peu nuageux"
        2 -> "Partiellement nuageux"
        3 -> "Couvert"
        45, 48 -> "Brouillard"
        in 51..57 -> "Bruine"
        in 61..65 -> "Pluie"
        66, 67 -> "Pluie verglaçante"
        in 71..77 -> "Neige"
        in 80..82 -> "Averses"
        85, 86 -> "Averses de neige"
        in 95..99 -> "Orage"
        else -> "—"
    }
}

/** Open-Meteo `/v1/forecast` response. */
object OpenMeteo {
    private val json = Json { ignoreUnknownKeys = true }

    fun url(lat: Double, lon: Double) =
        "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
            "&hourly=temperature_2m,precipitation_probability,weather_code" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,sunrise,sunset" +
            "&timezone=auto&forecast_days=8"

    @Serializable
    private data class Resp(val current: Cur, val hourly: Hourly, val daily: Daily)

    @Serializable
    private data class Cur(
        val time: String,
        @SerialName("temperature_2m") val temp: Double? = null,
        @SerialName("apparent_temperature") val feels: Double? = null,
        @SerialName("weather_code") val code: Int? = null,
        @SerialName("wind_speed_10m") val wind: Double? = null,
        @SerialName("is_day") val isDay: Int? = 1,
    )

    @Serializable
    private data class Hourly(
        val time: List<String>,
        @SerialName("temperature_2m") val temp: List<Double?>,
        @SerialName("precipitation_probability") val precip: List<Int?>,
        @SerialName("weather_code") val code: List<Int?>,
    )

    @Serializable
    private data class Daily(
        val time: List<String>,
        @SerialName("weather_code") val code: List<Int?>,
        @SerialName("temperature_2m_max") val max: List<Double?>,
        @SerialName("temperature_2m_min") val min: List<Double?>,
        @SerialName("precipitation_probability_max") val precip: List<Int?>,
        val sunrise: List<String?> = emptyList(),
        val sunset: List<String?> = emptyList(),
    )

    fun parse(raw: String): Weather {
        val r = json.decodeFromString<Resp>(raw)
        val c = r.current
        return Weather(
            current = Weather.Current(
                time = LocalDateTime.parse(c.time),
                temp = c.temp ?: Double.NaN,
                feels = c.feels ?: c.temp ?: Double.NaN,
                code = c.code ?: -1,
                wind = c.wind ?: 0.0,
                isDay = (c.isDay ?: 1) == 1,
            ),
            hours = r.hourly.time.indices.mapNotNull { i ->
                Weather.Hour(
                    time = LocalDateTime.parse(r.hourly.time[i]),
                    temp = r.hourly.temp.getOrNull(i) ?: return@mapNotNull null,
                    precip = r.hourly.precip.getOrNull(i) ?: 0,
                    code = r.hourly.code.getOrNull(i) ?: -1,
                )
            },
            days = r.daily.time.indices.mapNotNull { i ->
                Weather.Day(
                    date = LocalDate.parse(r.daily.time[i]),
                    code = r.daily.code.getOrNull(i) ?: -1,
                    min = r.daily.min.getOrNull(i) ?: return@mapNotNull null,
                    max = r.daily.max.getOrNull(i) ?: return@mapNotNull null,
                    precip = r.daily.precip.getOrNull(i) ?: 0,
                    sunrise = r.daily.sunrise.getOrNull(i)?.let(LocalDateTime::parse),
                    sunset = r.daily.sunset.getOrNull(i)?.let(LocalDateTime::parse),
                )
            },
        )
    }
}
