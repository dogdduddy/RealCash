package com.jim.realcash.ui.walletopen

import com.jim.realcash.domain.model.Denomination
import kotlin.math.hypot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CoinScatterTest {
    @Test
    fun deterministicSeedAndFreshEntryRearrangesCoins() {
        val area = WalletRect(0f, 0f, 100f, 350f)
        assertEquals(CoinScatter.place(42, 14, area), CoinScatter.place(42, 14, area))
        assertNotEquals(CoinScatter.place(42, 14, area), CoinScatter.place(43, 14, area))
    }

    @Test
    fun variedDiametersNeverOverlapOrLeaveAreaAcrossSeeds() {
        val area = WalletOpenLayout.calculate(390f, 844f).coins
        val diameters = listOf(34f, 34f, 30.8f, 30.8f, 27.7f, 23.1f, 23.1f)
        repeat(100) { seed ->
            val coins = CoinScatter.place(seed, diameters, area)
            assertEquals(diameters.size, coins.size)
            coins.forEachIndexed { index, coin ->
                val radius = coin.diameter / 2f
                assertTrue(coin.x - radius >= area.left && coin.x + radius <= area.right)
                assertTrue(coin.y - radius >= area.top && coin.y + radius <= area.bottom)
                assertTrue(coin.rotation in 0f..360f)
                coins.take(index).forEach { other ->
                    assertTrue(hypot(coin.x - other.x, coin.y - other.y) >= (coin.diameter + other.diameter) / 2f)
                }
            }
        }
    }

    @Test
    fun compactStripFitsFourCoinsAcrossSeeds() {
        for ((w, h) in listOf(320f to 568f, 844f to 390f)) {
            val area = WalletOpenLayout.calculate(w, h).coins
            repeat(100) { seed ->
                val coins = CoinScatter.place(seed, listOf(34f, 34f, 28f, 24f), area)
                assertEquals(4, coins.size)
                coins.forEachIndexed { i, coin ->
                    assertTrue(coin.x - coin.diameter / 2f >= area.left)
                    assertTrue(coin.x + coin.diameter / 2f <= area.right)
                    assertTrue(coin.y - coin.diameter / 2f >= area.top - 0.001f)
                    assertTrue(coin.y + coin.diameter / 2f <= area.bottom)
                    coins.take(i).forEach { other ->
                        assertTrue(hypot(coin.x - other.x, coin.y - other.y) >= (coin.diameter + other.diameter) / 2f)
                    }
                }
            }
        }
    }

    @Test
    fun rightColumnStartsAtBillHeadWithSixDpGapsAndThreeDpJitterAtAllSizes() {
        val diameters = listOf(34f, 34f, 30.8f, 30.8f, 30.8f, 23.1f, 23.1f)
        for ((w, h) in listOf(390f to 844f, 320f to 568f, 844f to 390f)) {
            val layout = WalletOpenLayout.calculate(w, h)
            val groups = pocketBillGroups(Denomination.descending
                .filter { it.kind == Denomination.Kind.BILL }
                .associateWith { 30 }, layout.pocket)
            repeat(100) { seed ->
                val coins = CoinScatter.place(seed, diameters, layout.coins)
                assertEquals(diameters.size, coins.size)
                assertEquals(groups.first().top, coins.first().y - coins.first().diameter / 2f, 0.001f)
                coins.zipWithNext().forEach { (a, b) ->
                    assertEquals(6f, b.y - b.diameter / 2f - a.y - a.diameter / 2f, 0.001f)
                }
                coins.forEach { coin ->
                    assertTrue(kotlin.math.abs(coin.x - (w - 12f - 17f)) <= 3f)
                    assertTrue(coin.x - coin.diameter / 2f >= layout.coins.left - 0.001f)
                    assertTrue(groups.all { it.right + 6f <= coin.x - coin.diameter / 2f + 0.001f })
                    assertTrue(coin.x + coin.diameter / 2f <= w)
                    assertTrue(coin.y + coin.diameter / 2f <= h)
                }
                assertTrue(coins.map { it.rotation }.distinct().size > 1)
            }
        }
    }

    @Test
    fun smallerCoinsUseTheLargestPresentCoinForTheColumnCenter() {
        val layout = WalletOpenLayout.calculate(390f, 844f)
        CoinScatter.place(7, listOf(28f, 24f), layout.coins).forEach {
            assertTrue(kotlin.math.abs(it.x - (390f - 12f - 14f)) <= 3f)
        }
    }

    @Test
    fun secondColumnRequiresFreeWidthAndNeverSpillsIntoAThird() {
        val narrow = CoinScatter.place(7, 10, WalletRect(300f, 100f, 40f, 74f))
        assertEquals(listOf(0, 1), narrow.map { it.index })
        val twoColumns = CoinScatter.place(7, 10, WalletRect(254f, 100f, 86f, 74f))
        assertEquals(listOf(0, 1, 2, 3), twoColumns.map { it.index })
        assertEquals(listOf(117f, 157f, 117f, 157f), twoColumns.map { it.y })
        assertTrue(twoColumns.take(2).all { it.x in 317f..323f })
        assertTrue(twoColumns.drop(2).all { it.x in 271f..277f })
        assertEquals(4, CoinScatter.place(7, 10, WalletRect(0f, 100f, 340f, 74f)).size)
    }

    @Test
    fun crowdedScreenStripsOnlyUseASecondColumnWhenItClearsTheBills() {
        for ((w, h) in listOf(390f to 844f, 320f to 568f, 844f to 390f)) {
            val layout = WalletOpenLayout.calculate(w, h)
            val groups = pocketBillGroups(Denomination.descending
                .filter { it.kind == Denomination.Kind.BILL }.associateWith { 30 }, layout.pocket)
            val capacity = ((layout.coins.height + 6f) / 40f).toInt()
            val coins = CoinScatter.place(7, 100, layout.coins)
            assertEquals(capacity * if (w > h) 2 else 1, coins.size)
            coins.forEach { coin ->
                assertTrue(groups.all { it.right + 6f <= coin.x - coin.diameter / 2f + 0.001f })
                assertTrue(coin.x - coin.diameter / 2f >= layout.coins.left - 0.001f)
                assertTrue(coin.x + coin.diameter / 2f <= w)
                assertTrue(coin.y + coin.diameter / 2f <= layout.coins.bottom)
            }
        }
    }

    @Test
    fun overcrowdedAndTooSmallAreasReturnOnlySafePlacements() {
        assertTrue(CoinScatter.place(1, 10, WalletRect(0f, 0f, 20f, 20f)).isEmpty())
        val coins = CoinScatter.place(1, 100, WalletRect(0f, 0f, 40f, 40f))
        assertEquals(1, coins.size)
        assertTrue(CoinScatter.place(1, 0, WalletRect(0f, 0f, 100f, 100f)).isEmpty())
    }
}
