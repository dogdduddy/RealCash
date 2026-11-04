package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.Denomination.C10
import com.jim.realcash.domain.model.Denomination.C100
import com.jim.realcash.domain.model.Denomination.C50
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.domain.testing.T0
import com.jim.realcash.domain.util.SequentialIdGenerator
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ChangeCalculatorTest {
    @Test
    fun zeroNeedsNoUnits() {
        assertEquals(emptyMap(), ChangeCalculator.minimalChange(Won(0)))
    }

    @Test
    fun tenWonIsOneCoin() {
        assertEquals(mapOf(C10 to 1), ChangeCalculator.minimalChange(Won(10)))
    }

    @Test
    fun nineThousandNineHundredNinety() {
        assertEquals(
            mapOf(W5000 to 1, W1000 to 4, C500 to 1, C100 to 4, C50 to 1, C10 to 4),
            ChangeCalculator.minimalChange(Won(9_990)),
        )
    }

    @Test
    fun oneHundredTwentyThreeThousandFourHundredFifty() {
        assertEquals(
            mapOf(W50000 to 2, W10000 to 2, W1000 to 3, C100 to 4, C50 to 1),
            ChangeCalculator.minimalChange(Won(123_450)),
        )
    }

    @Test
    fun resultIsOrderedLargestFirst() {
        val keys = ChangeCalculator.minimalChange(Won(66_660)).keys.toList()
        assertEquals(keys.sortedByDescending { it.won }, keys)
    }

    @Test
    fun rejectsAmountsThatAreNotMultiplesOfTen() {
        assertFailsWith<IllegalArgumentException> { ChangeCalculator.minimalChange(Won(1_005)) }
        assertFailsWith<IllegalArgumentException> { ChangeCalculator.minimalChange(Won(1)) }
    }

    @Test
    fun rejectsNegativeAmounts() {
        assertFailsWith<IllegalArgumentException> { ChangeCalculator.minimalChange(Won(-10)) }
    }

    @Test
    fun randomSumsMatchInput() {
        val random = Random(20261104)
        repeat(200) {
            val amount = Won(random.nextInt(0, 1_000_000) * 10)
            val change = ChangeCalculator.minimalChange(amount)
            assertEquals(amount, change.total(), "sum for $amount")
            assertTrue(change.values.all { it > 0 }, "no zero counts for $amount")
            // Canonical system: every denomination except the largest appears fewer times than fits the next one up.
            change.forEach { (d, n) ->
                if (d != W50000) assertTrue(n < 5, "too many $d for $amount")
            }
        }
    }

    @Test
    fun cashUnitFactoryCreatesOneUnitPerCount() {
        val ids = SequentialIdGenerator("c-")
        val units = CashUnitFactory.create(mapOf(C500 to 1, W1000 to 3), T0, CashUnitSource.CHANGE, ids)
        assertEquals(listOf(W1000, W1000, W1000, C500), units.map { it.denomination })
        assertEquals(4, units.map { it.id }.toSet().size)
        assertEquals(Won(3_500), units.total())
        assertFailsWith<IllegalArgumentException> {
            CashUnitFactory.create(mapOf(C500 to -1), T0, CashUnitSource.CHANGE, ids)
        }
    }
}
