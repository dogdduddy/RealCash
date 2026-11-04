package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Denomination.C10
import com.jim.realcash.domain.model.Denomination.C100
import com.jim.realcash.domain.model.Denomination.C50
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.countsByDenomination
import com.jim.realcash.domain.model.total
import com.jim.realcash.domain.testing.Fixture
import com.jim.realcash.domain.testing.T0
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class PullSelectorTest {
    private val fx = Fixture()

    /** Wallet from the plan's example: 50000×1, 10000×2, 1000×3, 500×2. */
    private val docWallet = fx.units(W50000 to 1, W10000 to 2, W1000 to 3, C500 to 2)

    private fun List<CashUnit>.denominationsOf(result: PullSelector.PullResult): List<Denomination> {
        val byId = associateBy { it.id }
        return result.unitIds.map { byId.getValue(it).denomination }
    }

    @Test
    fun largestFirstPicksSmallestSingleCoveringUnit() {
        val result = PullSelector.select(docWallet, Won(6_500), PullPolicy.LARGEST_FIRST)
        assertEquals(listOf(W10000), docWallet.denominationsOf(result))
        assertEquals(Won(10_000), result.total)
        assertEquals(Won.ZERO, result.shortfall)
    }

    @Test
    fun exactIfPossibleFallsBackToLargestFirstWhenNoExactSubsetExists() {
        // 1000×3 + 500×2 is only 4,000 and no subset with the 10,000/50,000 bills hits 6,500 exactly.
        val result = PullSelector.select(docWallet, Won(6_500), PullPolicy.EXACT_IF_POSSIBLE)
        assertEquals(listOf(W10000), docWallet.denominationsOf(result))
        assertEquals(Won(10_000), result.total)
    }

    @Test
    fun fewestUnitsOnDocWalletIsOneTenThousandBill() {
        val result = PullSelector.select(docWallet, Won(6_500), PullPolicy.FEWEST_UNITS)
        assertEquals(listOf(W10000), docWallet.denominationsOf(result))
    }

    @Test
    fun sevenThousandWonBillsCoverSixThousandFiveHundred() {
        val wallet = fx.units(W1000 to 7)
        PullPolicy.entries.forEach { policy ->
            val result = PullSelector.select(wallet, Won(6_500), policy)
            assertEquals(7, result.unitIds.size, "$policy")
            assertEquals(Won(7_000), result.total, "$policy")
            assertEquals(Won.ZERO, result.shortfall, "$policy")
            assertEquals(mapOf(C500 to 1), ChangeCalculator.minimalChange(result.total - Won(6_500)))
        }
    }

    @Test
    fun shortfallWhenWalletCannotCover() {
        val wallet = fx.units(W1000 to 3)
        PullPolicy.entries.forEach { policy ->
            val result = PullSelector.select(wallet, Won(6_500), policy)
            assertEquals(wallet.map { it.id }.toSet(), result.unitIds.toSet(), "$policy")
            assertEquals(Won(3_000), result.total)
            assertEquals(Won(3_500), result.shortfall)
        }
        val empty = PullSelector.select(emptyList(), Won(1_000), PullPolicy.LARGEST_FIRST)
        assertEquals(PullSelector.PullResult(emptyList(), Won.ZERO, Won(1_000)), empty)
    }

    @Test
    fun zeroAmountSelectsNothing() {
        PullPolicy.entries.forEach { policy ->
            assertEquals(PullSelector.PullResult(emptyList(), Won.ZERO, Won.ZERO), PullSelector.select(docWallet, Won.ZERO, policy))
        }
    }

    @Test
    fun largestFirstAccumulatesFromTheTopWhenNoSingleUnitCovers() {
        val wallet = fx.units(W10000 to 1, W5000 to 1, W1000 to 5)
        val result = PullSelector.select(wallet, Won(12_000), PullPolicy.LARGEST_FIRST)
        assertEquals(listOf(W10000, W5000), wallet.denominationsOf(result))
    }

    /** Review cases for the "lazy cash payer" LARGEST_FIRST definition. */
    @Test
    fun largestFirstHandsOverLargestThenSmallestCoveringUnit() {
        data class Case(val wallet: List<CashUnit>, val amount: Int, val expected: List<Denomination>, val total: Int)
        val cases = listOf(
            Case(fx.units(W10000 to 2, W5000 to 1, W1000 to 2), 12_000, listOf(W10000, W5000), 15_000),
            Case(fx.units(W10000 to 2, W1000 to 2), 12_000, listOf(W10000, W10000), 20_000),
            Case(fx.units(W50000 to 1, W1000 to 3), 52_000, listOf(W50000, W1000, W1000), 52_000),
            Case(fx.units(W1000 to 7), 6_500, List(7) { W1000 }, 7_000),
            Case(fx.units(W50000 to 1, W10000 to 2, W1000 to 3, C500 to 2), 6_500, listOf(W10000), 10_000),
            Case(fx.units(W5000 to 1, W1000 to 4, C500 to 3), 6_200, listOf(W5000, W1000, C500), 6_500),
        )
        cases.forEach { c ->
            val result = PullSelector.select(c.wallet, Won(c.amount), PullPolicy.LARGEST_FIRST)
            assertEquals(c.expected, c.wallet.denominationsOf(result), "${c.amount} from ${c.wallet.countsByDenomination()}")
            assertEquals(Won(c.total), result.total)
            assertEquals(Won.ZERO, result.shortfall)
        }
    }

    @Test
    fun largestFirstTakesTheOldestUnitOfADenomination() {
        val newer = fx.units(W10000 to 1, at = T0 + 5.minutes)
        val older = fx.units(W10000 to 1, at = T0)
        val result = PullSelector.select(newer + older, Won(6_500), PullPolicy.LARGEST_FIRST)
        assertEquals(listOf(older.single().id), result.unitIds)
    }

    @Test
    fun fewestUnitsBreaksTiesBySmallestOverpayment() {
        // Two units are needed: 10,000 + 5,000 and 10,000 + 1,000 both qualify; the latter overpays less.
        val wallet = fx.units(W10000 to 1, W5000 to 2, W1000 to 5)
        val fewest = PullSelector.select(wallet, Won(11_000), PullPolicy.FEWEST_UNITS)
        assertEquals(listOf(W10000, W1000), wallet.denominationsOf(fewest))
        val tight = fx.units(W10000 to 1, W5000 to 1, W1000 to 1)
        assertEquals(listOf(W10000, W5000), tight.denominationsOf(PullSelector.select(tight, Won(14_000), PullPolicy.FEWEST_UNITS)))
    }

    @Test
    fun exactIfPossibleFindsExactSubset() {
        val wallet = fx.units(W10000 to 1, W5000 to 1, W1000 to 3, C500 to 2)
        val result = PullSelector.select(wallet, Won(6_500), PullPolicy.EXACT_IF_POSSIBLE)
        assertEquals(Won(6_500), result.total)
        assertEquals(listOf(W5000, W1000, C500), wallet.denominationsOf(result))
    }

    @Test
    fun exactIfPossiblePrefersSmallChange() {
        val wallet = fx.units(W10000 to 1, W1000 to 10)
        val result = PullSelector.select(wallet, Won(10_000), PullPolicy.EXACT_IF_POSSIBLE)
        assertEquals(List(10) { W1000 }, wallet.denominationsOf(result))
    }

    @Test
    fun exactIfPossibleWithAmountNotMultipleOfTenFallsBack() {
        val result = PullSelector.select(docWallet, Won(6_505), PullPolicy.EXACT_IF_POSSIBLE)
        assertEquals(listOf(W10000), docWallet.denominationsOf(result))
    }

    @Test
    fun exactIfPossibleAboveDpLimitUsesGreedy() {
        val wallet = fx.units(W50000 to 101, C10 to 1)
        val amount = Won(5_000_010)
        assertTrue(amount > PullSelector.EXACT_DP_LIMIT)
        val result = PullSelector.select(wallet, amount, PullPolicy.EXACT_IF_POSSIBLE)
        assertEquals(amount, result.total)
        assertEquals(101, result.unitIds.size)
    }

    @Test
    fun exactIfPossibleAtDpLimitIsFast() {
        // 4,999,900 in bills/large coins; the last 100원 can only come from 50 + 10×5.
        val wallet = fx.units(
            W50000 to 99, W10000 to 4, W5000 to 1, W1000 to 4, C500 to 1, C100 to 4, C50 to 1, C10 to 10,
        )
        val result = PullSelector.select(wallet, PullSelector.EXACT_DP_LIMIT, PullPolicy.EXACT_IF_POSSIBLE)
        assertEquals(PullSelector.EXACT_DP_LIMIT, result.total)
        assertEquals(Won.ZERO, result.shortfall)
    }

    @Test
    fun fewestUnitsHandlesLargeWallets() {
        val wallet = fx.units(W10000 to 40, W5000 to 40, W1000 to 200, C500 to 200, C10 to 300)
        val result = PullSelector.select(wallet, Won(123_450), PullPolicy.FEWEST_UNITS)
        // 12 units can reach at most 120,000, so 13 are needed; 12×10,000 + 5,000 is the cheapest such set.
        assertEquals(13, result.unitIds.size)
        assertEquals(Won(125_000), result.total)
    }

    /** Compares every policy against brute force over small random wallets (seeded, deterministic). */
    @Test
    fun policiesMatchBruteForceOnRandomWallets() {
        val random = Random(42)
        repeat(60) { sample ->
            val counts = Denomination.descending.associateWith { random.nextInt(0, 4) }
            val wallet = fx.units(*counts.map { (d, n) -> d to n }.toTypedArray())
            val walletTotal = wallet.total().value
            if (walletTotal == 0) return@repeat
            val amount = Won((random.nextInt(1, walletTotal / 10 + 2)) * 10)
            val brute = bruteForce(counts, amount.value)

            val results = PullPolicy.entries.associateWith { PullSelector.select(wallet, amount, it) }
            results.forEach { (policy, r) ->
                val ids = r.unitIds
                assertEquals(ids.size, ids.toSet().size, "#$sample $policy distinct")
                assertEquals(wallet.filter { it.id in ids.toSet() }.total(), r.total, "#$sample $policy total")
                if (amount.value > walletTotal) {
                    assertEquals(Won(amount.value - walletTotal), r.shortfall, "#$sample $policy shortfall")
                } else {
                    assertEquals(Won.ZERO, r.shortfall)
                    assertTrue(r.total >= amount, "#$sample $policy covers")
                }
            }
            if (amount.value <= walletTotal) {
                val fewest = results.getValue(PullPolicy.FEWEST_UNITS)
                assertEquals(brute.minCount, fewest.unitIds.size, "#$sample fewest count, amount $amount, $counts")
                assertEquals(brute.minExcess, fewest.total.value - amount.value, "#$sample fewest excess")
                val exact = results.getValue(PullPolicy.EXACT_IF_POSSIBLE)
                if (brute.exactExists) {
                    assertEquals(amount, exact.total, "#$sample exact, amount $amount, $counts")
                } else {
                    assertEquals(results.getValue(PullPolicy.LARGEST_FIRST), exact, "#$sample exact fallback")
                }
                val largest = results.getValue(PullPolicy.LARGEST_FIRST)
                assertEquals(lazyPayerOracle(counts, amount.value), wallet.denominationsOf(largest), "#$sample largest")
                val coveringSingles = wallet.filter { it.amount >= amount }
                if (coveringSingles.isNotEmpty()) {
                    assertEquals(1, largest.unitIds.size)
                    assertEquals(coveringSingles.minOf { it.denomination.won }, largest.total.value)
                }
            }
        }
    }

    /** Independent count-based restatement of LARGEST_FIRST, used as the oracle for random wallets. */
    private fun lazyPayerOracle(counts: Map<Denomination, Int>, amount: Int): List<Denomination> {
        val left = Denomination.descending.associateWith { counts[it] ?: 0 }.toMutableMap()
        val result = mutableListOf<Denomination>()
        var remaining = amount
        while (true) {
            val covering = Denomination.ascending.firstOrNull { left.getValue(it) > 0 && it.won >= remaining }
            if (covering != null) return result + covering
            val largest = Denomination.descending.first { left.getValue(it) > 0 }
            left[largest] = left.getValue(largest) - 1
            result += largest
            remaining -= largest.won
        }
    }

    private class Brute(val exactExists: Boolean, val minCount: Int, val minExcess: Int)

    private fun bruteForce(counts: Map<Denomination, Int>, amount: Int): Brute {
        val denoms = counts.keys.toList()
        var exact = false
        var bestCount = Int.MAX_VALUE
        var bestExcess = Int.MAX_VALUE
        fun go(i: Int, n: Int, sum: Int) {
            if (i == denoms.size) {
                if (sum == amount) exact = true
                if (sum >= amount) {
                    val excess = sum - amount
                    if (n < bestCount || (n == bestCount && excess < bestExcess)) {
                        bestCount = n
                        bestExcess = excess
                    }
                }
                return
            }
            for (k in 0..counts.getValue(denoms[i])) go(i + 1, n + k, sum + k * denoms[i].won)
        }
        go(0, 0, 0)
        return Brute(exact, bestCount, bestExcess)
    }
}
