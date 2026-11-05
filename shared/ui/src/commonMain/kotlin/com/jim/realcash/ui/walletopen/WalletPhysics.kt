package com.jim.realcash.ui.walletopen

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.ui.Haptics

/** Local default until the platform haptics API gains a dedicated selection tick. */
fun Haptics.tick() = tap()

/** One signed, density-independent displacement drives both deferred layer transforms. */
data class SwayState(val displacementDp: Float) {
    companion object {
        const val DAMPING = 0.35f
        const val STIFFNESS = 420f
        val Rest = SwayState(0f)

        fun rotationX(displacementDp: Float): Float =
            WalletOpening.ROTATION_X - (displacementDp * 1.2f).coerceIn(-12f, 12f)

        fun limit(neighborDistanceDp: Float): Float =
            minOf(10f, (neighborDistanceDp - 1f).coerceAtLeast(0f))

        /** The leading edge sticks to the finger until the inter-paper clearance is exhausted. */
        fun contact(fingerX: Float, direction: Int, strip: WalletRect, neighborDistanceDp: Float): SwayContact {
            require(direction == -1 || direction == 1)
            val edge = if (direction > 0) strip.right else strip.left
            val travel = ((fingerX - edge) * direction).coerceAtLeast(0f)
            val limit = limit(neighborDistanceDp)
            return SwayContact(SwayState(minOf(travel, limit) * direction), travel > limit)
        }

        /** Only the next two papers in the swipe direction receive a pulse, across group boundaries too. */
        fun neighbors(index: Int, direction: Int, count: Int, peakDp: Float): List<SwayPulse> {
            require(direction == -1 || direction == 1)
            return (1..2).mapNotNull { distance ->
                val neighbor = index + distance * direction
                if (neighbor !in 0 until count) null
                else SwayPulse(neighbor, SwayState(peakDp / (1 shl distance)), distance * 20L)
            }
        }

        /** Includes fast swipes which cross a narrow strip between two pointer events. */
        fun crosses(from: Offset, to: Offset, strip: WalletRect): Boolean {
            if (strip.width <= 0f || strip.height <= 0f) return false
            var enter = 0f
            var exit = 1f
            for ((origin, delta, low, high) in listOf(
                listOf(from.x, to.x - from.x, strip.left, strip.right),
                listOf(from.y, to.y - from.y, strip.top, strip.bottom),
            )) {
                if (delta == 0f) { if (origin < low || origin > high) return false }
                else {
                    val a = (low - origin) / delta
                    val b = (high - origin) / delta
                    enter = maxOf(enter, minOf(a, b))
                    exit = minOf(exit, maxOf(a, b))
                    if (enter > exit) return false
                }
            }
            return true
        }
    }
}

data class SwayContact(val state: SwayState, val released: Boolean)
data class SwayPulse(val index: Int, val state: SwayState, val delayMillis: Long)

fun WalletRect.contains(point: Offset) = point.x >= left && point.x <= right && point.y >= top && point.y <= bottom
val WalletRect.center get() = Offset(left + width / 2f, top + height / 2f)

enum class DropDestination { TRAY, WALLET, FLOOR, SOURCE }

/** Immutable pickup metadata and pure drop decisions. No repository or animation clock is needed to test it. */
data class PickState(val unit: CashUnit, val fromTray: Boolean, val source: WalletRect, val start: Offset) {
    val isBill get() = unit.denomination.kind == com.jim.realcash.domain.model.Denomination.Kind.BILL
    val faceWidth get() = if (isBill && !fromTray) source.height else source.width
    val faceHeight get() = if (isBill && !fromTray) source.width else source.height
    val rotation get() = if (isBill && !fromTray) 90f else 0f
    fun centerAt(pointer: Offset) = pointer
    fun destination(pointer: Offset, tray: WalletRect, wallet: WalletRect, cancelled: Boolean = false): DropDestination = when {
        cancelled -> DropDestination.SOURCE
        tray.contains(pointer) -> DropDestination.TRAY
        fromTray && wallet.contains(pointer) -> DropDestination.WALLET
        else -> DropDestination.SOURCE
    }
    internal fun flight(destination: DropDestination, target: WalletRect) = DropFlight(
        target,
        when {
            !isBill -> 0f
            destination == DropDestination.TRAY -> 0f
            destination == DropDestination.WALLET -> 90f
            else -> rotation
        },
        returning = destination != DropDestination.TRAY,
    )
    companion object {
        const val LONG_PRESS_MILLIS = 400L
        const val LIFT_SCALE = 1.08f
        const val RETURN_MILLIS = 300L
    }
}

/** Per-frame values are read only from pointer handlers and graphicsLayer lambdas. */
@Stable
class WalletOpenPhysics {
    var picked: PickState? by mutableStateOf(null)
        private set
    var position by mutableStateOf(Offset.Zero)
        private set
    var tray: WalletRect by mutableStateOf(WalletRect(0f, 0f, 0f, 0f))
    val overTray by derivedStateOf { picked != null && tray.contains(position) }
    // Animation progress is another deferred layer read, never a composition input.
    var settling by mutableStateOf(false)
        private set
    fun start(pick: PickState): Boolean {
        if (picked != null) return false
        position = pick.start
        picked = pick
        settling = false
        return true
    }
    fun move(pointer: Offset) { if (!settling && picked != null) position = pointer }
    internal var flight: DropFlight? = null
    internal fun settle(drop: DropFlight) {
        flight = drop
        settling = true
    }
    fun clear() { picked = null; settling = false; flight = null }
}

internal data class DropFlight(val target: WalletRect, val rotation: Float, val returning: Boolean)
