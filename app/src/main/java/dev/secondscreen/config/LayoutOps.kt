package dev.secondscreen.config

import dev.secondscreen.widgets.PageSpec
import dev.secondscreen.widgets.PlacedWidget
import dev.secondscreen.widgets.WidgetSize
import dev.secondscreen.widgets.WidgetSlot
import dev.secondscreen.widgets.WidgetSpec
import kotlin.math.abs

/** Pure operations on pages and the 4×2 grid. No Compose, so they're unit-tested directly. */

const val GRID_COLS = 4
const val GRID_ROWS = 2

/**
 * First-fit packing of [slots] onto a [cols]×[rows] grid, in declaration order.
 * Slots that don't fit are dropped. Used to position the default layout.
 */
fun pack(slots: List<WidgetSlot>, cols: Int = GRID_COLS, rows: Int = GRID_ROWS): List<PlacedWidget> {
    val used = Array(rows) { BooleanArray(cols) }
    val out = mutableListOf<PlacedWidget>()
    for (s in slots) {
        val wc = s.size.cols.coerceAtMost(cols)
        val wr = s.size.rows.coerceAtMost(rows)
        search@ for (r in 0..rows - wr) for (c in 0..cols - wc) {
            val free = (r until r + wr).all { rr -> (c until c + wc).none { cc -> used[rr][cc] } }
            if (free) {
                for (rr in r until r + wr) for (cc in c until c + wc) used[rr][cc] = true
                out += PlacedWidget(s.spec, s.size, c, r)
                break@search
            }
        }
    }
    return out
}

/** Grid cells (col, row) covered by a widget of [size] whose top-left is ([col], [row]). */
fun cellsOf(size: WidgetSize, col: Int, row: Int): List<Pair<Int, Int>> =
    (row until row + size.rows).flatMap { r -> (col until col + size.cols).map { c -> c to r } }

fun PlacedWidget.cells() = cellsOf(size, col, row)

/** The widget covering cell ([col], [row]), if any. */
fun PageSpec.widgetAt(col: Int, row: Int): PlacedWidget? = widgets.firstOrNull { (col to row) in it.cells() }

/** True if [size] at ([col], [row]) is inside the grid and overlaps no widget except [ignoreId]. */
fun PageSpec.fits(size: WidgetSize, col: Int, row: Int, ignoreId: String? = null): Boolean {
    if (col < 0 || row < 0 || col + size.cols > GRID_COLS || row + size.rows > GRID_ROWS) return false
    val taken = widgets.filter { it.id != ignoreId }.flatMap { it.cells() }.toSet()
    return cellsOf(size, col, row).none { it in taken }
}

/** Sizes that fit with their top-left at ([col], [row]). */
fun PageSpec.sizesFitting(col: Int, row: Int, ignoreId: String? = null): List<WidgetSize> =
    WidgetSize.entries.filter { fits(it, col, row, ignoreId) }

/**
 * For a corner-drag that would make widget [id] span [cols]×[rows] cells: the closest size it can
 * actually take in place. Ties go to the larger size, since stretching means wanting more room.
 */
fun PageSpec.nearestSize(id: String, cols: Int, rows: Int): WidgetSize? {
    val w = widgets.firstOrNull { it.id == id } ?: return null
    return WidgetSize.entries
        .filter { fits(it, w.col, w.row, ignoreId = id) }
        .minWithOrNull(
            compareBy<WidgetSize>({ abs(it.cols - cols) + abs(it.rows - rows) }, { -(it.cols * it.rows) }),
        )
}

/** Top-left cells where widget [id] (keeping its size) could be moved. */
fun PageSpec.moveTargets(id: String): Set<Pair<Int, Int>> {
    val w = widgets.firstOrNull { it.id == id } ?: return emptySet()
    return (0 until GRID_ROWS).flatMap { r -> (0 until GRID_COLS).map { c -> c to r } }
        .filter { (c, r) -> fits(w.size, c, r, ignoreId = id) }
        .toSet()
}

/** Each returns the updated page, or null when the result wouldn't fit. */
fun PageSpec.place(spec: WidgetSpec, size: WidgetSize, col: Int, row: Int): PageSpec? =
    if (fits(size, col, row)) copy(widgets = widgets + PlacedWidget(spec, size, col, row)) else null

fun PageSpec.move(id: String, col: Int, row: Int): PageSpec? {
    val w = widgets.firstOrNull { it.id == id } ?: return null
    if (!fits(w.size, col, row, ignoreId = id)) return null
    return replace(w.copy(col = col, row = row))
}

fun PageSpec.resize(id: String, size: WidgetSize): PageSpec? {
    val w = widgets.firstOrNull { it.id == id } ?: return null
    if (!fits(size, w.col, w.row, ignoreId = id)) return null
    return replace(w.copy(size = size))
}

fun PageSpec.updateSpec(id: String, spec: WidgetSpec): PageSpec =
    widgets.firstOrNull { it.id == id }?.let { replace(it.copy(spec = spec)) } ?: this

fun PageSpec.remove(id: String): PageSpec = copy(widgets = widgets.filterNot { it.id == id })

private fun PageSpec.replace(w: PlacedWidget) = copy(widgets = widgets.map { if (it.id == w.id) w else it })

// ---- page list ----------------------------------------------------------------------------

fun List<PageSpec>.addPage(name: String): List<PageSpec> = this + PageSpec(name, emptyList())

fun List<PageSpec>.renamePage(index: Int, name: String): List<PageSpec> =
    mapIndexed { i, p -> if (i == index) p.copy(name = name) else p }

fun List<PageSpec>.deletePage(index: Int): List<PageSpec> = filterIndexed { i, _ -> i != index }

/** Swaps page [index] with its neighbour at [index] + [delta]; no-op at the ends. */
fun List<PageSpec>.movePage(index: Int, delta: Int): List<PageSpec> {
    val j = index + delta
    if (index !in indices || j !in indices) return this
    return toMutableList().also { val t = it[index]; it[index] = it[j]; it[j] = t }
}

fun List<PageSpec>.updatePage(index: Int, f: (PageSpec) -> PageSpec): List<PageSpec> =
    mapIndexed { i, p -> if (i == index) f(p) else p }

/**
 * Drops widgets that are out of bounds or overlap an earlier one, so a corrupted or hand-edited
 * layout can never render garbage.
 */
fun sanitize(pages: List<PageSpec>): List<PageSpec> = pages.map { page ->
    page.widgets.fold(page.copy(widgets = emptyList())) { acc, w ->
        if (acc.fits(w.size, w.col, w.row)) acc.copy(widgets = acc.widgets + w) else acc
    }
}
