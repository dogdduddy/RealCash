package com.jim.realcash.ui.walletopen

import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Denomination.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WalletOpenLayoutTest {
    private val allowance = mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5)

    @Test
    fun billDimensionsComeFromPocketDepthAndPhysicalRatios() {
        val pocket = WalletRect(30f, 400f, 260f, 250f)
        val groups = pocketBillGroups(allowance, pocket)
        val length = (250f - 250f * 0.06f) / 0.80f
        groups.zip(listOf(154f, 148f, 142f, 136f)).forEach { (group, realLength) ->
            assertEquals(length, group.billHeight, 0.001f)
            assertEquals(length * 68f / realLength, group.billWidth, 0.001f)
            assertEquals(pocket.bottom - pocket.height * 0.06f, group.top + group.billHeight, 0.001f)
            assertEquals(pocket.top, group.top + group.exposedHeight, 0.001f)
        }
        val alone = pocketBillGroups(mapOf(W1000 to 5), pocket).single()
        assertEquals(groups.last().billWidth, alone.billWidth)
        assertEquals(5, alone.visibleCount)
    }

    @Test
    fun overlappingGroupsAndAllFiveOneThousandWonBillsStayInsidePocket() {
        for ((width, height) in listOf(320f to 568f, 390f to 844f, 412f to 915f, 844f to 390f)) {
            val pocket = WalletOpenLayout.calculate(width, height).pocket
            val groups = pocketBillGroups(allowance, pocket)
            assertEquals(listOf(W50000, W10000, W5000, W1000), groups.map { it.denomination })
            val pitch = ((pocket.width - groups.maxOf { it.billWidth }) / 3f).coerceAtLeast(28f)
            groups.zipWithNext().forEach { (back, front) ->
                assertEquals(pitch, front.slotLeft - back.slotLeft, 0.001f)
                assertTrue(front.slotLeft - back.slotLeft >= 27.999f)
                assertTrue(front.left < back.right, "right-hand denomination overlaps the left")
                assertTrue(front.left >= back.frontLeft + 2.999f, "each front face keeps a visible strip")
            }
            assertEquals(5, groups.last().visibleCount)
            groups.forEachIndexed { index, group ->
                assertTrue(group.left >= pocket.left - 0.001f)
                assertTrue(group.right <= pocket.right + 0.001f)
                assertTrue(group.top + group.billHeight <= pocket.bottom)
                assertTrue(group.fanStep in 3f..8f)
                assertEquals(group.count, group.visibleCount)
                val hit = pocketBillHitBounds(groups, index)
                assertTrue(hit.width > 0f)
                assertTrue(hit.right <= (groups.getOrNull(index + 1)?.left ?: pocket.right) + 0.001f)
                assertEquals(pocket.top, hit.bottom, 0.001f)
            }
        }
    }

    @Test
    fun pitchHasTwentyEightDpFloor() {
        val groups = pocketBillGroups(allowance, WalletRect(0f, 200f, 200f, 200f))
        groups.zipWithNext().forEach { (a, b) -> assertEquals(28f, b.slotLeft - a.slotLeft, 0.001f) }
    }

    @Test
    fun thirtyBillsOnlyHideBackLayersAtThreeDpFloor() {
        val pocket = WalletOpenLayout.calculate(390f, 844f).pocket
        val groups = pocketBillGroups(mapOf(W50000 to 2, W10000 to 10, W5000 to 8, W1000 to 10), pocket)
        assertEquals(30, groups.sumOf { it.count })
        groups.forEach {
            assertTrue(it.visibleCount in 1..it.count)
            if (it.visibleCount < it.count) assertEquals(3f, it.fanStep)
            assertTrue(it.left >= pocket.left - 0.001f)
            assertTrue(it.right <= pocket.right + 0.001f)
        }
        val overflow = pocketBillGroups(Denomination.entries.associateWith { 1_000 }, pocket)
        assertEquals(4, overflow.size)
        assertTrue(overflow.all { it.fanStep == 3f && it.visibleCount < it.count })
        assertTrue(pocketBillGroups(emptyMap(), pocket).isEmpty())
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
