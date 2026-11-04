package com.jim.realcash.ui.walletopen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.ui.Haptics
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class CashHit(val unit: CashUnit, val fromTray: Boolean, val hit: WalletRect, val face: WalletRect)

@Stable
internal class BillSway(private val scope: CoroutineScope) {
    val angle = Animatable(0f)
    val x = Animatable(0f)
    private var pending: Job? = null
    private var angleJob: Job? = null
    private var xJob: Job? = null
    private var latest = SwayState.Rest
    fun push(target: SwayState) {
        latest = target
        // Throttle the stagger independently of the springs. Continuous input cannot starve distant bills.
        if (pending?.isActive == true) return
        pending = scope.launch { delay(target.delayMillis); animate(latest) }
    }
    fun release() {
        pending?.cancel()
        latest = SwayState.Rest
        animate(latest)
    }
    private fun animate(target: SwayState) {
        angleJob?.cancel(); xJob?.cancel()
        angleJob = scope.launch { angle.animateTo(target.rotation, spring(SwayState.DAMPING, SwayState.STIFFNESS)) }
        xJob = scope.launch { x.animateTo(target.offsetDp, spring(SwayState.DAMPING, SwayState.STIFFNESS)) }
    }
}

/** One recognizer owns both modes; the 400ms timeout cannot race a separate swipe recognizer. */
internal fun Modifier.walletGestures(
    enabled: Boolean,
    density: Float,
    hits: () -> List<CashHit>,
    pocket: WalletRect,
    sway: List<Pair<WalletRect, BillSway>>,
    physics: WalletOpenPhysics,
    haptics: Haptics,
    onRelease: (Boolean) -> Unit,
): Modifier = pointerInput(enabled, density, pocket, physics, sway) {
    if (!enabled) return@pointerInput
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val origin = down.position / density
        val hit = hits().lastOrNull { it.hit.contains(origin) }
        if (physics.picked != null || (hit == null && !pocket.contains(origin))) return@awaitEachGesture
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
        } else if (!released && moved && horizontal && (pocket.contains(origin) || hit?.fromTray == false)) {
            val velocity = VelocityTracker()
            velocity.addPosition(down.uptimeMillis, down.position)
            var previous = origin
            val ticked = mutableSetOf<Int>()
            try {
                while (true) {
                    val change = last
                    if (!change.pressed || change.isConsumed) break
                    velocity.addPosition(change.uptimeMillis, change.position)
                    val point = change.position / density
                    val speed = velocity.calculateVelocity().x
                    sway.forEach { (rect, animation) ->
                        animation.push(SwayState.target(speed, rect.center.x - point.x, pocket.width))
                    }
                    // Every exposed paper strip gets one tick per pass, even with skipped pointer samples.
                    sway.forEachIndexed { index, (rect, _) ->
                        val right = minOf(rect.right, sway.getOrNull(index + 1)?.first?.left ?: rect.right)
                        val strip = rect.copy(width = (right - rect.left).coerceAtLeast(0f), height = (pocket.top - rect.top).coerceAtLeast(0f))
                        if (index !in ticked && SwayState.crosses(previous, point, strip)) { haptics.tick(); ticked += index }
                        if (!strip.contains(point) && !SwayState.crosses(previous, point, strip)) ticked -= index
                    }
                    previous = point
                    change.consume()
                    last = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                }
            } finally { sway.forEach { it.second.release() } }
        }
    }
}
