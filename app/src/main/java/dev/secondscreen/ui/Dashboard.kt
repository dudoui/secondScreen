package dev.secondscreen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.secondscreen.config.GRID_COLS
import dev.secondscreen.config.GRID_ROWS
import dev.secondscreen.ui.theme.Type
import androidx.compose.material3.Text
import androidx.compose.runtime.key
import androidx.compose.ui.unit.Dp
import dev.secondscreen.ui.theme.Ink
import dev.secondscreen.widgets.PageSpec
import dev.secondscreen.widgets.WidgetHost
import kotlinx.coroutines.delay

@Composable
fun DashboardScreen(pages: List<PageSpec>, onOpenSettings: () -> Unit) {
    val pager = rememberPagerState { pages.size }

    // Burn-in guard: nudge everything by a few pixels every 3 minutes.
    val shift by produceState(IntOffset.Zero) {
        val xs = intArrayOf(0, 3, -2, 2, -3)
        val ys = intArrayOf(0, -2, 3, -3, 2)
        var i = 0
        while (true) {
            delay(180_000)
            i++
            value = IntOffset(xs[i % xs.size], ys[(i / 2) % ys.size])
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink.Bg)
            .pointerInput(Unit) { detectStillHold(holdMs = 900) { onOpenSettings() } },
    ) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize().offset { shift },
            beyondViewportPageCount = 1,
        ) { page ->
            pages.getOrNull(page)?.let { PageGrid(it) }
        }
        if (pages.isEmpty()) {
            Text(
                "Aucune page. Maintenir le doigt appuyé pour configurer.",
                style = Type.bodySmall,
                color = Ink.Faint,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        PageDots(
            count = pages.size,
            current = pager.currentPage,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 6.dp),
        )
    }
}

/**
 * Fires when one finger stays down, without moving, for [holdMs]. Observes events in the Initial
 * pass without consuming them, so swipes and taps on widgets still work, and any movement cancels.
 */
private suspend fun PointerInputScope.detectStillHold(holdMs: Long, onHold: () -> Unit) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
    val slop = viewConfiguration.touchSlop
    val interrupted = withTimeoutOrNull(holdMs) {
        var stop = false
        while (!stop) {
            val ev = awaitPointerEvent(PointerEventPass.Initial)
            val c = ev.changes.firstOrNull { it.id == down.id }
            stop = c == null || !c.pressed || ev.changes.size > 1 ||
                (c.position - down.position).getDistance() > slop
        }
        true
    }
    if (interrupted == null) onHold()
}

@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier) {
    if (count < 2) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(4.dp).background(if (i == current) Ink.Dim else Ink.Ghost, CircleShape))
        }
    }
}

/** Cell size and spacing of the 4×2 page grid inside a given box. */
data class GridGeometry(val cellW: Dp, val cellH: Dp, val gap: Dp) {
    fun x(col: Int) = (cellW + gap) * col
    fun y(row: Int) = (cellH + gap) * row
    fun width(cols: Int) = cellW * cols + gap * (cols - 1)
    fun height(rows: Int) = cellH * rows + gap * (rows - 1)

    companion object {
        fun of(maxWidth: Dp, maxHeight: Dp, gap: Dp = 10.dp) = GridGeometry(
            cellW = (maxWidth - gap * (GRID_COLS - 1)) / GRID_COLS,
            cellH = (maxHeight - gap * (GRID_ROWS - 1)) / GRID_ROWS,
            gap = gap,
        )
    }
}

/** Padding around the grid on the dashboard; the editor preview uses the same. */
val PagePadding = Modifier.padding(horizontal = 14.dp, vertical = 14.dp).padding(bottom = 6.dp)

@Composable
fun PageGrid(page: PageSpec, modifier: Modifier = Modifier.fillMaxSize().then(PagePadding)) {
    BoxWithConstraints(modifier) {
        val g = GridGeometry.of(maxWidth, maxHeight)
        for (w in page.widgets) {
            key(w.id) {
                Box(
                    Modifier
                        .offset(x = g.x(w.col), y = g.y(w.row))
                        .size(width = g.width(w.size.cols), height = g.height(w.size.rows)),
                ) {
                    WidgetHost(w)
                }
            }
        }
    }
}
