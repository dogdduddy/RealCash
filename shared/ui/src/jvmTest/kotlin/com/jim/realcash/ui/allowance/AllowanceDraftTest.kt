package com.jim.realcash.ui.allowance

import com.jim.realcash.domain.calc.SplitStyle
import com.jim.realcash.domain.model.Denomination.C100
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W5000
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
    fun steppersEditFreelyAndValidateAgainstTheAmount() {
        var d = AllowanceDraft().withAmountText("100000")
        d = d.increment(W1000)
        assertTrue(d.edited)
        assertFalse(d.isValid)
        assertTrue(d.showSumError)
        assertEquals(Won(101_000), d.sum)

        d = d.decrement(W50000)
        assertEquals(Won(51_000), d.sum)
        assertFalse(d.isValid)

        // Rebuild exactly 100,000 by hand: 50000 + 10000x4 + 5000 + 1000x4 + 1000 already there = 100,000.
        repeat(4) { d = d.increment(W10000) }
        d = d.increment(W5000)
        repeat(4) { d = d.increment(W1000) }
        assertEquals(Won(100_000), d.sum)
        assertTrue(d.isValid)
        assertFalse(d.showSumError)
        assertEquals(mapOf(W50000 to 1, W10000 to 4, W5000 to 1, W1000 to 5), d.split())
    }

    @Test
    fun decrementAtZeroIsANoOp() {
        val d = AllowanceDraft().withAmountText("1000")
        assertEquals(d, d.decrement(C100))
        assertEquals(0, d.countOf(C100))
    }

    @Test
    fun retypingTheAmountResetsEdits() {
        val d = AllowanceDraft().withAmountText("1000").increment(W1000).withAmountText("5000")
        assertFalse(d.edited)
        assertEquals(mapOf(W5000 to 1), d.counts)
    }

    @Test
    fun pickingAStyleAfterEditsResetsTheCounts() {
        val d = AllowanceDraft().withAmountText("60000").increment(C100).withStyle(SplitStyle.LARGEST_FIRST)
        assertFalse(d.edited)
        assertEquals(mapOf(W50000 to 1, W10000 to 1), d.counts)
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
    }

    @Test
    fun splitDropsZeroCountsAndOrdersLargestFirst() {
        val d = AllowanceDraft().withAmountText("1000").increment(W50000).decrement(W1000).increment(C100).decrement(C100)
        assertEquals(listOf(W50000), d.split().keys.toList())
        assertEquals(mapOf(W50000 to 1), d.split())
    }
}
