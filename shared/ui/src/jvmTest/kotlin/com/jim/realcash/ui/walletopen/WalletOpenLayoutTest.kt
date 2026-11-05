package com.jim.realcash.ui.walletopen

import com.jim.realcash.domain.model.Denomination.*
import kotlin.test.*

class WalletOpenLayoutTest {
    private val allowance = mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5)

    @Test fun expensiveDenominationsPaintUnderCheaperOnesAndBacksPaintFirst() {
        val groups = pocketBillGroups(allowance, WalletOpenLayout.calculate(390f, 844f).pocket)
        assertEquals(listOf(W50000, W10000, W5000, W1000), groups.map { it.denomination })
        assertEquals(listOf(listOf(0), listOf(2, 1, 0), listOf(1, 0), listOf(4, 3, 2, 1, 0)), groups.map { it.drawOrder() })
    }

    @Test fun pickingHidesOnlyFrontAndLeavesTheBacksInTheirOriginalSlots() {
        val groups = pocketBillGroups(allowance, WalletOpenLayout.calculate(390f, 844f).pocket)
        groups.forEach { group ->
            val before = group.drawOrder().associateWith { group.exposedBillBounds(it) }
            val held = group.drawOrder(true).associateWith { group.exposedBillBounds(it) }
            assertEquals(before - 0, held)
        }
        assertEquals(listOf(2, 1), groups[1].drawOrder(true))
        assertTrue(groups[0].drawOrder(true).isEmpty())
    }

    @Test fun fullPickupFaceExtendsInsideTheOpeningForEveryCount() {
        for (count in listOf(1, 2, 3, 30)) {
            val group = pocketBillGroups(mapOf(W10000 to count), WalletOpenLayout.calculate(390f, 844f).pocket).single()
            assertTrue(group.face.left < group.openingX)
            assertEquals(group.openingX + group.exposedWidth, group.face.right, 0.001f)
            assertEquals(group.billHeight, group.face.height)
        }
    }

    @Test fun swipeRegionsContainOnlyUnoccludedPaperToTheRightOfOpening() {
        val groups = pocketBillGroups(allowance, WalletOpenLayout.calculate(390f, 844f).pocket)
        val pieces = visiblePaperBounds(groups).flatMap { it.third }
        assertTrue(pieces.isNotEmpty())
        pieces.forEach { assertTrue(it.left >= groups.first().openingX) }
        pieces.forEachIndexed { i, a -> pieces.drop(i + 1).forEach { b ->
            assertFalse(a.left < b.right && a.right > b.left && a.top < b.bottom && a.bottom > b.top)
        } }
    }

    @Test fun billDimensionsFitBesideCoinsAndKeepPhysicalRatios() {
        val layout = WalletOpenLayout.calculate(390f, 844f)
        val groups = pocketBillGroups(allowance, layout.pocket)
        assertTrue(groups[0].billHeight <= layout.wallet.height * 0.8f)
        val deepest = pocketBillGroups(allowance.mapValues { 30 }, layout.pocket).last()
        assertEquals(layout.coins.left - 6f, deepest.right, 0.001f)
        groups.zip(listOf(154f, 148f, 142f, 136f)).forEach { (group, length) ->
            assertEquals(group.billWidth * length / 68f, group.billHeight, 0.001f)
            assertEquals(groups[0].billWidth, group.billWidth)
        }
        assertEquals(groups.last().billWidth, pocketBillGroups(mapOf(W1000 to 5), layout.pocket).single().billWidth)
    }

    @Test fun fourGroupsHaveTwentyFourDpHeadsAndPaddedHeadHitsAcrossScreenSizes() {
        for ((w, h) in listOf(320f to 568f, 390f to 844f, 844f to 390f)) {
            val layout = WalletOpenLayout.calculate(w, h)
            val groups = pocketBillGroups(allowance, layout.pocket)
            groups.zipWithNext().forEach { (a, b) -> assertEquals(24f, b.top - a.top, 0.001f) }
            assertEquals(layout.pocket.top + 3f * 14f + 30f, groups.last().top, 0.001f)
            groups.forEachIndexed { i, group ->
                assertTrue(group.right + 8f <= w)
                assertTrue(group.top >= layout.pocket.top && group.bottom <= layout.pocket.bottom)
                val hit = pocketBillHitBounds(groups, i)
                assertEquals(layout.pocket.left, hit.left)
                assertEquals(group.top - 8f, hit.top)
                assertEquals((groups.getOrNull(i + 1)?.top ?: group.bottom) + 8f, hit.bottom)
                if (i < groups.lastIndex) assertEquals(24f + 16f, hit.height, 0.001f)
            }
        }
    }

    @Test fun exposedFractionsIncreaseTowardsCheaperNotes() {
        val groups = pocketBillGroups(allowance, WalletOpenLayout.calculate(390f, 844f).pocket)
        groups.zip(listOf(0.25f, 0.35f, 0.45f, 0.55f)).forEach { (g, fraction) ->
            assertEquals(fraction, g.exposedWidth / g.billWidth, 0.0001f)
        }
        groups.zipWithNext().forEach { (a, b) -> assertTrue(a.exposedWidth < b.exposedWidth) }
    }

    @Test fun thirtyBillsCompressToTwelveDpAndNeverMoveOtherDenominations() {
        for ((w, h) in listOf(320f to 568f, 390f to 844f, 844f to 390f)) {
            val pocket = WalletOpenLayout.calculate(w, h).pocket
            for (count in listOf(1, 2, 5, 9, 30, 100)) {
                val group = pocketBillGroups(mapOf(W10000 to count), pocket).single()
                assertEquals(count, group.visibleCount)
                assertEquals(minOf(1.5f * (count - 1), 12f), group.fanStep * (count - 1), 0.001f)
                assertTrue(group.right <= pocket.right + 0.001f)
                assertTrue(group.bottom <= pocket.bottom + 0.001f)
                val all = pocketBillGroups(allowance + (W10000 to count), pocket)
                assertEquals(all[1].face, group.face)
            }
        }
        assertTrue(pocketBillGroups(emptyMap(), WalletRect(0f, 0f, 100f, 300f)).isEmpty())
    }

    @Test fun trayBillsUseSixtyTwoPercentHeightAndTwentyFourDpPitch() {
        val area = WalletRect(20f, 80f, 340f, 110f)
        val bounds = trayBillBounds(listOf(W50000, W10000, W1000), area)
        bounds.forEach { assertEquals(110f * 0.62f, it.height, 0.001f) }
        bounds.zipWithNext().forEach { (a, b) -> assertEquals(24f, b.left - a.left) }
        assertEquals(area.left, bounds.first().left)
        assertTrue(bounds.all { it.top >= area.top && it.bottom <= area.bottom })
    }

    @Test fun reviewedWalletAndCoinsFitAllThreeScreensWithoutOverlappingPaper() {
        for ((w, h) in listOf(320f to 568f, 390f to 844f, 412f to 915f, 844f to 390f)) {
            val layout = WalletOpenLayout.calculate(w, h)
            assertEquals(0.03f, (layout.pocket.top - layout.wallet.top) / layout.wallet.height, 0.0001f)
            assertEquals(0.975f, (layout.pocket.left - layout.wallet.left) / layout.wallet.width, 0.0001f)
            assertEquals(640f / 1230f, layout.wallet.width / layout.wallet.height, 0.0001f)
            assertTrue(layout.wallet.left >= 0 && layout.wallet.right <= w)
            assertTrue(layout.wallet.top >= 0 && layout.wallet.bottom <= h)
            assertTrue(layout.coins.left > layout.wallet.right && layout.coins.right <= w)
            val groups = pocketBillGroups(allowance.mapValues { 30 }, layout.pocket)
            assertEquals(layout.pocket.top, layout.coins.top)
            assertTrue(groups.all { it.right + 6f <= layout.coins.left + 0.001f })
            assertTrue(groups.all { it.bottom <= layout.pocket.bottom + 0.001f })
            val coins = CoinScatter.place(7, 2, layout.coins)
            assertEquals(2, coins.size)
            coins.forEach { assertTrue(it.y - it.diameter / 2 >= layout.coins.top - 0.001f && it.y + it.diameter / 2 <= h) }
            if (w > h) {
                assertTrue(layout.tray.right < layout.wallet.left)
                assertEquals(layout.tray.top, layout.wallet.top)
            } else {
                assertEquals(12f, layout.wallet.left)
                assertTrue(layout.tray.bottom < layout.wallet.top)
            }
        }
        assertEquals(844f * 0.55f, WalletOpenLayout.calculate(390f, 844f).wallet.height, 0.001f)
    }
}
