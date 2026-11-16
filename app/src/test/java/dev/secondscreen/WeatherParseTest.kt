package dev.secondscreen

import dev.secondscreen.data.weather.OpenMeteo
import dev.secondscreen.data.weather.WeatherCodes
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeatherParseTest {
    @Test
    fun parsesOpenMeteo() {
        val raw = """
            {"latitude":48.86,"longitude":2.35,"timezone":"Europe/Paris",
             "current":{"time":"2026-11-14T17:00","interval":900,"temperature_2m":9.4,"apparent_temperature":6.8,"weather_code":61,"wind_speed_10m":14.2,"is_day":0},
             "hourly":{"time":["2026-11-14T17:00","2026-11-14T18:00"],"temperature_2m":[9.4,null],"precipitation_probability":[80,null],"weather_code":[61,3]},
             "daily":{"time":["2026-11-14","2026-11-15"],"weather_code":[61,3],"temperature_2m_max":[11.0,12.5],"temperature_2m_min":[6.0,5.1],
                      "precipitation_probability_max":[85,10],"sunrise":["2026-11-14T08:01","2026-11-15T08:03"],"sunset":["2026-11-14T17:09","2026-11-15T17:08"]}}
        """.trimIndent()
        val w = OpenMeteo.parse(raw)
        assertEquals(9.4, w.current.temp, 0.0)
        assertEquals(false, w.current.isDay)
        assertEquals(1, w.hours.size) // null temperature dropped
        assertEquals(LocalDate.of(2026, 11, 15), w.days[1].date)
        assertEquals("Pluie", WeatherCodes.label(w.current.code))
    }
}
