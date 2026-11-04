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
    /** Equal-pitch slot; front faces share the slot’s right edge. */
    val slotLeft: Float,
) {
    val right get() = left + billWidth + (visibleCount - 1) * fanStep
    val exposedHeight get() = billHeight * 0.20f
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
 * Length comes solely from the pocket depth. Right-aligned slots leave the last fan room to
 * spread leftwards instead of pushing its front face through the pocket's right seam.
 */
fun pocketBillGroups(counts: Map<Denomination, Int>, pocket: WalletRect): List<PocketBillGroup> {
    val bills = Denomination.descending.filter { it.kind == Denomination.Kind.BILL && (counts[it] ?: 0) > 0 }
    if (bills.isEmpty() || pocket.width <= 0f || pocket.height <= 0f) return emptyList()
    val length = (pocket.height - 0.06f * pocket.height) / 0.80f
    val widths = bills.map { length / it.billAspectRatio }
    val widest = widths.max()
    val pitch = if (bills.size == 1) 0f else ((pocket.width - widest) / (bills.size - 1)).coerceAtLeast(28f)
    return bills.mapIndexed { index, d ->
        val count = counts.getValue(d)
        val slotLeft = if (bills.size == 1) pocket.right - widest else pocket.left + index * pitch
        val frontLeft = slotLeft + widest - widths[index]
        // Keep at least 3dp of the front face visible before the next fan begins.
        val previousFront = if (index == 0) pocket.left else
            pocket.left + (index - 1) * pitch + widest - widths[index - 1] + 3f
        val room = (frontLeft - previousFront).coerceAtLeast(0f)
        val step = if (count == 1) 8f else minOf(8f, room / (count - 1)).coerceAtLeast(3f)
        val visible = minOf(count, (room / step + 0.0001f).toInt() + 1)
        PocketBillGroup(d, count, visible, frontLeft - (visible - 1) * step,
            pocket.top - length * 0.20f, widths[index], length, step, slotLeft)
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
