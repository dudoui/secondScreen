package dev.secondscreen

import dev.secondscreen.config.DefaultLayout
import dev.secondscreen.config.LayoutCodec
import dev.secondscreen.config.addPage
import dev.secondscreen.config.deletePage
import dev.secondscreen.config.fits
import dev.secondscreen.config.move
import dev.secondscreen.config.nearestSize
import dev.secondscreen.config.movePage
import dev.secondscreen.config.moveTargets
import dev.secondscreen.config.place
import dev.secondscreen.config.remove
import dev.secondscreen.config.renamePage
import dev.secondscreen.config.resize
import dev.secondscreen.config.sanitize
import dev.secondscreen.config.sizesFitting
import dev.secondscreen.config.widgetAt
import dev.secondscreen.widgets.AllWidgetSpecs
import dev.secondscreen.widgets.PageSpec
import dev.secondscreen.widgets.PlacedWidget
import dev.secondscreen.widgets.WidgetSize.Huge
import dev.secondscreen.widgets.WidgetSize.Large
import dev.secondscreen.widgets.WidgetSize.Medium
import dev.secondscreen.widgets.WidgetSize.Small
import dev.secondscreen.widgets.WidgetSpec.Clock
import dev.secondscreen.widgets.WidgetSpec.News
import dev.secondscreen.widgets.WidgetSpec.NextEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutOpsTest {
    private val empty = PageSpec("p", emptyList())

    @Test
    fun boundsAndOverlap() {
        val p = empty.place(Clock, Large, 0, 0)!!
        assertFalse(p.fits(Small, 1, 1))        // covered by the Large
        assertTrue(p.fits(Small, 2, 0))
        assertFalse(p.fits(Medium, 3, 0))       // runs off the right edge
        assertFalse(p.fits(Large, 2, 1))        // runs off the bottom
        assertNull(p.place(Clock, Small, 0, 1)) // occupied
        assertEquals(p.widgets[0], p.widgetAt(1, 1))
        assertNull(p.widgetAt(3, 1))
    }

    @Test
    fun gapsAreAllowed() {
        val p = empty.place(Clock, Small, 3, 1)!!
        assertEquals(3 to 1, p.widgets[0].col to p.widgets[0].row)
        assertEquals(listOf(Small, Medium, Large), p.sizesFitting(0, 0))
    }

    @Test
    fun moveAndResize() {
        var p = empty.place(Clock, Medium, 0, 0)!!.place(Clock, Small, 2, 0)!!
        val a = p.widgets[0].id
        assertNull(p.move(a, 1, 0))            // would overlap the Small
        p = p.move(a, 0, 1)!!
        assertEquals(0 to 1, p.widgets[0].col to p.widgets[0].row)
        assertTrue((0 to 0) in p.moveTargets(a))
        assertNull(p.resize(a, Huge))
        p = p.resize(a, Small)!!
        assertEquals(Small, p.widgets[0].size)
        assertEquals(1, p.remove(a).widgets.size)
    }

    @Test
    fun resizeIgnoresItself() {
        val p = empty.place(Clock, Small, 0, 0)!!
        assertNotNull(p.resize(p.widgets[0].id, Huge))
    }

    @Test
    fun stretchSnapsToNearestFittingSize() {
        val p = empty.place(Clock, Small, 0, 0)!!
        val id = p.widgets[0].id
        assertEquals(Small, p.nearestSize(id, 1, 1))
        assertEquals(Medium, p.nearestSize(id, 2, 1))
        assertEquals(Medium, p.nearestSize(id, 3, 1))  // 3×1 doesn't exist; 2×1 is closest
        assertEquals(Large, p.nearestSize(id, 1, 2))   // tie 1×1 / 2×2 → the larger
        assertEquals(Huge, p.nearestSize(id, 4, 1))
        assertEquals(Huge, p.nearestSize(id, 4, 2))
        // A neighbour at (2,0) blocks 4×2: stretching all the way gives 2×2.
        val blocked = p.place(Clock, Small, 2, 0)!!
        assertEquals(Large, blocked.nearestSize(id, 4, 2))
    }

    @Test
    fun pageOps() {
        var pages = listOf(PageSpec("a", emptyList()), PageSpec("b", emptyList()))
        pages = pages.addPage("c").renamePage(0, "A").movePage(0, 1)
        assertEquals(listOf("b", "A", "c"), pages.map { it.name })
        assertEquals(pages, pages.movePage(0, -1))
        assertEquals(listOf("b", "c"), pages.deletePage(1).map { it.name })
    }

    @Test
    fun jsonKeepsParamsPositionsAndGaps() {
        val pages = listOf(
            empty.place(News("X"), Medium, 0, 0)!!.place(NextEvents(5), Small, 3, 1)!!,
            PageSpec("vide", emptyList()),
        )
        assertEquals(pages, LayoutCodec.decode(LayoutCodec.encode(pages)))
    }

    @Test
    fun everySpecDecodes() {
        AllWidgetSpecs.forEach { spec ->
            val pages = listOf(empty.place(spec, Small, 0, 0)!!)
            assertEquals(pages, LayoutCodec.decode(LayoutCodec.encode(pages)))
        }
    }

    @Test
    fun invalidJsonFallsBackToDefault() {
        assertEquals(DefaultLayout.declared.map { it.first }, LayoutCodec.decode("{oops").map { it.name })
        assertEquals(DefaultLayout.declared.map { it.first }, LayoutCodec.decode(null).map { it.name })
        assertEquals(emptyList<PageSpec>(), LayoutCodec.decode("[]"))
    }

    @Test
    fun sanitizeDropsOverlapsAndOutOfBounds() {
        val bad = PageSpec(
            "x",
            listOf(
                PlacedWidget(Clock, Large, 0, 0),
                PlacedWidget(Clock, Small, 1, 1),  // overlaps
                PlacedWidget(Clock, Medium, 3, 0), // off-grid
                PlacedWidget(Clock, Small, 3, 1),
            ),
        )
        assertEquals(listOf(0 to 0, 3 to 1), sanitize(listOf(bad))[0].widgets.map { it.col to it.row })
    }

    @Test
    fun defaultLayoutKeepsEveryWidget() {
        val pages = DefaultLayout.pages()
        assertEquals(DefaultLayout.declared.map { it.second.size }, pages.map { it.widgets.size })
        assertEquals(pages, sanitize(pages))
    }
}
