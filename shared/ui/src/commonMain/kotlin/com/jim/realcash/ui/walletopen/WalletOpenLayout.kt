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
    val exposedWidth: Float,
) {
    val right get() = left + exposedWidth + (visibleCount - 1) * fanStep
    val frontLeft get() = left + (visibleCount - 1) * fanStep
}

/** Reviewed wallet.open.b.opening and opening.usable, in image coordinates. */
object WalletOpening {
    const val TOP = 0.02f
    const val LEFT = 0.10f
    const val RIGHT = 0.92f
    const val BOTTOM = 0.98f
    const val USABLE_TOP = 0.15f
    const val USABLE_LEFT = 0.17f
    const val USABLE_RIGHT = 0.83f
    const val USABLE_BOTTOM = 0.85f
    const val FACE_FRACTION = 0.35f
    const val ROTATION_X = 60f
    const val GROUP_GAP = 6f

    /** Same quadratic lens used to clip the artwork and reject hits on the walls. */
    fun contains(wallet: WalletRect, point: androidx.compose.ui.geometry.Offset): Boolean {
        val y = (point.y - wallet.top) / wallet.height
        val t = (y - TOP) / (BOTTOM - TOP)
        if (t !in 0f..1f) return false
        val mid = (LEFT + RIGHT) / 2f
        val spread = 4f * t * (1f - t)
        val x = (point.x - wallet.left) / wallet.width
        return x >= mid + (LEFT - mid) * spread && x <= mid + (RIGHT - mid) * spread
    }
}

data class WalletOpenLayout(val wallet: WalletRect, val pocket: WalletRect, val tray: WalletRect, val trayInner: WalletRect, val coins: WalletRect) {
    companion object {
        fun calculate(width: Float, height: Float): WalletOpenLayout {
            require(width > 0 && height > 0)
            val landscape = width > height
            val trayWidth = minOf(width * (if (landscape) 0.43f else 0.92f), height * (if (landscape) 0.30f else 0.22f) * 1024f / 427f)
            val tray = WalletRect(if (landscape) 12f else (width - trayWidth) / 2f, 78f, trayWidth, trayWidth * 427f / 1024f)
            val walletHeight = if (landscape) minOf(height - 24f, (width - tray.right - 88f) * 1518f / 640f)
                else minOf(height * 0.60f, (height - tray.bottom - 70f).coerceAtLeast(1f), width * 0.72f * 1518f / 640f)
            val walletWidth = walletHeight * 640f / 1518f
            val wallet = WalletRect(if (landscape) width - walletWidth - 76f else (width - walletWidth) / 2f,
                height - walletHeight - 12f, walletWidth, walletHeight)
            return WalletOpenLayout(
                wallet, wallet.inner(WalletOpening.USABLE_LEFT, WalletOpening.USABLE_TOP, WalletOpening.USABLE_RIGHT, WalletOpening.USABLE_BOTTOM),
                tray, tray.inner(0.05f, 0.13f, 0.95f, 0.90f),
                WalletRect(wallet.right + 6f, wallet.top + walletHeight * 0.12f,
                    (width - wallet.right - 12f).coerceAtLeast(0f), walletHeight * 0.80f),
            )
        }
    }
}

/**
 * Upright long edges run down the lens. Only the top 35% of each face's short
 * edge is visible, foreshortened by rotationX(60). Full dimensions remain available
 * for pickup; count-weighted fans compress without dropping paper or adding badges.
 */
fun pocketBillGroups(counts: Map<Denomination, Int>, pocket: WalletRect): List<PocketBillGroup> {
    val bills = Denomination.descending.filter { it.kind == Denomination.Kind.BILL && (counts[it] ?: 0) > 0 }
    if (bills.isEmpty() || pocket.width <= 0f || pocket.height <= 0f) return emptyList()
    val longest = pocket.height * 0.94f
    val width = longest / Denomination.W50000.billAspectRatio
    val exposedWidth = width * WalletOpening.FACE_FRACTION * 0.5f // cos(60°)
    val gap = minOf(WalletOpening.GROUP_GAP, pocket.width / (bills.size * 4f))
    val strip = minOf(exposedWidth, (pocket.width - gap * (bills.size - 1)) / bills.size)
    val extra = (pocket.width - gap * (bills.size - 1) - strip * bills.size).coerceAtLeast(0f)
    val weights = bills.map { (counts.getValue(it) - 1).coerceAtLeast(0).toFloat() }
    val total = weights.sum().coerceAtLeast(1f)
    var cursor = pocket.left
    return bills.mapIndexed { index, d ->
        val count = counts.getValue(d)
        val fan = minOf(extra * weights[index] / total, 8f * (count - 1))
        val step = if (count == 1) 0f else fan / (count - 1)
        val length = width * d.billAspectRatio
        val left = cursor
        cursor += strip + fan + gap
        PocketBillGroup(d, count, count, left, pocket.top + (pocket.height - length) / 2f,
            width, length, step, left, strip + fan, length, strip)
    }
}

/** The exposed front strip and fan pick the front unit, never the hidden face. */
fun pocketBillHitBounds(groups: List<PocketBillGroup>, index: Int): WalletRect {
    val group = groups[index]
    return WalletRect(group.left, group.top, group.right - group.left, group.exposedHeight)
}

/** Horizontal tray faces overlap by a fixed pitch, with coins following the final bill edge. */
fun trayBillBounds(denominations: List<Denomination>, area: WalletRect): List<WalletRect> {
    val height = area.height * 0.62f
    return denominations.mapIndexed { index, d ->
        WalletRect(area.left + index * 24f, area.top + (area.height - height) / 2f,
            height * d.billAspectRatio, height)
    }
}
