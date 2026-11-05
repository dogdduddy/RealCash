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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class CashHit(val unit: CashUnit, val fromTray: Boolean, val hit: WalletRect, val face: WalletRect, val lens: WalletRect? = null) {
    fun contains(point: Offset) = hit.contains(point) && (lens == null || WalletOpening.contains(lens, point))
}

@Stable
internal class BillSway(private val scope: CoroutineScope) {
    private val displacement = Animatable(0f)
    val displacementDp get() = displacement.value
    private var motion: Job? = null
    private var pending: Job? = null
    private var held = false

    fun follow(state: SwayState) {
        held = true
        pending?.cancel()
        motion?.cancel()
        motion = scope.launch(start = CoroutineStart.UNDISPATCHED) { displacement.snapTo(state.displacementDp) }
    }

    fun pulse(pulse: SwayPulse) {
        if (held) return
        pending?.cancel()
        pending = scope.launch {
            delay(pulse.delayMillis)
            if (!held) {
                motion?.cancel()
                motion = scope.launch {
                    displacement.snapTo(pulse.state.displacementDp)
                    returnToRest()
                }
            }
        }
    }

    fun release() {
        held = false
        pending?.cancel()
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
    private val passed = mutableSetOf<BillSway>()
    private val held = mutableMapOf<BillSway, Float>()
    private var direction = 0

    fun move(from: Offset, to: Offset, papers: List<Pair<WalletRect, BillSway>>) {
        val nextDirection = when {
            to.x > from.x -> 1
            to.x < from.x -> -1
            else -> direction
        }
        if (nextDirection == 0) return
        if (direction != nextDirection) {
            held.keys.forEach { it.release() }
            held.clear()
            passed.clear()
        }
        direction = nextDirection
        val indices = if (direction > 0) papers.indices.toList() else papers.indices.reversed()
        for (index in indices) {
            val (strip, animation) = papers[index]
            val crossed = SwayState.crosses(from, to, strip)
            if (crossed && ticked.add(animation)) tick()
            if (!crossed && !strip.contains(to)) ticked.remove(animation)
            if (!crossed && animation !in held) {
                passed.remove(animation)
                continue
            }
            if (animation in passed) continue
            val edge = if (direction > 0) strip.right else strip.left
            val neighbor = papers.getOrNull(index + direction)?.first
            val neighborEdge = neighbor?.let { if (direction > 0) it.right else it.left }
            val clearance = neighborEdge?.let { abs(it - edge) } ?: 11f
            val contact = SwayState.contact(to.x, direction, strip, clearance)
            val peak = held[animation]?.let {
                if (abs(it) > abs(contact.state.displacementDp)) it else contact.state.displacementDp
            } ?: contact.state.displacementDp
            // A skipped sample still releases from the maximum displacement, never from zero.
            animation.follow(contact.state)
            held[animation] = peak
            if (contact.released || to.y !in strip.top..strip.bottom) {
                animation.release()
                held.remove(animation)
                passed.add(animation)
                if (contact.released) SwayState.neighbors(index, direction, papers.size, peak).forEach { pulse ->
                    papers[pulse.index].second.pulse(pulse)
                }
            }
        }
    }

    fun release(papers: List<Pair<WalletRect, BillSway>>) {
        papers.forEach { it.second.release() }
        held.clear()
        passed.clear()
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
        val hit = hits.value.lastOrNull { it.contains(origin) }
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
