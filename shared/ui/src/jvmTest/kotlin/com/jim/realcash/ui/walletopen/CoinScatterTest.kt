package com.jim.realcash.ui.walletopen

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
    fun overcrowdedAndTooSmallAreasReturnOnlySafePlacements() {
        assertTrue(CoinScatter.place(1, 10, WalletRect(0f, 0f, 20f, 20f)).isEmpty())
        val coins = CoinScatter.place(1, 100, WalletRect(0f, 0f, 40f, 40f))
        assertEquals(1, coins.size)
        assertTrue(CoinScatter.place(1, 0, WalletRect(0f, 0f, 100f, 100f)).isEmpty())
    }
}
