package dev.secondscreen.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Dehaze
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.secondscreen.data.weather.Weather
import dev.secondscreen.data.weather.WeatherCodes
import dev.secondscreen.ui.LocalContainer
import dev.secondscreen.ui.LocalNow
import dev.secondscreen.ui.LocalSettings
import dev.secondscreen.ui.theme.Ink
import dev.secondscreen.ui.theme.Type
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ---- logic ---------------------------------------------------------------------------------

fun weatherIcon(code: Int, isDay: Boolean = true): ImageVector = when (code) {
    0, 1 -> if (isDay) Icons.Outlined.WbSunny else Icons.Outlined.NightsStay
    2 -> if (isDay) Icons.Outlined.FilterDrama else Icons.Outlined.NightsStay
    45, 48 -> Icons.Outlined.Dehaze
    in 51..57 -> Icons.Outlined.Grain
    in 61..67, in 80..82 -> Icons.Outlined.Umbrella
    in 71..77, 85, 86 -> Icons.Outlined.AcUnit
    in 95..99 -> Icons.Outlined.FlashOn
    else -> Icons.Outlined.Cloud
}

fun deg(t: Double) = if (t.isNaN()) "–" else "${t.roundToInt()}°"

/** The one thing worth flagging about the weather right now, if any. */
fun weatherAlert(w: Weather, now: LocalDateTime): Pair<String, Color>? {
    if (WeatherCodes.isStorm(w.current.code)) return "Orage en cours" to Ink.Accent
    val soon = w.upcomingHours(now).drop(1).take(1)
    if (!WeatherCodes.isWet(w.current.code) && soon.any { it.precip >= 50 && WeatherCodes.isWet(it.code) }) {
        return "Pluie dans l'heure" to Ink.Accent
    }
    if (w.upcomingHours(now).take(6).any { WeatherCodes.isStorm(it.code) }) return "Orages prévus" to Ink.Accent
    val today = w.today(now)
    if (today != null && today.precip >= 60) return "Pluie probable · ${today.precip}%" to Ink.Warn
    return null
}

private val HOUR = DateTimeFormatter.ofPattern("H'h'")
private val DAY_SHORT = DateTimeFormatter.ofPattern("EEE", Locale.FRENCH)

private fun dayName(d: LocalDate, today: LocalDate) = when (d) {
    today -> "Auj."
    today.plusDays(1) -> "Dem."
    else -> d.format(DAY_SHORT).replaceFirstChar { it.titlecase(Locale.FRENCH) }
}

// ---- current -------------------------------------------------------------------------------

