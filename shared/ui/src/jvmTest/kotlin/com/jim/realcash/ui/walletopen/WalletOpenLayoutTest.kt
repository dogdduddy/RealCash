package com.jim.realcash.ui.walletopen

import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Denomination.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletOpenLayoutTest {
    @Test
    fun billsStayInPocketWithLargestOnLeftAndFourteenDpBetweenGroups() {
        for ((width, height) in listOf(320f to 568f, 390f to 844f, 412f to 915f, 844f to 390f)) {
            val layout = WalletOpenLayout.calculate(width, height)
            val groups = pocketBillGroups(mapOf(W1000 to 50, W5000 to 3, W10000 to 8, W50000 to 2), layout.pocket)
            assertEquals(listOf(W50000, W10000, W5000, W1000), groups.map { it.denomination })
            groups.zipWithNext().forEach { (a, b) -> assertTrue(b.left - a.right >= 13.999f) }
            groups.forEach {
                assertTrue(it.left >= layout.pocket.left)
                assertTrue(it.right <= layout.pocket.right + 0.001f)
                assertTrue(it.top + it.billHeight <= layout.pocket.bottom)
                assertEquals(layout.pocket.top, it.top + it.exposedHeight, 0.001f)
                assertTrue(it.fanStep in 3f..10f)
                assertTrue(it.visibleCount in 1..it.count)
            }
        }
    }

    @Test
    fun overflowHidesBackBillsInsteadOfReducingFanBelowThreeDp() {
        val pocket = WalletOpenLayout.calculate(390f, 844f).pocket
        val groups = pocketBillGroups(Denomination.entries.associateWith { 1_000 }, pocket)
        assertEquals(4, groups.size) // coins do not enter the bill pocket
        assertTrue(groups.all { it.fanStep == 3f && it.visibleCount < it.count })
        assertTrue(pocketBillGroups(emptyMap(), pocket).isEmpty())
    }

    @Test
    fun assetFractionsAndSceneBoundsMatchReviewedImages() {
        val layout = WalletOpenLayout.calculate(390f, 844f)
        assertEquals(0.405f, (layout.pocket.top - layout.wallet.top) / layout.wallet.height, 0.0001f)
        assertEquals(0.037f, (layout.pocket.left - layout.wallet.left) / layout.wallet.width, 0.0001f)
        assertEquals(0.90f, (layout.trayInner.right - layout.trayInner.left) / layout.tray.width, 0.0001f)
        assertEquals(0.77f, layout.trayInner.height / layout.tray.height, 0.0001f)
        assertEquals(640f / 1024f, layout.wallet.width / layout.wallet.height)
        assertTrue(layout.coins.left > layout.wallet.right)
        assertTrue(layout.coins.right <= 390f)
        assertTrue(layout.coins.bottom < 844f)
    }
}
