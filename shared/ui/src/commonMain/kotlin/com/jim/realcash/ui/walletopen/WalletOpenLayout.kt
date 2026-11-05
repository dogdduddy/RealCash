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

/** Reviewed assets/manifest.json wallet.open.c.opening; artwork is always shown whole. */
object WalletOpening {
    const val X = 0.975f
    const val TOP = 0.03f
    const val BOTTOM = 0.97f
    const val HEAD_STEP = 14f
    const val PAPER_STEP = 1.5f
    const val MAX_STACK_OFFSET = 12f
    const val HIT_PADDING = 8f

    fun contains(wallet: WalletRect, point: androidx.compose.ui.geometry.Offset): Boolean =
        point.x >= wallet.left + wallet.width * X &&
            point.y in (wallet.top + wallet.height * TOP)..(wallet.top + wallet.height * BOTTOM)
}

data class PocketBillGroup(
    val denomination: Denomination,
    val count: Int,
    val left: Float,
    val top: Float,
    val billWidth: Float,
    val billHeight: Float,
    val fanStep: Float,
    val openingX: Float,
    val exposedWidth: Float,
) {
    val visibleCount get() = count
    val right get() = openingX + exposedWidth + (count - 1) * fanStep
    val frontLeft get() = left
    val exposedHeight get() = billHeight
    val bottom get() = top + billHeight + (count - 1) * fanStep
    val face get() = WalletRect(left, top, billWidth, billHeight)

    /** Back papers stay in their slots while the front paper is held. */
    fun drawOrder(frontPicked: Boolean = false): List<Int> =
        (count - 1 downTo if (frontPicked) 1 else 0).toList()

    fun exposedBillBounds(index: Int): WalletRect = WalletRect(
        openingX, top + index * fanStep, exposedWidth + index * fanStep, billHeight,
    )
}

data class WalletOpenLayout(val wallet: WalletRect, val pocket: WalletRect, val tray: WalletRect, val trayInner: WalletRect, val coins: WalletRect) {
    companion object {
        fun calculate(width: Float, height: Float): WalletOpenLayout {
            require(width > 0 && height > 0)
            val landscape = width > height
            val trayWidth = minOf(width * (if (landscape) 0.43f else 0.92f), height * (if (landscape) 0.30f else 0.22f) * 1024f / 427f)
            val tray = WalletRect(if (landscape) 12f else (width - trayWidth) / 2f, 78f, trayWidth, trayWidth * 427f / 1024f)
            val walletHeight = if (landscape) height - tray.top - 12f
                else minOf(height * 0.55f, height - tray.bottom - 42f)
            val walletWidth = walletHeight * 640f / 1230f
            val wallet = WalletRect(if (landscape) tray.right + 24f else 12f,
                if (landscape) tray.top else minOf(height - walletHeight - 12f, tray.bottom + 104f), walletWidth, walletHeight)
            // pocket.left is the vertical clipping line, not the hidden left edge of a full note.
            val pocket = WalletRect(wallet.left + wallet.width * WalletOpening.X,
                wallet.top + wallet.height * WalletOpening.TOP,
                width - 12f - (wallet.left + wallet.width * WalletOpening.X),
                wallet.height * (WalletOpening.BOTTOM - WalletOpening.TOP))
            val all = pocketBillGroups(Denomination.descending.filter { it.kind == Denomination.Kind.BILL }
                .associateWith { 30 }, pocket)
            val coinTop = all.maxOf { it.bottom } + 8f
            return WalletOpenLayout(wallet, pocket, tray, tray.inner(0.05f, 0.13f, 0.95f, 0.90f),
                WalletRect(wallet.right + 6f, coinTop, width - wallet.right - 18f, height - 4f - coinTop))
        }
    }
}

/** One flat bundle: expensive notes underneath, with fixed denomination heads and real length ratios. */
fun pocketBillGroups(counts: Map<Denomination, Int>, pocket: WalletRect): List<PocketBillGroup> {
    if (pocket.width <= 0f || pocket.height <= 0f) return emptyList()
    val walletHeight = pocket.height / (WalletOpening.BOTTOM - WalletOpening.TOP)
    // Compact screens reserve a full coin diameter below the bundle, including the deepest stack.
    val longest = minOf(walletHeight * 0.8f,
        (walletHeight * (1f - WalletOpening.TOP) - 3f * WalletOpening.HEAD_STEP -
            WalletOpening.MAX_STACK_OFFSET - 8f - 34f) / (136f / 154f),
        (pocket.width - WalletOpening.MAX_STACK_OFFSET - 8f) / 0.55f * Denomination.W50000.billAspectRatio,
    ).coerceAtLeast(1f)
    val shortEdge = longest / Denomination.W50000.billAspectRatio
    return Denomination.descending.filter { it.kind == Denomination.Kind.BILL }.mapIndexedNotNull { index, d ->
        val count = counts[d] ?: 0
        if (count <= 0) return@mapIndexedNotNull null
        val fraction = when (d) {
            Denomination.W1000 -> 0.55f
            Denomination.W5000 -> 0.45f
            Denomination.W10000 -> 0.35f
            else -> 0.25f
        }
        val exposed = shortEdge * fraction
        val step = if (count == 1) 0f else minOf(WalletOpening.PAPER_STEP, WalletOpening.MAX_STACK_OFFSET / (count - 1))
        PocketBillGroup(d, count, pocket.left + exposed - shortEdge,
            pocket.top + index * WalletOpening.HEAD_STEP, shortEdge, shortEdge * d.billAspectRatio,
            step, pocket.left, exposed)
    }
}

/** Only the stepped head is pickable below another denomination; the top group exposes its face. */
fun pocketBillHitBounds(groups: List<PocketBillGroup>, index: Int): WalletRect {
    val group = groups[index]
    val bottom = groups.getOrNull(index + 1)?.top ?: group.bottom
    return WalletRect(group.openingX, group.top - WalletOpening.HIT_PADDING,
        group.right - group.openingX, bottom - group.top + 2f * WalletOpening.HIT_PADDING)
}

/** Subtract front papers to give swipe contact only to paper the finger can actually see. */
internal fun visiblePaperBounds(groups: List<PocketBillGroup>): List<Triple<Denomination, Int, List<WalletRect>>> {
    val paint = groups.flatMap { group -> group.drawOrder().map { Triple(group.denomination, it, group.exposedBillBounds(it)) } }
    return paint.mapIndexed { i, paper ->
        var pieces = listOf(paper.third)
        paint.drop(i + 1).forEach { cover -> pieces = pieces.flatMap { it.subtract(cover.third) } }
        Triple(paper.first, paper.second, pieces)
    }
}

private fun WalletRect.subtract(cover: WalletRect): List<WalletRect> {
    val l = maxOf(left, cover.left); val r = minOf(right, cover.right)
    val t = maxOf(top, cover.top); val b = minOf(bottom, cover.bottom)
    if (l >= r || t >= b) return listOf(this)
    return listOf(WalletRect(left, top, width, t - top), WalletRect(left, b, width, bottom - b),
        WalletRect(left, t, l - left, b - t), WalletRect(r, t, right - r, b - t))
        .filter { it.width > 0f && it.height > 0f }
}

/** Horizontal tray faces overlap by a fixed pitch, with coins following the final bill edge. */
fun trayBillBounds(denominations: List<Denomination>, area: WalletRect): List<WalletRect> {
    val height = area.height * 0.62f
    return denominations.mapIndexed { index, d ->
        WalletRect(area.left + index * 24f, area.top + (area.height - height) / 2f,
            height * d.billAspectRatio, height)
    }
}