@Composable
fun CurrentWeatherWidget(size: WidgetSize) {
    val state by LocalContainer.current.weather.state.collectAsState()
    val city = LocalSettings.current.city
    Column(Modifier.fillMaxSize()) {
        WidgetLabel(city)
        Spacer(Modifier.height(6.dp))
        WithData(state) { w ->
            val now = LocalNow.current.toLocalDateTime()
            val alert = weatherAlert(w, now)
            val today = w.today(now)
            val c = w.current
            when (size) {
                WidgetSize.Small -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                        val bw = maxWidth
                        val bh = maxHeight
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(weatherIcon(c.code, c.isDay), null, tint = Ink.Dim, modifier = Modifier.size(26.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(deg(c.temp), style = Type.num.sized(fitFontSize(bw - 34.dp, bh, 3, 0.55f)), maxLines = 1)
                        }
                    }
                    Text(
                        alert?.first ?: WeatherCodes.label(c.code),
                        style = Type.bodySmall,
                        color = alert?.second ?: Ink.Dim,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                else -> Column(Modifier.fillMaxSize()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(weatherIcon(c.code, c.isDay), null, tint = Ink.Dim, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(deg(c.temp), style = Type.num.sized(64.sp), maxLines = 1)
                        Spacer(Modifier.width(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(WeatherCodes.label(c.code), style = Type.body, maxLines = 1)
                            Text("ressenti ${deg(c.feels)}", style = Type.bodySmall, maxLines = 1)
                            if (today != null) Text("${deg(today.min)} / ${deg(today.max)}", style = Type.numSmall, maxLines = 1)
                            Text("vent ${c.wind.roundToInt()} km/h", style = Type.bodySmall, color = Ink.Faint, maxLines = 1)
                        }
                    }
                    if (alert != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(alert.first, style = Type.body, color = alert.second)
                    }
                    if (size == WidgetSize.Large || size == WidgetSize.Huge) {
                        Spacer(Modifier.weight(1f))
                        HourStrip(w.upcomingHours(now).take(if (size == WidgetSize.Huge) 12 else 6))
                    }
                }
            }
        }
    }
}

// ---- hourly --------------------------------------------------------------------------------

@Composable
fun HourlyForecastWidget(size: WidgetSize) {
    val state by LocalContainer.current.weather.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        WidgetLabel("Prochaines heures")
        Spacer(Modifier.height(10.dp))
        WithData(state) { w ->
            val hours = w.upcomingHours(LocalNow.current.toLocalDateTime())
            when (size) {
                WidgetSize.Small -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    hours.drop(1).take(3).forEach { h ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.time.format(HOUR), style = Type.numSmall, modifier = Modifier.width(34.dp))
                            Icon(weatherIcon(h.code), null, tint = Ink.Dim, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.weight(1f))
                            Text(deg(h.temp), style = Type.body)
                        }
                    }
                }
                WidgetSize.Medium -> HourStrip(hours.take(6))
                else -> HourlyChart(hours.take(25), Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun HourStrip(hours: List<Weather.Hour>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        hours.forEach { h ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(h.time.format(HOUR), style = Type.numSmall, color = Ink.Faint)
                Icon(weatherIcon(h.code), null, tint = Ink.Dim, modifier = Modifier.size(20.dp))
                Text(deg(h.temp), style = Type.body)
                Text(
                    if (h.precip >= 20) "${h.precip}%" else " ",
                    style = Type.numSmall.copy(fontSize = 11.sp),
                    color = if (h.precip >= 60) Ink.Warn else Ink.Faint,
                )
            }
        }
    }
}

/** Temperature line over grey precipitation bars, labelled every 3 hours. */
@Composable
private fun HourlyChart(hours: List<Weather.Hour>, modifier: Modifier) {
    if (hours.size < 2) return
    val measurer = rememberTextMeasurer()
    val tempStyle = TextStyle(fontSize = 12.sp, color = Ink.Dim)
    val hourStyle = TextStyle(fontSize = 11.sp, color = Ink.Faint)
    val pctStyle = TextStyle(fontSize = 10.sp, color = Ink.Faint)
    Canvas(modifier) {
        val n = hours.size
        val stepX = size.width / n
        val labelH = 18.dp.toPx()
        val top = 22.dp.toPx()
        val bottom = size.height - labelH
        val lo = hours.minOf { it.temp } - 1
        val hi = hours.maxOf { it.temp } + 1
        val lineBottom = bottom - (bottom - top) * 0.35f
        fun x(i: Int) = stepX * i + stepX / 2
        fun y(t: Double) = (lineBottom - (t - lo) / (hi - lo) * (lineBottom - top)).toFloat()

        // precipitation bars (bottom 40% of the chart)
        val barMax = (bottom - top) * 0.4f
        hours.forEachIndexed { i, h ->
            if (h.precip <= 0) return@forEachIndexed
            val bh = barMax * h.precip / 100f
            drawRoundRect(
                color = if (h.precip >= 60) Ink.Warn.copy(alpha = 0.45f) else Ink.Ghost,
                topLeft = Offset(x(i) - stepX * 0.3f, bottom - bh),
                size = Size(stepX * 0.6f, bh),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }

        // temperature line
        val path = Path()
        hours.forEachIndexed { i, h -> if (i == 0) path.moveTo(x(i), y(h.temp)) else path.lineTo(x(i), y(h.temp)) }
        drawPath(path, Ink.Fg, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // labels
        for (i in 0 until n step 3) {
            val h = hours[i]
            val t = measurer.measure(deg(h.temp), tempStyle)
            drawText(t, topLeft = Offset((x(i) - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), y(h.temp) - t.size.height - 4.dp.toPx()))
            drawCircle(Ink.Fg, radius = 2.5.dp.toPx(), center = Offset(x(i), y(h.temp)))
            val lbl = measurer.measure(h.time.format(HOUR), hourStyle)
            drawText(lbl, topLeft = Offset((x(i) - lbl.size.width / 2f).coerceIn(0f, size.width - lbl.size.width), size.height - lbl.size.height))
            if (h.precip >= 20) {
                val p = measurer.measure("${h.precip}%", pctStyle)
                drawText(p, topLeft = Offset((x(i) - p.size.width / 2f).coerceIn(0f, size.width - p.size.width), bottom - barMax * h.precip / 100f - p.size.height - 2.dp.toPx()))
            }
        }
    }
}

// ---- daily ---------------------------------------------------------------------------------

@Composable
fun DailyForecastWidget(size: WidgetSize) {
    val state by LocalContainer.current.weather.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        WidgetLabel(if (size == WidgetSize.Small) "Jours" else "7 jours")
        Spacer(Modifier.height(10.dp))
        WithData(state) { w ->
            val today = LocalNow.current.toLocalDate()
            val days = w.days.filter { !it.date.isBefore(today) }.take(
                when (size) {
                    WidgetSize.Small -> 3
                    WidgetSize.Medium -> 3
                    else -> 7
                },
            )
            if (days.isEmpty()) return@WithData
            val lo = days.minOf { it.min }
            val hi = days.maxOf { it.max }
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = if (size == WidgetSize.Small) Arrangement.spacedBy(6.dp) else Arrangement.SpaceEvenly,
            ) {
                days.forEach { d -> DayRow(d, lo, hi, today, compact = size == WidgetSize.Small, big = size == WidgetSize.Huge) }
            }
        }
    }
}

@Composable
private fun DayRow(d: Weather.Day, lo: Double, hi: Double, today: LocalDate, compact: Boolean, big: Boolean) {
    val textStyle = if (big) Type.body.copy(fontSize = 18.sp) else Type.body
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(dayName(d.date, today), style = textStyle, color = if (d.date == today) Ink.Fg else Ink.Dim, modifier = Modifier.width(if (compact) 40.dp else 52.dp), maxLines = 1)
        Icon(weatherIcon(d.code), null, tint = Ink.Dim, modifier = Modifier.size(if (big) 22.dp else 18.dp))
        if (compact) {
            Spacer(Modifier.weight(1f))
            Text("${deg(d.min)} ${deg(d.max)}", style = Type.numSmall, maxLines = 1)
            return@Row
        }
        Text(
            if (d.precip >= 20) "${d.precip}%" else "",
            style = Type.numSmall,
            color = if (d.precip >= 60) Ink.Warn else Ink.Faint,
            textAlign = TextAlign.End,
            modifier = Modifier.width(48.dp),
        )
        Text(deg(d.min), style = Type.numSmall, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
        Spacer(Modifier.width(10.dp))
        RangeBar(d.min, d.max, lo, hi, Modifier.weight(1f).height(4.dp))
        Spacer(Modifier.width(10.dp))
        Text(deg(d.max), style = textStyle, modifier = Modifier.width(36.dp))
    }
}

@Composable
private fun RangeBar(min: Double, max: Double, lo: Double, hi: Double, modifier: Modifier) {
    Canvas(modifier) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(Ink.Ghost, cornerRadius = r)
        val span = (hi - lo).takeIf { it > 0 } ?: 1.0
        val a = ((min - lo) / span).toFloat() * size.width
        val b = ((max - lo) / span).toFloat() * size.width
        drawRoundRect(Ink.Dim, topLeft = Offset(a, 0f), size = Size((b - a).coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}
