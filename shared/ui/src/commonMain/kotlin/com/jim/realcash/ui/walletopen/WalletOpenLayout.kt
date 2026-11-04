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
) {
    val right get() = left + billWidth + (visibleCount - 1) * fanStep
    val exposedHeight get() = billHeight * 0.28f
}

data class WalletOpenLayout(val wallet: WalletRect, val pocket: WalletRect, val tray: WalletRect, val trayInner: WalletRect, val coins: WalletRect) {
    companion object {
        fun calculate(width: Float, height: Float): WalletOpenLayout {
            require(width > 0 && height > 0)
            val trayWidth = minOf(width * 0.92f, height * 0.22f * 1024f / 427f)
            val tray = WalletRect((width - trayWidth) / 2f, 78f, trayWidth, trayWidth * 427f / 1024f)
            // Reserve both the right-hand strip and room for the tray controls on smaller screens.
            val walletHeight = minOf(height * 0.60f, width * 0.72f * 1024f / 640f,
                (height - tray.bottom - 76f).coerceAtLeast(1f))
            val walletWidth = walletHeight * 640f / 1024f
            val wallet = WalletRect((width - walletWidth) / 2f, height - walletHeight - 12f, walletWidth, walletHeight)
            return WalletOpenLayout(
                wallet, wallet.inner(0.037f, 0.405f, 0.963f, 0.98f),
                tray, tray.inner(0.05f, 0.13f, 0.95f, 0.90f),
                WalletRect(wallet.right + 4f, wallet.top + walletHeight * 0.20f,
                    (width - wallet.right - 8f).coerceAtLeast(0f), walletHeight * 0.74f),
            )
        }
    }
}

/** Largest bills on the left. Equal-width group budgets preserve at least 14dp between groups. */
fun pocketBillGroups(counts: Map<Denomination, Int>, pocket: WalletRect): List<PocketBillGroup> {
    val bills = Denomination.descending.filter { it.kind == Denomination.Kind.BILL && (counts[it] ?: 0) > 0 }
    if (bills.isEmpty()) return emptyList()
    val slotWidth = ((pocket.width - 14f * (bills.size - 1)) / bills.size).coerceAtLeast(0f)
    if (slotWidth <= 0f) return emptyList()
    val faceWidth = minOf(58f, slotWidth * 0.78f, pocket.height / 2.27f)
    return bills.mapIndexed { index, d ->
        val count = counts.getValue(d)
        val room = slotWidth - faceWidth
        val step = minOf(10f, room / (count - 1).coerceAtLeast(1)).coerceAtLeast(3f)
        val visible = minOf(count, (room / step).toInt() + 1)
        val length = faceWidth * d.billAspectRatio
        PocketBillGroup(d, count, visible, pocket.left + index * (slotWidth + 14f),
            pocket.top - length * 0.28f, faceWidth, length, step)
    }
}
