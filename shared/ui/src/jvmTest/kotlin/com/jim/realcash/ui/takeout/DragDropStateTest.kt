package com.jim.realcash.ui.takeout

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.jim.realcash.domain.model.Denomination
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DragDropStateTest {
    private fun state() = DragDropState().apply { zoneBounds = Rect(0f, 0f, 100f, 100f) }

    @Test
    fun dropInsideZoneReturnsDenomination() {
        val s = state()
        s.start(Denomination.W10000, Offset(50f, 300f))
        assertFalse(s.isOverZone)
        s.moveBy(Offset(0f, -250f))
        assertTrue(s.isOverZone)
        assertEquals(Denomination.W10000, s.end())
        assertNull(s.dragged)
        assertFalse(s.isOverZone)
    }

    @Test
    fun dropOutsideOrCancelDropsNothing() {
        val s = state()
        s.start(Denomination.W5000, Offset(50f, 300f))
        s.moveBy(Offset(0f, -100f))
        assertNull(s.end())
        s.start(Denomination.W5000, Offset(50f, 50f))
        assertTrue(s.isOverZone)
        s.cancel()
        assertNull(s.dragged)
        assertNull(s.end())
    }

    @Test
    fun movesWithoutDragAreIgnored() {
        val s = state()
        s.moveBy(Offset(10f, 10f))
        assertEquals(Offset.Zero, s.position)
    }
}
