package dev.secondscreen.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.secondscreen.config.DefaultLayout
import dev.secondscreen.config.GRID_COLS
import dev.secondscreen.config.GRID_ROWS
import dev.secondscreen.config.addPage
import dev.secondscreen.config.deletePage
import dev.secondscreen.config.move
import dev.secondscreen.config.movePage
import dev.secondscreen.config.moveTargets
import dev.secondscreen.config.place
import dev.secondscreen.config.remove
import dev.secondscreen.config.renamePage
import dev.secondscreen.config.resize
import dev.secondscreen.config.nearestSize
import dev.secondscreen.config.updatePage
import dev.secondscreen.config.updateSpec
import dev.secondscreen.config.widgetAt
import androidx.compose.ui.text.input.KeyboardType
import dev.secondscreen.data.rss.feedHost
import dev.secondscreen.data.rss.normalizeFeedUrl
import dev.secondscreen.ui.GridGeometry
import dev.secondscreen.ui.LocalSettings
import dev.secondscreen.ui.theme.Ink
import dev.secondscreen.ui.theme.Type
import dev.secondscreen.widgets.AllWidgetSpecs
import dev.secondscreen.widgets.PageSpec
import dev.secondscreen.widgets.PlacedWidget
import dev.secondscreen.widgets.WidgetHost
import dev.secondscreen.widgets.WidgetSize
import dev.secondscreen.widgets.WidgetSpec
import dev.secondscreen.widgets.displayName
import dev.secondscreen.widgets.label
import dev.secondscreen.i18n.S

private val Shape = RoundedCornerShape(12.dp)

/**
 * Edits the list of pages: page strip on top, a live preview of the selected page's 4×2 grid,
 * and an action panel for the selected widget. Every change goes through [onChange]; nothing is
 * persisted until the settings screen saves.
 */
