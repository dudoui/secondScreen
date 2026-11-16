package dev.secondscreen

import dev.secondscreen.config.DefaultLayout
import dev.secondscreen.data.weather.WeatherCodes
import dev.secondscreen.i18n.En
import dev.secondscreen.i18n.Fr
import dev.secondscreen.i18n.S
import dev.secondscreen.i18n.capitalize
import dev.secondscreen.i18n.formatter
import dev.secondscreen.i18n.stringsFor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class StringsTest {
    private val original = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(original)

    @Test
    fun frenchForFrenchEnglishOtherwise() {
        assertSame(Fr, stringsFor(Locale.FRANCE))
        assertSame(Fr, stringsFor(Locale.CANADA_FRENCH))
        assertSame(En, stringsFor(Locale.UK))
        assertSame(En, stringsFor(Locale.GERMANY))
    }

    @Test
    fun followsTheDefaultLocale() {
        Locale.setDefault(Locale.FRANCE)
        assertEquals("Pluie", WeatherCodes.label(61))
        assertEquals("Accueil", DefaultLayout.pages().first().name)

        Locale.setDefault(Locale.US)
        assertEquals("Rain", WeatherCodes.label(61))
        assertEquals("Home", DefaultLayout.pages().first().name)
    }

    @Test
    fun datesUseTheLanguageOfTheText() {
        val day = LocalDate.of(2026, 11, 16)

        Locale.setDefault(Locale.FRANCE)
        assertEquals("Lundi 16 novembre", S.capitalize(day.format(S.formatter(S.longDatePattern))))

        Locale.setDefault(Locale.US)
        assertEquals("Monday, November 16", day.format(S.formatter(S.longDatePattern)))

        Locale.setDefault(Locale.UK)
        assertEquals("Monday 16 November", day.format(S.formatter(S.longDatePattern)))

        // English text never gets German day names.
        Locale.setDefault(Locale.GERMANY)
        assertEquals("Monday 16 November", day.format(S.formatter(S.longDatePattern)))
    }
}
