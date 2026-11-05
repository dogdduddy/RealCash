package com.jim.realcash.ui.walletopen

import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Denomination.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletOpenLayoutTest {
    private val allowance = mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5)

    @Test
    fun pocketBillsPaintRightToLeftWithTheLeftmostFaceInFront() {
        val groups = pocketBillGroups(allowance, WalletOpenLayout.calculate(390f, 844f).pocket)
        assertEquals(listOf(0), groups[0].drawOrder())
        assertEquals(listOf(2, 1, 0), groups[1].drawOrder())
        assertEquals(listOf(1, 0), groups[2].drawOrder())
        assertEquals(listOf(4, 3, 2, 1, 0), groups[3].drawOrder())
    }

    @Test
    fun pickingHidesOnlyTheLeftFrontAndLeavesTheBacksInTheirOriginalSlots() {
        val groups = pocketBillGroups(allowance, WalletOpenLayout.calculate(390f, 844f).pocket)
        groups.forEach { group ->
            val before = group.drawOrder().associateWith { group.left + it * group.fanStep }
            val held = group.drawOrder(frontPicked = true).associateWith { group.left + it * group.fanStep }
            assertEquals(before - 0, held)
        }
        assertEquals(listOf(2, 1), groups[1].drawOrder(frontPicked = true))
        assertTrue(groups[0].drawOrder(frontPicked = true).isEmpty())
    }

    @Test
    fun frontLeftIsTheLeftEdgeForEveryGroupSize() {
        for (count in listOf(1, 2, 3, 30)) {
            val group = pocketBillGroups(mapOf(W10000 to count), WalletOpenLayout.calculate(390f, 844f).pocket).single()
            assertEquals(group.left, group.frontLeft)
        }
    }

    @Test
    fun swayStripsFollowTheFullLeftFaceThenOnlyTheRightEdges() {
        val groups = pocketBillGroups(allowance, WalletOpenLayout.calculate(390f, 844f).pocket)
        groups.forEach { group ->
            val strips = (0 until group.visibleCount).map(group::exposedBillBounds)
            assertEquals(group.left, strips.first().left, 0.001f)
            assertEquals(group.exposedWidth, strips.first().width)
            strips.drop(1).forEach { assertEquals(group.fanStep, it.width) }
            strips.zipWithNext().forEach { (a, b) -> assertEquals(a.right, b.left, 0.001f) }
            assertEquals(group.right, strips.last().right, 0.001f)
        }
    }

    @Test
    fun billDimensionsComeFromPocketDepthAndPhysicalRatios() {
        val pocket = WalletRect(30f, 400f, 260f, 350f)
        val groups = pocketBillGroups(allowance, pocket)
        val width = pocket.height * 0.94f * 68f / 154f
        groups.zip(listOf(154f, 148f, 142f, 136f)).forEach { (group, realLength) ->
            assertEquals(width * realLength / 68f, group.billHeight, 0.001f)
            assertEquals(width, group.billWidth, 0.001f)
            assertEquals(pocket.top + pocket.height / 2f, group.top + group.billHeight / 2f, 0.001f)
            assertEquals(width * 0.35f * 0.5f, group.exposedWidth, 0.001f)
        }
        groups.zipWithNext().forEach { (a, b) -> assertTrue(a.billHeight > b.billHeight) }
        assertEquals(groups.last().billWidth, pocketBillGroups(mapOf(W1000 to 5), pocket).single().billWidth)
    }

    @Test
    fun fourGroupsHaveDescendingOrderAndRealGapsAcrossScreenSizes() {
        for ((width, height) in listOf(320f to 568f, 390f to 844f, 412f to 915f, 844f to 390f)) {
            val pocket = WalletOpenLayout.calculate(width, height).pocket
            val groups = pocketBillGroups(allowance, pocket)
            assertEquals(listOf(W50000, W10000, W5000, W1000), groups.map { it.denomination })
            groups.zipWithNext().forEach { (a, b) ->
                assertEquals(minOf(WalletOpening.GROUP_GAP, pocket.width / 16f), b.left - a.right, 0.001f)
            }
            groups.forEachIndexed { index, group ->
                assertTrue(group.left >= pocket.left - 0.001f && group.right <= pocket.right + 0.001f)
                assertTrue(group.top >= pocket.top && group.top + group.billHeight <= pocket.bottom)
                val hit = pocketBillHitBounds(groups, index)
                assertEquals(group.right, hit.right)
                assertEquals(group.top + group.exposedHeight, hit.bottom)
                assertTrue(hit.width > 0f && hit.width < group.billWidth)
            }
        }
    }

    @Test
    fun weightedFansGiveMoreRoomToGroupsWithMorePaper() {
        val groups = pocketBillGroups(mapOf(W50000 to 2, W10000 to 10, W5000 to 8, W1000 to 10),
            WalletOpenLayout.calculate(390f, 844f).pocket)
        assertTrue(groups[1].slotWidth > groups[0].slotWidth)
        assertTrue(groups[1].slotWidth > groups[2].slotWidth)
        assertEquals(groups[1].slotWidth, groups[3].slotWidth)
        assertEquals(groups[0].fanStep, groups[1].fanStep, 0.001f)
    }

    @Test
    fun thirtyBillsFitWithoutBadgesEvenOnSmallScreens() {
        for ((w, h) in listOf(320f to 568f, 390f to 844f, 844f to 390f)) {
            val pocket = WalletOpenLayout.calculate(w, h).pocket
            val groups = pocketBillGroups(mapOf(W50000 to 2, W10000 to 10, W5000 to 8, W1000 to 10), pocket)
            assertEquals(30, groups.sumOf { it.visibleCount })
            groups.forEach {
                assertEquals(it.count, it.visibleCount)
                assertTrue(it.fanStep in 0f..8f)
                assertTrue(it.right <= pocket.right + 0.001f)
            }
        }
        assertTrue(pocketBillGroups(emptyMap(), WalletRect(0f, 0f, 100f, 300f)).isEmpty())
    }

    @Test
    fun trayBillsUseSixtyTwoPercentHeightAndTwentyFourDpPitch() {
        val area = WalletRect(20f, 80f, 340f, 110f)
        val bounds = trayBillBounds(listOf(W50000, W10000, W1000), area)
        bounds.forEach { assertEquals(110f * 0.62f, it.height, 0.001f) }
        bounds.zipWithNext().forEach { (a, b) -> assertEquals(24f, b.left - a.left) }
        assertEquals(area.left, bounds.first().left)
        assertTrue(bounds.all { it.top >= area.top && it.bottom <= area.bottom })
    }

    @Test
    fun assetFractionsAndWholeWalletBoundsMatchReviewedImage() {
        for ((width, height) in listOf(320f to 568f, 390f to 844f, 412f to 915f, 844f to 390f)) {
            val layout = WalletOpenLayout.calculate(width, height)
            assertEquals(0.15f, (layout.pocket.top - layout.wallet.top) / layout.wallet.height, 0.0001f)
            assertEquals(0.17f, (layout.pocket.left - layout.wallet.left) / layout.wallet.width, 0.0001f)
            assertEquals(640f / 1518f, layout.wallet.width / layout.wallet.height)
            assertTrue(layout.wallet.left >= 0f && layout.wallet.right <= width)
            assertTrue(layout.wallet.top >= 0f && layout.wallet.bottom <= height)
            assertTrue(layout.coins.left > layout.wallet.right && layout.coins.right <= width)
            assertTrue(layout.coins.bottom < height)
            if (width > height) assertTrue(layout.tray.right < layout.wallet.left)
            else assertTrue(layout.tray.bottom < layout.wallet.top)
        }
        assertEquals(844f * 0.6f, WalletOpenLayout.calculate(390f, 844f).wallet.height, 0.001f)
    }
}
