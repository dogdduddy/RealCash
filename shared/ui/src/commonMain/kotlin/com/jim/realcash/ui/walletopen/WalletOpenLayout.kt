package com.jim.realcash.ui.walletopen

import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.ui.money.billAspectRatio

/** All coordinates are logical dp, independent of Compose measurement and device density. */
data class WalletRect(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right get() = left + width
    val bottom get() = top + height
    fun inner(left: Float, top: Float, right: Float, bottom: Float) =
        WalletRect(this.left + width * left, this.top + height * top, width * (right - left), height * (bottom - top))
}

data class PocketBillGroup(
    val denomination: Denomination,
    val count: Int,
    val visibleCount: Int,
    val left: Float,
    val top: Float,
    val billWidth: Float,
    val billHeight: Float,
    val fanStep: Float,
    /** Start and width of the count-weighted fan slot, in dp. */
    val slotLeft: Float,
    val slotWidth: Float,
    val exposedHeight: Float,
) {
    val right get() = left + billWidth + (visibleCount - 1) * fanStep
    val frontLeft get() = left + (visibleCount - 1) * fanStep
}

data class WalletOpenLayout(val wallet: WalletRect, val pocket: WalletRect, val tray: WalletRect, val trayInner: WalletRect, val coins: WalletRect) {
    companion object {
        fun calculate(width: Float, height: Float): WalletOpenLayout {
            require(width > 0 && height > 0)
            val landscape = width > height
            val trayWidth = minOf(width * (if (landscape) 0.43f else 0.92f), height * (if (landscape) 0.30f else 0.22f) * 1024f / 427f)
            val tray = WalletRect(if (landscape) 12f else (width - trayWidth) / 2f, 78f, trayWidth, trayWidth * 427f / 1024f)
            // Reserve both the right-hand strip and room for the tray controls on smaller screens.
            // A short portrait still needs a real pocket, rather than four miniature bill slots.
            // In landscape the tray and wallet sit side by side.
            val minimumHeight = minOf(400f, width * 0.78f * 1024f / 640f)
            val walletHeight = if (landscape) height - 24f else
                minOf(height * 0.60f, width * 0.72f * 1024f / 640f,
                    (height - tray.bottom - 76f).coerceAtLeast(1f)).coerceAtLeast(minimumHeight)
            val walletWidth = walletHeight * 640f / 1024f
            val wallet = WalletRect(if (landscape) width - walletWidth - 76f else (width - walletWidth) / 2f,
                height - walletHeight - 12f, walletWidth, walletHeight)
            return WalletOpenLayout(
                wallet, wallet.inner(0.037f, 0.405f, 0.963f, 0.98f),
                tray, tray.inner(0.05f, 0.13f, 0.95f, 0.90f),
                WalletRect(wallet.right + 4f, wallet.top + walletHeight * 0.20f,
                    (width - wallet.right - 8f).coerceAtLeast(0f), walletHeight * 0.74f),
            )
        }
    }
}

/**
 * Back-to-front drawing order: high denominations first, then each fan from left to right.
 * All bills have the same physical short edge and rest on the same pocket bottom.
 */
fun pocketBillGroups(counts: Map<Denomination, Int>, pocket: WalletRect): List<PocketBillGroup> {
    val bills = Denomination.descending.filter { it.kind == Denomination.Kind.BILL && (counts[it] ?: 0) > 0 }
    if (bills.isEmpty() || pocket.width <= 0f || pocket.height <= 0f) return emptyList()
    val longest = pocket.height * 0.94f / 0.74f
    val width = longest / Denomination.W50000.billAspectRatio
    val bottom = pocket.bottom - pocket.height * 0.06f
    val available = (pocket.width - width).coerceAtLeast(0f)
    val weights = bills.map { 28f + (counts.getValue(it) - 1).toFloat() * 8f }
    val slots = FloatArray(bills.size)
    val pending = bills.indices.toMutableList()
    // Water-fill the proportional shares: fixing a small group at 28dp must not
    // take the extra space intended for groups with more paper.
    var remaining = available.coerceAtLeast(28f * bills.size)
    while (pending.isNotEmpty()) {
        val totalWeight = pending.sumOf { weights[it].toDouble() }.toFloat()
        val atFloor = pending.filter { remaining * weights[it] / totalWeight < 28f }
        if (atFloor.isEmpty()) {
            pending.forEach { slots[it] = remaining * weights[it] / totalWeight }
            break
        }
        atFloor.forEach { slots[it] = 28f; remaining -= 28f }
        pending.removeAll(atFloor)
    }
    var cursor = pocket.left
    return bills.mapIndexed { index, d ->
        val count = counts.getValue(d)
        val slotLeft = cursor
        cursor += slots[index]
        // On a very narrow pocket, the 28dp slot floors can exceed the physical
        // space. Keep the paper inside the seam and badge only at the 3dp floor.
        val left = slotLeft.coerceAtMost(pocket.left + available)
        val room = minOf(slots[index], pocket.left + available - left).coerceAtLeast(0f)
        val step = if (count == 1) 8f else minOf(8f, room / (count - 1)).coerceAtLeast(3f)
        val visible = minOf(count, (room / step + 0.0001f).toInt() + 1)
        val length = width * d.billAspectRatio
        val top = bottom - length
        PocketBillGroup(d, count, visible, left, top, width, length, step, slotLeft,
            slots[index], pocket.top - top)
    }
}

/** Only visible paper is tappable; every strip of a group picks its rightmost/front unit. */
fun pocketBillHitBounds(groups: List<PocketBillGroup>, index: Int): WalletRect {
    val group = groups[index]
    val right = minOf(group.right, groups.getOrNull(index + 1)?.left ?: group.right)
    return WalletRect(group.left, group.top, (right - group.left).coerceAtLeast(0f), group.exposedHeight)
}

/** Horizontal tray faces overlap by a fixed pitch, with coins following the final bill edge. */
fun trayBillBounds(denominations: List<Denomination>, area: WalletRect): List<WalletRect> {
    val height = area.height * 0.62f
    return denominations.mapIndexed { index, d ->
        WalletRect(area.left + index * 24f, area.top + (area.height - height) / 2f,
            height * d.billAspectRatio, height)
    }
}
