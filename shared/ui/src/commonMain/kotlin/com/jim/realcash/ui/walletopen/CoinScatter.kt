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
        if (placed.size == diameters.size) return placed
        // A narrow floor can be jammed by early random choices even when every coin fits.
        // Pack rows as a deterministic fallback, keeping a seeded offset and individual rotation.
        val rows = mutableListOf<MutableList<Pair<Int, Float>>>()
        var row = mutableListOf<Pair<Int, Float>>()
        var rowWidth = 0f
        var usedHeight = 0f
        diameters.withIndex().sortedByDescending { it.value }.forEach { (index, diameter) ->
            if (diameter > area.width || diameter > area.height) return@forEach
            if (row.isNotEmpty() && rowWidth + 1f + diameter > area.width) {
                usedHeight += row.maxOf { it.second } + 1f
                rows += row
                row = mutableListOf()
                rowWidth = 0f
            }
            if (usedHeight + diameter <= area.height) {
                if (row.isNotEmpty()) rowWidth += 1f
                row += index to diameter
                rowWidth += diameter
            }
        }
        if (row.isNotEmpty()) rows += row
        if (rows.sumOf { it.size } <= placed.size) return placed
        val height = rows.sumOf { it.maxOf { coin -> coin.second }.toDouble() }.toFloat() + (rows.size - 1)
        var y = area.top + random.nextFloat() * (area.height - height).coerceAtLeast(0f)
        return rows.flatMap { coins ->
            val rowHeight = coins.maxOf { it.second }
            val width = coins.sumOf { it.second.toDouble() }.toFloat() + (coins.size - 1)
            var x = area.left + random.nextFloat() * (area.width - width).coerceAtLeast(0f)
            val result = coins.map { (index, diameter) ->
                ScatteredCoin(index, x + diameter / 2f, y + rowHeight / 2f, diameter, random.nextFloat() * 360f)
                    .also { x += diameter + 1f }
            }
            y += rowHeight + 1f
            result
        }.sortedBy { it.index }
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
