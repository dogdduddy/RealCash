package com.jim.realcash.ui.walletopen

import kotlin.math.hypot
import kotlin.random.Random

/** Centre coordinates and clockwise angle. Unplaced overflow is deliberately excluded, never overlapped. */
data class ScatteredCoin(val index: Int, val x: Float, val y: Float, val diameter: Float, val rotation: Float)

object CoinScatter {
    const val MAX_DIAMETER = 34f
    const val EDGE_MARGIN = 12f
    const val JITTER = 3f
    const val GAP = 6f

    fun place(seed: Int, count: Int, area: WalletRect, diameter: Float = MAX_DIAMETER): List<ScatteredCoin> =
        place(seed, List(count.coerceAtLeast(0)) { diameter }, area)

    /** Rightmost column first; a second column is allowed only inside the paper-free strip. */
    fun place(seed: Int, diameters: List<Float>, area: WalletRect): List<ScatteredCoin> {
        require(diameters.all { it > 0f && it.isFinite() })
        val largest = diameters.maxOrNull() ?: return emptyList()
        if (area.width + 0.001f < largest + 2f * JITTER || area.height <= 0f) return emptyList()
        val random = Random(seed)
        val pitch = largest + GAP + 2f * JITTER
        val rightCenter = area.right - JITTER - largest / 2f
        var column = 0
        var top = area.top
        return buildList {
            diameters.forEachIndexed { index, diameter ->
                if (diameter > area.height) return@forEachIndexed
                if (top + diameter > area.bottom) {
                    column++
                    top = area.top
                }
                val center = rightCenter - column * pitch
                if (column >= 2 || center - largest / 2f - JITTER < area.left - 0.001f) return@forEachIndexed
                add(ScatteredCoin(index, center + (random.nextFloat() * 2f - 1f) * JITTER,
                    top + diameter / 2f, diameter, random.nextFloat() * 360f))
                top += diameter + GAP
            }
        }
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
