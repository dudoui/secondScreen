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
import dev.secondscreen.i18n.S

/**
 * The layout used until the user edits it (and restored by "Reset"). Pages are in swipe
 * order; each is a 4×2 grid and widgets are packed first-fit in the order listed.
 * Sizes: Small 1×1, Medium 2×1, Large 2×2, Huge 4×2 (whole page).
 */
/** Page names follow the language in use when the layout is created. */
object DefaultLayout {
    val declared: List<Pair<String, List<WidgetSlot>>> get() = listOf(
        Pair(
            S.pageHome,
            listOf(
                Clock sized Medium,
                CurrentWeather sized Small,
                NextEvent sized Small,
                NextEvents(count = 3) sized Medium,
                News() sized Medium,
            ),
        ),
        Pair(
            S.music,
            listOf(
                NowPlaying sized Large,
                Clock sized Medium,
                NextEvents(count = 3) sized Medium,
            ),
        ),
        Pair(
            S.agenda,
            listOf(
                Agenda sized Large,
                CurrentWeather sized Medium,
                DailyForecast sized Medium,
            ),
        ),
        Pair(
            S.pageWeather,
            listOf(
                HourlyForecast sized Large,
                DailyForecast sized Large,
            ),
        ),
        Pair(S.news, listOf(News(S.defaultFeedTitle) sized Huge)),
        Pair(S.clock, listOf(Clock sized Huge)),
    )

    /** Fresh copy with positions (and new ids) each call. */
    fun pages(): List<PageSpec> = declared.map { (name, slots) -> PageSpec(name, pack(slots)) }
}
