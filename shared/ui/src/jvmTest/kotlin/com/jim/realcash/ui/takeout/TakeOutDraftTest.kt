package com.jim.realcash.ui.takeout

import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Denomination.C100
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.Won
import com.jim.realcash.ui.wallet.DenominationCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

private val T0 = Instant.parse("2026-11-04T09:00:00Z")

/** Units with ids `u-1`, `u-2`, ... acquired one minute apart in the given order (earlier = older). */
fun unitsOf(vararg counts: Pair<Denomination, Int>, prefix: String = "u"): List<CashUnit> {
    var i = 0
    return counts.flatMap { (d, n) ->
        List(n) {
            i++
            CashUnit(CashUnitId("$prefix-$i"), d, CashUnitState.IN_WALLET, T0 + i.minutes, CashUnitSource.ALLOWANCE)
        }
    }
}

class TakeOutDraftTest {
    private val wallet = unitsOf(W50000 to 1, W10000 to 2, W5000 to 1, W1000 to 3, C500 to 2, C100 to 3)

    @Test
    fun addPicksOldestUnitOfDenominationAndCountsDecrement() {
        // Newer 10,000 listed first in the input: the draft still picks the oldest one.
        val shuffled = wallet.reversed()
        var d = TakeOutDraft.of(shuffled)
        assertEquals(2, d.remaining(W10000))
        d = d.add(W10000)
        assertEquals(listOf(CashUnitId("u-2")), d.picked, "oldest 10,000 (u-2) first")
        assertEquals(1, d.remaining(W10000))
        d = d.add(W10000)
        assertEquals(0, d.remaining(W10000))
        assertSame(d, d.add(W10000), "nothing left: unchanged")
        assertEquals(Won(20_000), d.pickedTotal)
        assertEquals(listOf(DenominationCount(W10000, 2)), d.pickedCounts)
        // Emptied stack stays listed with 0 (shown dimmed).
        assertEquals(DenominationCount(W10000, 0), d.remainingBills.first { it.denomination == W10000 })
        assertEquals(listOf(W50000, W10000, W5000, W1000), d.remainingBills.map { it.denomination })
        assertEquals(listOf(C500, C100), d.remainingCoins.map { it.denomination })
    }

    @Test
    fun removePutsBackMostRecentPickOfDenomination() {
        val d = TakeOutDraft.of(wallet).add(W1000).add(W5000).add(W1000)
        val removed = d.remove(W1000)
        assertEquals(listOf(CashUnitId("u-5"), CashUnitId("u-4")), removed.picked, "the second 1,000 (u-6) went back")
        assertSame(removed, removed.remove(W50000), "nothing of that denomination picked")
        assertTrue(removed.clear().picked.isEmpty())
    }

    @Test
    fun feedbackShortExactOverWithChange() {
        var d = TakeOutDraft.of(wallet).withExpectedText("13,000")
        assertEquals(Won(13_000), d.expected)
        assertEquals(TakeOutFeedback.Short(Won(13_000)), d.feedback)
        d = d.add(W10000)
        assertEquals(TakeOutFeedback.Short(Won(3_000)), d.feedback)
        d = d.add(W5000)
        val over = assertIs<TakeOutFeedback.Over>(d.feedback)
        assertEquals(Won(2_000), over.over)
        assertEquals(mapOf(W1000 to 2), over.change)
        assertEquals(Won(2_000), over.changeTotal)
        d = d.remove(W5000).add(W1000).add(W1000).add(W1000)
        assertEquals(TakeOutFeedback.Exact, d.feedback)
        assertEquals(TakeOutFeedback.None, d.withExpectedText("").feedback)
        assertEquals(TakeOutFeedback.None, d.withExpectedText("0").feedback, "zero counts as no expectation")
    }

