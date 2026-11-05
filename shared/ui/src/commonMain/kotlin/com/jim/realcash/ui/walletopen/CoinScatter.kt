package com.jim.realcash.ui.walletopen

import kotlin.math.hypot
import kotlin.random.Random

/** Centre coordinates and clockwise angle. Unplaced overflow is deliberately excluded, never overlapped. */
data class ScatteredCoin(val index: Int, val x: Float, val y: Float, val diameter: Float, val rotation: Float)

object CoinScatter {
    fun place(seed: Int, count: Int, area: WalletRect, diameter: Float = 34f): List<ScatteredCoin> =
        place(seed, List(count.coerceAtLeast(0)) { diameter }, area)

    /** Uses actual diameters, so smaller coins retain their physical proportions. */
    fun place(seed: Int, diameters: List<Float>, area: WalletRect): List<ScatteredCoin> {
        require(diameters.all { it > 0f && it.isFinite() })
        val random = Random(seed)
        val placed = mutableListOf<ScatteredCoin>()
        diameters.forEachIndexed { index, diameter ->
            val r = diameter / 2f
            if (area.width < diameter || area.height < diameter) return@forEachIndexed
            var candidate: ScatteredCoin? = null
            repeat(400) {
                if (candidate == null) {
                    val x = area.left + r + random.nextFloat() * (area.width - diameter)
                    val y = area.top + r + random.nextFloat() * (area.height - diameter)
                    // A full radius sum is stricter than the requested 0.9 diameter minimum.
                    if (placed.all { hypot(x - it.x, y - it.y) >= (diameter + it.diameter) / 2f + 1f }) {
                        candidate = ScatteredCoin(index, x, y, diameter, random.nextFloat() * 360f)
                    }
                }
            }
            candidate?.let(placed::add)
        }
        return placed
    }
}

/** A floor drop preserves its exact centre, or springs back if it hits art, an edge or another coin. */
internal fun floorCoinTarget(
    pointer: androidx.compose.ui.geometry.Offset,
    diameter: Float,
    floor: WalletRect,
    wallet: WalletRect,
    tray: WalletRect,
    occupied: List<WalletRect>,
): WalletRect? {
    val target = WalletRect(pointer.x - diameter / 2f, pointer.y - diameter / 2f, diameter, diameter)
    if (target.left < floor.left || target.right > floor.right || target.top < floor.top || target.bottom > floor.bottom) return null
    fun overlapsArt(rect: WalletRect) = target.left < rect.right && target.right > rect.left && target.top < rect.bottom && target.bottom > rect.top
    if (overlapsArt(wallet) || overlapsArt(tray)) return null
    if (occupied.any { hypot(pointer.x - it.center.x, pointer.y - it.center.y) < (diameter + it.width) / 2f + 1f }) return null
    return target
}