@Composable
fun LayoutEditor(pages: List<PageSpec>, onChange: (List<PageSpec>) -> Unit, modifier: Modifier = Modifier) {
    var pageIndex by rememberSaveable { mutableStateOf(0) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var moving by rememberSaveable { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }

    val index = pageIndex.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val page = pages.getOrNull(index)
    val selected = page?.widgets?.firstOrNull { it.id == selectedId }
    val knownFeeds = knownFeeds(pages, LocalSettings.current.rssUrl)

    fun selectPage(i: Int) {
        pageIndex = i
        selectedId = null
        moving = false
    }

    fun editPage(f: (PageSpec) -> PageSpec?) {
        val p = page ?: return
        f(p)?.let { updated -> onChange(pages.updatePage(index) { updated }) }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ---- page strip ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.forEachIndexed { i, p ->
                    Chip(p.name.ifBlank { S.untitledPage }, selected = i == index) { selectPage(i) }
                }
                Chip(S.addPage, selected = false) {
                    onChange(pages.addPage(S.pageNumber(pages.size + 1)))
                    selectPage(pages.size)
                }
            }
            if (page != null) {
                SmallAction(S.rename) { dialog = EditorDialog.Rename }
                SmallAction("◀", enabled = index > 0) {
                    onChange(pages.movePage(index, -1)); pageIndex = index - 1
                }
                SmallAction("▶", enabled = index < pages.lastIndex) {
                    onChange(pages.movePage(index, 1)); pageIndex = index + 1
                }
                SmallAction(S.delete) { dialog = EditorDialog.DeletePage }
            }
            SmallAction(S.reset) { dialog = EditorDialog.Reset }
        }

        if (page == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(S.noPagesEditor, style = Type.bodySmall, color = Ink.Faint)
            }
            return@Column
        }

        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            // ---- grid preview ----
            GridPreview(
                page = page,
                selectedId = selected?.id,
                moveTargets = if (moving && selected != null) page.moveTargets(selected.id) else emptySet(),
                onEmptyCell = { c, r ->
                    if (moving) moving = false
                    else { selectedId = null; dialog = EditorDialog.Add(c, r) }
                },
                onWidget = { w ->
                    moving = false
                    selectedId = if (selectedId == w.id) null else w.id
                },
                onMoveTarget = { c, r ->
                    selected?.let { w -> editPage { it.move(w.id, c, r) } }
                    moving = false
                },
                onResize = { id, size -> editPage { it.resize(id, size) } },
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )

            // ---- side panel ----
            Column(
                Modifier.width(230.dp).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (selected == null) {
                    Text(
                        S.editorHint,
                        style = Type.bodySmall,
                        color = Ink.Faint,
                    )
                } else {
                    Text(selected.spec.displayName, style = Type.body)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${selected.size.label}\n${S.resizeHint}",
                        style = Type.bodySmall,
                        color = Ink.Faint,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip(S.move, selected = moving) { moving = !moving }
                        Chip(S.delete, selected = false) {
                            editPage { it.remove(selected.id) }
                            selectedId = null
                            moving = false
                        }
                    }
                    if (selected.spec.hasParams) {
                        Chip(S.settings, selected = false) { dialog = EditorDialog.Params(selected.id) }
                    }
                    if (moving) {
                        Text(S.tapHighlightedCell, style = Type.bodySmall, color = Ink.Faint)
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is EditorDialog.Add -> page?.let { p ->
            AddWidgetDialog(
                onAdd = { spec ->
                    if (spec is WidgetSpec.News) {
                        // A news widget needs its feed first.
                        dialog = EditorDialog.AddNews(d.col, d.row)
                    } else {
                        p.place(spec, WidgetSize.Small, d.col, d.row)?.let { updated ->
                            onChange(pages.updatePage(index) { updated })
                            selectedId = updated.widgets.last().id
                        }
                        dialog = null
                    }
                },
                onDismiss = { dialog = null },
            )
        }
        is EditorDialog.AddNews -> page?.let { p ->
            NewsDialog(
                heading = S.addRssFeed,
                initial = WidgetSpec.News(title = ""),
                known = knownFeeds,
                onDone = { spec ->
                    p.place(spec, WidgetSize.Small, d.col, d.row)?.let { updated ->
                        onChange(pages.updatePage(index) { updated })
                        selectedId = updated.widgets.last().id
                    }
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        is EditorDialog.Params -> page?.widgets?.firstOrNull { it.id == d.widgetId }?.let { w ->
            val done = { spec: WidgetSpec -> editPage { it.updateSpec(w.id, spec) }; dialog = null }
            when (val s = w.spec) {
                is WidgetSpec.News -> NewsDialog(
                    heading = s.displayName,
                    initial = s,
                    known = knownFeeds,
                    onDone = done,
                    onDismiss = { dialog = null },
                )
                else -> ParamsDialog(spec = s, onDone = done, onDismiss = { dialog = null })
            }
        }
        EditorDialog.Rename -> page?.let { p ->
            TextDialog(
                title = S.renamePage,
                initial = p.name,
                onDone = { onChange(pages.renamePage(index, it.trim())); dialog = null },
                onDismiss = { dialog = null },
            )
        }
        EditorDialog.DeletePage -> page?.let { p ->
            ConfirmDialog(
                text = S.deletePageConfirm(p.name),
                confirm = S.delete,
                onConfirm = {
                    onChange(pages.deletePage(index))
                    selectPage((index - 1).coerceAtLeast(0))
                    dialog = null
                },
                onDismiss = { dialog = null },
            )
        }
        EditorDialog.Reset -> ConfirmDialog(
            text = S.resetConfirm,
            confirm = S.reset,
            onConfirm = {
                onChange(DefaultLayout.pages())
                selectPage(0)
                dialog = null
            },
            onDismiss = { dialog = null },
        )
    }
}

private sealed interface EditorDialog {
    data class Add(val col: Int, val row: Int) : EditorDialog
    data class AddNews(val col: Int, val row: Int) : EditorDialog
    data class Params(val widgetId: String) : EditorDialog
    data object Rename : EditorDialog
    data object DeletePage : EditorDialog
    data object Reset : EditorDialog
}

private val WidgetSpec.hasParams get() = this is WidgetSpec.NextEvents || this is WidgetSpec.News

// ---- grid ----------------------------------------------------------------------------------

@Composable
private fun GridPreview(
    page: PageSpec,
    selectedId: String?,
    moveTargets: Set<Pair<Int, Int>>,
    onEmptyCell: (Int, Int) -> Unit,
    onWidget: (PlacedWidget) -> Unit,
    onMoveTarget: (Int, Int) -> Unit,
    onResize: (String, WidgetSize) -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier.clip(Shape).background(Ink.Bg).border(1.dp, Ink.Line, Shape).padding(10.dp)) {
        val g = GridGeometry.of(maxWidth, maxHeight)

        // Empty cells.
        for (r in 0 until GRID_ROWS) for (c in 0 until GRID_COLS) {
            if (page.widgetAt(c, r) == null) {
                Box(
                    Modifier
                        .offset(x = g.x(c), y = g.y(r))
                        .size(g.cellW, g.cellH)
                        .dashedBorder()
                        .clickable { onEmptyCell(c, r) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text("+", color = Ink.Faint, fontSize = 22.sp)
                }
            }
        }

        // Widgets, rendered live, with a tap-catching overlay on top.
        for (w in page.widgets) {
            key(w.id) {
                Box(
                    Modifier
                        .offset(x = g.x(w.col), y = g.y(w.row))
                        .size(g.width(w.size.cols), g.height(w.size.rows)),
                ) {
                    WidgetHost(w)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (w.id == selectedId) Modifier.border(2.dp, Ink.Fg, RoundedCornerShape(16.dp))
                                else Modifier,
                            )
                            .clickable { onWidget(w) },
                    )
                }
            }
        }

        // Where the selected widget can go: its footprint at each valid top-left cell is shown as
        // a highlighted top-left cell.
        for ((c, r) in moveTargets) {
            Box(
                Modifier
                    .offset(x = g.x(c), y = g.y(r))
                    .size(g.cellW, g.cellH)
                    .clip(Shape)
                    .background(Ink.Warn.copy(alpha = 0.18f))
                    .border(2.dp, Ink.Warn, Shape)
                    .clickable { onMoveTarget(c, r) },
            )
        }

        // Resize: drag the selected widget's bottom-right corner. The outline shows the size it
        // will snap to; the change is applied when the finger lifts.
        val sel = page.widgets.firstOrNull { it.id == selectedId }
        if (sel != null && moveTargets.isEmpty()) {
            key(sel.id) {
                var preview by remember { mutableStateOf<WidgetSize?>(null) }
                // The handle stays put while dragging so drag deltas stay consistent.
                val shown = sel.size
                val handle = 44.dp
                Box(
                    Modifier
                        .offset(
                            x = g.x(sel.col) + g.width(shown.cols) - handle / 2 - 6.dp,
                            y = g.y(sel.row) + g.height(shown.rows) - handle / 2 - 6.dp,
                        )
                        .size(handle)
                        .pointerInput(sel.id, sel.size, page) {
                            var w = 0f
                            var h = 0f
                            val stepX = (g.cellW + g.gap).toPx()
                            val stepY = (g.cellH + g.gap).toPx()
                            val gapPx = g.gap.toPx()
                            detectDragGestures(
                                onDragStart = {
                                    w = g.width(sel.size.cols).toPx()
                                    h = g.height(sel.size.rows).toPx()
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    w += amount.x
                                    h += amount.y
                                    val cols = ((w + gapPx) / stepX).roundToInt().coerceIn(1, GRID_COLS)
                                    val rows = ((h + gapPx) / stepY).roundToInt().coerceIn(1, GRID_ROWS)
                                    preview = page.nearestSize(sel.id, cols, rows)
                                },
                                onDragEnd = {
                                    preview?.let { if (it != sel.size) onResize(sel.id, it) }
                                    preview = null
                                },
                                onDragCancel = { preview = null },
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(26.dp).background(Ink.Fg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("↘", color = Ink.Bg, fontSize = 15.sp)
                    }
                }
                // Drawn after the handle so showing it doesn't disturb the handle mid-drag.
                preview?.let { size ->
                    Box(
                        Modifier
                            .offset(x = g.x(sel.col), y = g.y(sel.row))
                            .size(g.width(size.cols), g.height(size.rows))
                            .clip(RoundedCornerShape(16.dp))
                            .background(Ink.Fg.copy(alpha = 0.08f))
                            .border(2.dp, Ink.Fg, RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${size.cols}×${size.rows}", color = Ink.Fg, fontSize = 20.sp)
                    }
                }
            }
        }
    }
}

private fun Modifier.dashedBorder() = drawBehind {
    drawRoundRect(
        color = Ink.Ghost,
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))),
        cornerRadius = CornerRadius(16.dp.toPx()),
    )
}

// ---- small controls --------------------------------------------------------------------------

@Composable
private fun Chip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val color = when {
        !enabled -> Ink.Ghost
        selected -> Ink.Bg
        else -> Ink.Fg
    }
    Box(
        Modifier
            .clip(Shape)
            .background(if (selected && enabled) Ink.Fg else Ink.Card)
            .border(1.dp, if (enabled) Ink.Line else Ink.Card, Shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(label, color = color, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun SmallAction(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled) {
        Text(label, color = if (enabled) Ink.Dim else Ink.Ghost, fontSize = 14.sp)
    }
}

// ---- dialogs ---------------------------------------------------------------------------------

@Composable
private fun AddWidgetDialog(onAdd: (WidgetSpec) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Card,
        title = { Text(S.addWidget, color = Ink.Fg) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AllWidgetSpecs.forEach { spec -> Chip(spec.displayName, selected = false) { onAdd(spec) } }
                Text(S.addedSmallHint, style = Type.bodySmall, color = Ink.Faint)
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel, color = Ink.Dim) } },
    )
}

