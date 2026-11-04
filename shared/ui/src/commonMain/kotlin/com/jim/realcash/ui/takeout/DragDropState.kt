package com.jim.realcash.ui.takeout

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.jim.realcash.domain.model.Denomination

/**
 * Hoisted drag & drop state of one screen (ported from HardPay's wallet screen).
 *
 * Every position is in root coordinates (`positionInRoot` / `boundsInRoot`), so the source stacks, the drop zone and
 * the ghost layer can live in different parts of the tree.
 *
 * Performance properties kept from HardPay:
 * - [position] changes on every pointer move, but composition only reads [isOverZone] (a `derivedStateOf`, so it
 *   invalidates on the edge only) and [dragged] (start/end only);
 * - the ghost reads [position] lazily inside `graphicsLayer {}`, so a move re-runs only that layer's draw phase.
 */
@Stable
class DragDropState {
    /** Denomination being dragged, or `null` when no drag is in progress. */
    var dragged: Denomination? by mutableStateOf(null)
        private set

    /** Pointer position (root coordinates) of the current drag. Read it only in draw / layout lambdas. */
    var position: Offset by mutableStateOf(Offset.Zero)
        private set

    /** Bounds of the drop zone in root coordinates, reported by the zone's `onGloballyPositioned`. */
    var zoneBounds: Rect? by mutableStateOf(null)

    /** Root position of the full-size box that hosts the ghost (its translation origin). */
    var hostOrigin: Offset by mutableStateOf(Offset.Zero)

    /** True while a dragged unit is over the drop zone. Changes only when the pointer crosses the zone's edge. */
    val isOverZone: Boolean by derivedStateOf { dragged != null && zoneBounds?.contains(position) == true }

    /** A drag of [denomination] started at [rootPosition]. */
    fun start(denomination: Denomination, rootPosition: Offset) {
        position = rootPosition
        dragged = denomination
    }

    /** The pointer moved by [delta]. */
    fun moveBy(delta: Offset) {
        if (dragged != null) position += delta
    }

    /** The pointer was lifted: returns the dropped denomination when it was over the zone, and resets the drag. */
    fun end(): Denomination? {
        val dropped = dragged.takeIf { isOverZone }
        dragged = null
        return dropped
    }

    /** The gesture was cancelled: nothing is dropped. */
    fun cancel() {
        dragged = null
    }
}
