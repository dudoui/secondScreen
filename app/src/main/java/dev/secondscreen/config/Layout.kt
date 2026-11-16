package dev.secondscreen.config

import dev.secondscreen.widgets.PageSpec
import dev.secondscreen.widgets.WidgetSlot
import dev.secondscreen.widgets.WidgetSize.Huge
import dev.secondscreen.widgets.WidgetSize.Large
import dev.secondscreen.widgets.WidgetSize.Medium
import dev.secondscreen.widgets.WidgetSize.Small
import dev.secondscreen.widgets.WidgetSpec.Agenda
import dev.secondscreen.widgets.WidgetSpec.Clock
import dev.secondscreen.widgets.WidgetSpec.CurrentWeather
import dev.secondscreen.widgets.WidgetSpec.DailyForecast
import dev.secondscreen.widgets.WidgetSpec.HourlyForecast
import dev.secondscreen.widgets.WidgetSpec.News
import dev.secondscreen.widgets.WidgetSpec.NextEvent
import dev.secondscreen.widgets.WidgetSpec.NextEvents
import dev.secondscreen.widgets.WidgetSpec.NowPlaying
import dev.secondscreen.widgets.sized

/**
 * The layout used until the user edits it (and restored by "Réinitialiser"). Pages are in swipe
 * order; each is a 4×2 grid and widgets are packed first-fit in the order listed.
 * Sizes: Small 1×1, Medium 2×1, Large 2×2, Huge 4×2 (whole page).
 */
object DefaultLayout {
    val declared: List<Pair<String, List<WidgetSlot>>> = listOf(
        Pair(
            "Accueil",
            listOf(
                Clock sized Medium,
                CurrentWeather sized Small,
                NextEvent sized Small,
                NextEvents(count = 3) sized Medium,
                News() sized Medium,
            ),
        ),
        Pair(
            "Musique",
            listOf(
                NowPlaying sized Large,
                Clock sized Medium,
                NextEvents(count = 3) sized Medium,
            ),
        ),
        Pair(
            "Agenda",
            listOf(
                Agenda sized Large,
                CurrentWeather sized Medium,
                DailyForecast sized Medium,
            ),
        ),
        Pair(
            "Météo",
            listOf(
                HourlyForecast sized Large,
                DailyForecast sized Large,
            ),
        ),
        Pair("Actualités", listOf(News("Le Monde · À la une") sized Huge)),
        Pair("Horloge", listOf(Clock sized Huge)),
    )

    /** Fresh copy with positions (and new ids) each call. */
    fun pages(): List<PageSpec> = declared.map { (name, slots) -> PageSpec(name, pack(slots)) }
}