@Composable
private fun ParamsDialog(spec: WidgetSpec, onDone: (WidgetSpec) -> Unit, onDismiss: () -> Unit) {
    var draft by remember { mutableStateOf(spec) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Card,
        title = { Text(spec.displayName, color = Ink.Fg) },
        text = {
            when (val d = draft) {
                is WidgetSpec.NextEvents -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(S.eventCount, color = Ink.Dim, modifier = Modifier.weight(1f))
                    SmallAction("−", enabled = d.count > 1) { draft = d.copy(count = d.count - 1) }
                    Text("${d.count}", color = Ink.Fg, fontSize = 18.sp)
                    SmallAction("+", enabled = d.count < 5) { draft = d.copy(count = d.count + 1) }
                }
                else -> Unit
            }
        },
        confirmButton = { TextButton(onClick = { onDone(draft) }) { Text(S.ok, color = Ink.Fg) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel, color = Ink.Dim) } },
    )
}

/** A feed the user can pick again: one already on a page, or the default from the settings. */
private data class KnownFeed(val title: String, val url: String)

private fun knownFeeds(pages: List<PageSpec>, defaultUrl: String): List<KnownFeed> {
    val default = defaultUrl.trim()
    val used = pages.flatMap { it.widgets }.mapNotNull { it.spec as? WidgetSpec.News }
        .map { KnownFeed(it.title, it.url.trim().ifEmpty { default }) }
    return (used + KnownFeed("", default))
        .filter { it.url.isNotEmpty() }
        .distinctBy { it.url }
        .map { if (it.title.isBlank()) it.copy(title = feedHost(it.url) ?: S.news) else it }
}