    @Test
    fun feedbackReportsWalletShortfallWhenEverythingIsNotEnough() {
        val small = unitsOf(W10000 to 1, W1000 to 2) // 12,000
        val d = TakeOutDraft.of(small).withExpectedText("17000").add(W10000)
        assertEquals(TakeOutFeedback.Short(Won(5_000), walletTotal = Won(12_000)), d.feedback)
    }

    @Test
    fun expectedIsRoundedUpToCashForComparisons() {
        val d = TakeOutDraft.of(wallet).withExpectedText("6505").add(W10000)
        assertEquals(Won(6_510), d.cashExpected)
        val over = assertIs<TakeOutFeedback.Over>(d.feedback)
        assertEquals(Won(3_490), over.over)
        assertEquals(mapOf(W1000 to 3, C100 to 4, Denomination.C50 to 1, Denomination.C10 to 4), over.change)
    }

    @Test
    fun fitExactUsesSmallChangeAndReportsExactness() {
        var d = TakeOutDraft.of(wallet).add(W50000).withExpectedText("3600")
        assertTrue(d.helpersEnabled)
        d = d.fitExact()
        assertEquals(TakeOutHelperResult.Exact(Won(3_600)), d.helper)
        assertEquals(Won(3_600), d.pickedTotal)
        assertEquals(TakeOutFeedback.Exact, d.feedback)
        assertEquals(listOf(DenominationCount(W1000, 3), DenominationCount(C500, 1), DenominationCount(C100, 1)), d.pickedCounts)
        // A manual change clears the helper note.
        assertEquals(null, d.add(W5000).helper)
    }

    @Test
    fun fitExactFallsBackWhenNoExactSubsetExists() {
        val d = TakeOutDraft.of(unitsOf(W10000 to 2, W5000 to 1)).withExpectedText("12000").fitExact()
        assertEquals(TakeOutHelperResult.NotExact(Won(15_000)), d.helper, "LARGEST_FIRST fallback: 10,000 + 5,000")
        assertIs<TakeOutFeedback.Over>(d.feedback)
    }

    @Test
    fun fewestUsesFewestUnitsAndReportsShortfall() {
        val d = TakeOutDraft.of(unitsOf(W10000 to 2, W5000 to 1, W1000 to 3)).withExpectedText("12000").fewest()
        assertEquals(TakeOutHelperResult.Fewest(2, Won(15_000)), d.helper, "two units; 10,000 + 5,000 overpays least")
        val poor = TakeOutDraft.of(unitsOf(W1000 to 2)).withExpectedText("5000").fewest()
        assertEquals(TakeOutHelperResult.Shortfall(Won(3_000)), poor.helper)
        assertEquals(Won(2_000), poor.pickedTotal)
    }

    @Test
    fun helpersNeedAnExpectedAmount() {
        val d = TakeOutDraft.of(wallet).add(W1000)
        assertFalse(d.helpersEnabled)
        assertSame(d, d.fitExact())
        assertSame(d, d.fewest())
    }

    @Test
    fun prefillAndRebase() {
        val hand = unitsOf(W10000 to 1, prefix = "h")
        val d = TakeOutDraft.of(wallet + hand, picked = listOf(CashUnitId("h-1"), CashUnitId("ghost")), expected = Won(9_000))
        assertEquals(listOf(CashUnitId("h-1")), d.picked, "unknown ids dropped")
        assertEquals("9000", d.expectedDigits)
        assertTrue(d.canConfirm)
        // The hand was settled elsewhere: its unit disappears and the pick with it.
        val rebased = d.rebase(wallet)
        assertTrue(rebased.picked.isEmpty())
        assertFalse(rebased.canConfirm)
        assertSame(rebased, rebased.rebase(wallet.shuffled()), "same units: unchanged")
    }

    @Test
    fun pickedIdsAreLargestFirst() {
        val d = TakeOutDraft.of(wallet).add(C100).add(W5000).add(W10000)
        assertEquals(listOf(W10000, W5000, C100), d.pickedUnits.map { it.denomination })
        assertEquals(d.pickedUnits.map { it.id }, d.pickedIds)
    }
}
