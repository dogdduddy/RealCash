package com.jim.realcash.ui.walletopen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.ui.Haptics
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal data class CashHit(val unit: CashUnit, val fromTray: Boolean, val hit: WalletRect, val face: WalletRect, val visible: WalletRect = hit) {
    fun contains(point: Offset) = hit.contains(point)
}

@Stable
internal class BillSway(private val scope: CoroutineScope) {
    private val displacement = Animatable(0f)
    val displacementDp get() = displacement.value
    private var motion: Job? = null

    fun follow(state: SwayState) {
        motion?.cancel()
        motion = scope.launch(start = CoroutineStart.UNDISPATCHED) { displacement.snapTo(state.displacementDp) }
    }

    fun release() {
        motion?.cancel()
        motion = scope.launch { returnToRest() }
    }

    private suspend fun returnToRest() {
        displacement.animateTo(0f, spring(SwayState.DAMPING, SwayState.STIFFNESS))
    }
}

/** Pointer-local bookkeeping; no frame state is observed by composition. Geometry stays at rest. */
internal class BillSwayGesture(private val tick: () -> Unit) {
    private val ticked = mutableSetOf<BillSway>()
    private val anchors = mutableMapOf<BillSway, Float>()

    fun move(from: Offset, to: Offset, papers: List<Pair<WalletRect, BillSway>>) {
        if (to.x == from.x) return
        papers.groupBy({ it.second }, { it.first }).forEach { (animation, pieces) ->
            val contact = pieces.firstOrNull { SwayState.crosses(from, to, it) }
            if (contact != null) {
                if (ticked.add(animation)) tick()
                val anchor = anchors.getOrPut(animation) { from.x.coerceIn(contact.left, contact.right) }
                animation.follow(SwayState.slide(to.x - anchor))
                if (pieces.none { it.contains(to) }) {
                    animation.release()
                    anchors.remove(animation)
                }
            } else if (anchors.remove(animation) != null) animation.release()
        }
    }

    fun release(papers: List<Pair<WalletRect, BillSway>>) {
        papers.map { it.second }.distinct().forEach { it.release() }
        anchors.clear()
        ticked.clear()
    }
}

/** One recognizer owns both modes; the 400ms timeout cannot race a separate swipe recognizer. */
internal fun Modifier.walletGestures(
    enabled: State<Boolean>,
    density: Float,
    hits: State<List<CashHit>>,
    pocket: State<WalletRect>,
    sway: State<List<Pair<WalletRect, BillSway>>>,
    physics: WalletOpenPhysics,
    haptics: Haptics,
    onRelease: (Boolean) -> Unit,
): Modifier = pointerInput(physics, density) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        // Saving blocks only new gestures; a gesture already in progress keeps its owner.
        if (!enabled.value) return@awaitEachGesture
        val origin = down.position / density
        val hit = hits.value.lastOrNull { it.visible.contains(origin) }
            ?: hits.value.lastOrNull { it.contains(origin) }
        if (physics.picked != null || (hit == null && !pocket.value.contains(origin))) return@awaitEachGesture
        var last = down
        var released = false
        var moved = false
        var horizontal = false
        val held = withTimeoutOrNull(PickState.LONG_PRESS_MILLIS) {
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                last = change
                if (!change.pressed || change.isConsumed) { released = true; break }
                val delta = change.position - down.position
                if (delta.getDistance() > viewConfiguration.touchSlop) {
                    moved = true
                    horizontal = abs(delta.x) > abs(delta.y)
                    break
                }
            }
            false
        } == null
        if (held && hit != null) {
            if (!physics.start(PickState(hit.unit, hit.fromTray, hit.face, origin))) return@awaitEachGesture
            haptics.pickUp()
            var ended = false
            try {
                while (true) {
                    val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                    physics.move(change.position / density)
                    if (change.isConsumed) break
                    change.consume()
                    if (!change.pressed) { onRelease(false); ended = true; break }
                }
            } finally { if (!ended) onRelease(true) }
        } else if (!released && moved && horizontal && (pocket.value.contains(origin) || hit?.fromTray == false)) {
            var previous = origin
            val gesture = BillSwayGesture { haptics.tick() }
            try {
                while (true) {
                    val change = last
                    if (!change.pressed || change.isConsumed) break
                    val point = change.position / density
                    gesture.move(previous, point, sway.value)
                    previous = point
                    change.consume()
                    last = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                }
            } finally { gesture.release(sway.value) }
        }
    }
}