/** Picks a news widget's feed: type any address, or tap one already in use. */
@Composable
private fun NewsDialog(
    heading: String,
    initial: WidgetSpec.News,
    known: List<KnownFeed>,
    onDone: (WidgetSpec.News) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(initial.url) }
    var title by remember { mutableStateOf(initial.title) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Card,
        title = { Text(heading, color = Ink.Fg) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(S.feedUrlLabel, url, { url = it }, keyboardType = KeyboardType.Uri)
                Field(S.feedTitleLabel, title, { title = it })
                if (known.isNotEmpty()) {
                    Text(S.feedsInUse, style = Type.bodySmall, color = Ink.Faint)
                    known.forEach { feed ->
                        val host = feedHost(feed.url)
                        val label = if (host == null || host == feed.title) feed.title else "${feed.title} · $host"
                        Chip(label, selected = normalizeFeedUrl(url) == feed.url) {
                            url = feed.url
                            title = feed.title
                        }
                    }
                }
                Text(
                    S.feedFallbackHint,
                    style = Type.bodySmall,
                    color = Ink.Faint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val u = normalizeFeedUrl(url)
                val t = title.trim().ifEmpty { feedHost(u) ?: S.news }
                onDone(WidgetSpec.News(title = t, url = u))
            }) { Text(S.ok, color = Ink.Fg) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel, color = Ink.Dim) } },
    )
}

@Composable
private fun TextDialog(title: String, initial: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Card,
        title = { Text(title, color = Ink.Fg) },
        text = { Field(S.nameLabel, text, { text = it }) },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text(S.ok, color = Ink.Fg) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel, color = Ink.Dim) } },
    )
}

@Composable
private fun ConfirmDialog(text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ink.Card,
        text = { Text(text, color = Ink.Fg) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, color = Ink.Accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(S.cancel, color = Ink.Dim) } },
    )
}
