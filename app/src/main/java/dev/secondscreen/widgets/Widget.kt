package dev.secondscreen.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import dev.secondscreen.data.Loadable
import dev.secondscreen.ui.LocalNow
import dev.secondscreen.ui.theme.Ink
import dev.secondscreen.ui.theme.Type
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/** How many grid cells (of the 4×2 page grid) a widget occupies. */
@Serializable
enum class WidgetSize(val cols: Int, val rows: Int, val label: String) {
    Small(1, 1, "Petit 1×1"),
    Medium(2, 1, "Moyen 2×1"),
    Large(2, 2, "Grand 2×2"),
    Huge(4, 2, "Plein écran 4×2"),
}

@Serializable
sealed interface WidgetSpec {
    @Serializable @SerialName("clock") data object Clock : WidgetSpec
    @Serializable @SerialName("next_event") data object NextEvent : WidgetSpec
    @Serializable @SerialName("next_events") data class NextEvents(val count: Int = 3) : WidgetSpec
    @Serializable @SerialName("agenda") data object Agenda : WidgetSpec
    @Serializable @SerialName("current_weather") data object CurrentWeather : WidgetSpec
    @Serializable @SerialName("hourly_forecast") data object HourlyForecast : WidgetSpec
    @Serializable @SerialName("daily_forecast") data object DailyForecast : WidgetSpec
    @Serializable @SerialName("news") data class News(val title: String = "Le Monde") : WidgetSpec
    @Serializable @SerialName("now_playing") data object NowPlaying : WidgetSpec
}

/** Human-readable name, shown in the layout editor. */
val WidgetSpec.displayName: String
    get() = when (this) {
        WidgetSpec.Clock -> "Horloge"
        WidgetSpec.NextEvent -> "Prochain évènement"
        is WidgetSpec.NextEvents -> "Prochains évènements"
        WidgetSpec.Agenda -> "Agenda"
        WidgetSpec.CurrentWeather -> "Météo actuelle"
        WidgetSpec.HourlyForecast -> "Prévisions horaires"
        WidgetSpec.DailyForecast -> "Prévisions jours"
        is WidgetSpec.News -> "Actualités"
        WidgetSpec.NowPlaying -> "Musique"
    }

/** Every widget type, with default parameters, in the order offered to the user. */
val AllWidgetSpecs: List<WidgetSpec> = listOf(
    WidgetSpec.Clock,
    WidgetSpec.NextEvent,
    WidgetSpec.NextEvents(),
    WidgetSpec.Agenda,
    WidgetSpec.CurrentWeather,
    WidgetSpec.HourlyForecast,
    WidgetSpec.DailyForecast,
    WidgetSpec.News(),
    WidgetSpec.NowPlaying,
)

/** A widget at an explicit position: top-left cell ([col], [row]) on the page grid. */
@Serializable
data class PlacedWidget(
    val spec: WidgetSpec,
    val size: WidgetSize,
    val col: Int,
    val row: Int,
    val id: String = UUID.randomUUID().toString(),
)

@Serializable
data class PageSpec(
    val name: String,
    val widgets: List<PlacedWidget>,
    val id: String = UUID.randomUUID().toString(),
)

/** A widget not yet positioned; used to declare the default layout. */
data class WidgetSlot(val spec: WidgetSpec, val size: WidgetSize)

infix fun WidgetSpec.sized(size: WidgetSize) = WidgetSlot(this, size)

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun WidgetHost(w: PlacedWidget) {
    val framed = w.spec !is WidgetSpec.Clock
    val frame = if (framed) {
        Modifier.clip(CardShape).background(Ink.Card).border(1.dp, Ink.Line, CardShape)
    } else Modifier
    Box(
        Modifier
            .fillMaxSize()
            .then(frame)
            .padding(if (w.size == WidgetSize.Small) 12.dp else 16.dp),
    ) {
        when (val s = w.spec) {
            WidgetSpec.Clock -> ClockWidget(w.size)
            WidgetSpec.NextEvent -> NextEventWidget(w.size)
            is WidgetSpec.NextEvents -> NextEventsWidget(s.count, w.size)
            WidgetSpec.Agenda -> AgendaWidget(w.size)
            WidgetSpec.CurrentWeather -> CurrentWeatherWidget(w.size)
            WidgetSpec.HourlyForecast -> HourlyForecastWidget(w.size)
            WidgetSpec.DailyForecast -> DailyForecastWidget(w.size)
            is WidgetSpec.News -> NewsWidget(s.title, w.size)
            WidgetSpec.NowPlaying -> NowPlayingWidget(w.size)
        }
    }
}

// ---- shared bits ---------------------------------------------------------------------------

@Composable
fun WidgetLabel(text: String, modifier: Modifier = Modifier, color: Color = Ink.Faint) {
    Text(text.uppercase(), style = Type.label, color = color, modifier = modifier, maxLines = 1)
}

/**
 * Shows [content] once a source has data; otherwise a quiet placeholder. When the data shown is
 * stale or the last refresh failed, a small amber dot appears in the top-right corner.
 */
@Composable
fun <T> WithData(state: Loadable<T>, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    Box(modifier.fillMaxSize()) {
        val data = state.data
        if (data != null) {
            content(data)
        } else {
            Text(
                state.error ?: "Chargement…",
                style = Type.bodySmall,
                color = Ink.Faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        val nowMs = LocalNow.current.toInstant().toEpochMilli()
        if (data != null && (state.error != null || state.isStale(nowMs))) {
            Box(Modifier.align(Alignment.TopEnd).size(5.dp).background(Ink.Warn, CircleShape))
        }
    }
}

/** Font size so that [chars] characters fit in [maxW] and one line fits in [maxH]. */
@Composable
fun fitFontSize(maxW: Dp, maxH: Dp, chars: Int, charWidthEm: Float = 0.6f): TextUnit {
    val byH = maxH.value / 1.18f
    val byW = maxW.value / (chars.coerceAtLeast(1) * charWidthEm)
    return with(LocalDensity.current) { minOf(byH, byW).coerceAtLeast(8f).dp.toSp() }
}

fun TextStyle.sized(size: TextUnit) = copy(fontSize = size, lineHeight = size * 1.08f)
