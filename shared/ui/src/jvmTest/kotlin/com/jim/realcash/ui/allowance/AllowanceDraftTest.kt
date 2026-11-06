package com.jim.realcash.ui.allowance

import com.jim.realcash.domain.calc.SplitStyle
import com.jim.realcash.domain.model.Denomination.C100
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.Won
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AllowanceDraftTest {
    @Test
    fun emptyDraftIsInvalid() {
        val d = AllowanceDraft()
        assertNull(d.amount)
        assertFalse(d.isValid)
        assertFalse(d.showSumError)
        assertEquals(Won.ZERO, d.sum)
    }

    @Test
    fun typingAnAmountSuggestsLargestFirst() {
        val d = AllowanceDraft().withAmountText("100,000")
        assertEquals("100000", d.digits)
        assertEquals(Won(100_000), d.amount)
        assertEquals(mapOf(W50000 to 2), d.counts)
        assertTrue(d.isValid)
        assertFalse(d.edited)
        assertEquals(Won(100_000), d.sum)
    }

    @Test
    fun balancedStyleKeepsMoreTenThousands() {
        val d = AllowanceDraft().withAmountText("100000").withStyle(SplitStyle.BALANCED)
        assertEquals(mapOf(W50000 to 1, W10000 to 5), d.counts)
        assertTrue(d.isValid)
        assertEquals(SplitStyle.BALANCED, d.style)
    }

    @Test
    fun incrementUpdatesTheAmountAndKeepsTheSplitValid() {
        val d = AllowanceDraft().withAmountText("30,000").increment(W10000)
        assertEquals("40000", d.digits)
        assertEquals(Won(40_000), d.amount)
        assertEquals(Won(40_000), d.sum)
        assertTrue(d.edited)
        assertTrue(d.isValid)
        assertFalse(d.showSumError)
        assertEquals(mapOf(W10000 to 4), d.split())
    }

    @Test
    fun decrementUpdatesTheAmountUntilTheLastBillIsRemoved() {
        var d = AllowanceDraft().withAmountText("30000").decrement(W10000)
        assertEquals("20000", d.digits)
        assertEquals(Won(20_000), d.sum)
        assertEquals(2, d.countOf(W10000))
        assertTrue(d.edited)
        assertTrue(d.isValid)
        assertFalse(d.showSumError)

        repeat(2) { d = d.decrement(W10000) }
        assertEquals("0", d.digits)
        assertEquals(Won.ZERO, d.amount)
        assertEquals(Won.ZERO, d.sum)
        assertEquals(0, d.countOf(W10000))
        assertTrue(d.edited)
        assertFalse(d.isValid)
        assertFalse(d.showSumError)
        assertEquals(emptyMap(), d.split())
        assertEquals(d, d.decrement(W10000))
    }

    @Test
    fun incrementFromAnEmptyDraftSetsTheAmount() {
        val d = AllowanceDraft().increment(W10000)
        assertEquals("10000", d.digits)
        assertTrue(d.edited)
        assertTrue(d.isValid)
        assertFalse(d.showSumError)
    }

    @Test
    fun aNegativeCountIsInvalidEvenWhenTheTotalIsPositive() {
        val d = AllowanceDraft(digits = "9000", counts = mapOf(W10000 to 1, W1000 to -1))
        assertEquals(Won(9_000), d.sum)
        assertFalse(d.isValid)
        assertFalse(d.showSumError)
    }

    @Test
    fun decrementAtZeroIsANoOp() {
        val d = AllowanceDraft().withAmountText("1000")
        assertEquals(d, d.decrement(C100))
        assertEquals(0, d.countOf(C100))
    }

    @Test
    fun retypingTheAmountResetsEdits() {
        val d = AllowanceDraft().withAmountText("30000").increment(W10000).withAmountText("50000")
        assertEquals("50000", d.digits)
        assertEquals(Won(50_000), d.sum)
        assertFalse(d.edited)
        assertEquals(mapOf(W50000 to 1), d.counts)
        assertTrue(d.isValid)
        assertFalse(d.showSumError)
    }

    @Test
    fun pickingAStyleAfterEditsResetsTheCounts() {
        val d = AllowanceDraft().withAmountText("60000").increment(C100).withStyle(SplitStyle.LARGEST_FIRST)
        assertEquals("60100", d.digits)
        assertFalse(d.edited)
        assertEquals(mapOf(W50000 to 1, W10000 to 1, C100 to 1), d.counts)
        assertTrue(d.isValid)
    }

    @Test
    fun nonCashAmountsAreFlaggedAndGetNoSuggestion() {
        val d = AllowanceDraft().withAmountText("6505")
        assertTrue(d.amountNotCash)
        assertEquals(emptyMap(), d.counts)
        assertFalse(d.isValid)
        assertTrue(d.showSumError)
    }

    @Test
    fun zeroAmountIsInvalid() {
        val d = AllowanceDraft().withAmountText("0")
        assertEquals("", d.digits)
        assertFalse(d.isValid)
        assertFalse(d.showSumError)
    }

    @Test
    fun incrementReplacesAnUnrepresentableTypedAmountWithTheSplitTotal() {
        val d = AllowanceDraft().withAmountText("6505").increment(W10000)
        assertEquals("10000", d.digits)
        assertEquals(Won(10_000), d.sum)
        assertTrue(d.edited)
        assertTrue(d.isValid)
        assertFalse(d.amountNotCash)
        assertFalse(d.showSumError)
    }

    @Test
    fun splitDropsZeroCountsAndOrdersLargestFirst() {
        val d = AllowanceDraft().withAmountText("1000").increment(W50000).decrement(W1000).increment(C100).decrement(C100)
        assertEquals(listOf(W50000), d.split().keys.toList())
        assertEquals(mapOf(W50000 to 1), d.split())
    }
}
