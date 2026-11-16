package dev.secondscreen

import dev.secondscreen.config.DefaultLayout
import dev.secondscreen.config.pack
import dev.secondscreen.widgets.WidgetSize.Huge
import dev.secondscreen.widgets.WidgetSize.Large
import dev.secondscreen.widgets.WidgetSize.Medium
import dev.secondscreen.widgets.WidgetSize.Small
import dev.secondscreen.widgets.WidgetSpec.Clock
import dev.secondscreen.widgets.sized
import org.junit.Assert.assertEquals
import org.junit.Test

class PackTest {
    @Test
    fun firstFitPacking() {
        val p = pack(listOf(Clock sized Medium, Clock sized Small, Clock sized Small, Clock sized Medium, Clock sized Medium))
        assertEquals(listOf(0 to 0, 2 to 0, 3 to 0, 0 to 1, 2 to 1), p.map { it.col to it.row })
    }

    @Test
    fun largeThenSmallsFillRightHalf() {
        val p = pack(listOf(Clock sized Large, Clock sized Small, Clock sized Small, Clock sized Medium))
        assertEquals(listOf(0 to 0, 2 to 0, 3 to 0, 2 to 1), p.map { it.col to it.row })
    }

    @Test
    fun overflowIsDropped() {
        assertEquals(1, pack(listOf(Clock sized Huge, Clock sized Small)).size)
    }

    @Test
    fun defaultPagesFitEntirely() {
        DefaultLayout.declared.forEach { (name, slots) ->
            assertEquals(name, slots.size, pack(slots).size)
        }
    }
}
