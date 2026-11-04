package com.jim.realcash.ui.exchange

import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.Denomination.C10
import com.jim.realcash.domain.model.Denomination.C100
import com.jim.realcash.domain.model.Denomination.C50
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.Won
import com.jim.realcash.ui.takeout.unitsOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ExchangeDraftTest {
    private val wallet = unitsOf(W50000 to 1, W10000 to 5, W1000 to 2, C10 to 3)

    @Test
    fun trayTakesOldestUnitsAndCanBeEdited() {
        var d = ExchangeDraft.of(wallet.reversed())
        d = d.addToTray(W10000).addToTray(W10000)
        assertEquals(listOf(CashUnitId("u-2"), CashUnitId("u-3")), d.trayIds)
        assertEquals(Won(20_000), d.trayTotal)
        assertEquals(3, d.remaining(W10000))
        d = d.removeFromTray(W10000)
        assertEquals(listOf(CashUnitId("u-2")), d.trayIds)
        assertSame(d, d.removeFromTray(W1000))
        assertSame(d, d.addToTray(C500), "not in the wallet")
    }

    @Test
    fun receivePrefillsMinimalChange() {
        val d = ExchangeDraft.of(wallet).addToTray(W10000).addToTray(W10000).addToTray(W10000).addToTray(W10000).addToTray(W10000)
            .toReceive()
        assertEquals(ExchangeDraft.Step.RECEIVE, d.step)
        assertEquals(ExchangeSuggestion.MINIMAL, d.suggestion)
        assertEquals(mapOf(W50000 to 1), d.toSplit, "10,000×5 -> 50,000×1")
        assertTrue(d.isValid)
        // Tray is frozen in step 2.
        assertSame(d, d.addToTray(W1000))
    }

    @Test
    fun singleLargeBillOpensOnTheSmallerPreset() {
        val d = ExchangeDraft.of(wallet).addToTray(W50000).toReceive()
        assertEquals(ExchangeSuggestion.SMALLER, d.suggestion, "minimal change would be the same bill")
        assertEquals(mapOf(W10000 to 5), d.toSplit)
        assertTrue(d.isValid)
        val same = d.withSuggestion(ExchangeSuggestion.MINIMAL)
        assertEquals(mapOf(W50000 to 1), same.toSplit)
        assertTrue(same.isSameAsGiven)
        assertTrue(same.sumMatches)
        assertFalse(same.isValid, "a no-op exchange is not allowed")
    }

    @Test
    fun steppersEditAndValidateTheSum() {
        var d = ExchangeDraft.of(wallet).addToTray(W50000).toReceive()
        d = d.decrement(W10000)
        assertEquals(ExchangeSuggestion.CUSTOM, d.suggestion)
        assertFalse(d.sumMatches)
        assertEquals(Won(40_000), d.toTotal)
        assertFalse(d.isValid)
        d = d.increment(W5000).increment(W5000)
        assertTrue(d.isValid)
        assertEquals(mapOf(W10000 to 4, W5000 to 2), d.toSplit)
        assertSame(d, d.decrement(C500), "never below zero")
    }

    @Test
    fun smallerSplitRule() {
        assertEquals(mapOf(W10000 to 5), ExchangeDraft.smallerSplit(mapOf(W50000 to 1)))
        assertEquals(mapOf(W5000 to 4), ExchangeDraft.smallerSplit(mapOf(W10000 to 2)))
        assertEquals(mapOf(W1000 to 5), ExchangeDraft.smallerSplit(mapOf(W5000 to 1)))
        assertEquals(mapOf(C500 to 2), ExchangeDraft.smallerSplit(mapOf(W1000 to 1)))
        assertEquals(mapOf(C100 to 5), ExchangeDraft.smallerSplit(mapOf(C500 to 1)))
        assertEquals(mapOf(C10 to 5), ExchangeDraft.smallerSplit(mapOf(C50 to 1)))
        // 10원 coins cannot be broken: kept as they are, and alone there is no preset.
        assertEquals(mapOf(W5000 to 2, C10 to 3), ExchangeDraft.smallerSplit(mapOf(W10000 to 1, C10 to 3)))
        assertNull(ExchangeDraft.smallerSplit(mapOf(C10 to 3)))
        assertNull(ExchangeDraft.smallerSplit(emptyMap()))
        // The result always sums to the input.
        val given = mapOf(W50000 to 2, W10000 to 3, W1000 to 1, C500 to 1)
        val split = checkNotNull(ExchangeDraft.smallerSplit(given))
        assertEquals(given.entries.sumOf { (d, n) -> d.won * n }, split.entries.sumOf { (d, n) -> d.won * n })
    }

    @Test
    fun backToGiveKeepsTrayAndRebaseDropsVanishedUnits() {
        val d = ExchangeDraft.of(wallet).addToTray(W50000).addToTray(W1000).toReceive()
        val back = d.backToGive()
        assertEquals(ExchangeDraft.Step.GIVE, back.step)
        assertEquals(d.tray, back.tray)
        // The 1,000 left the wallet (settled elsewhere): dropped from the tray, back to step 1.
        val rebased = d.rebase(wallet.filterNot { it.id == CashUnitId("u-7") })
        assertEquals(listOf(CashUnitId("u-1")), rebased.tray)
        assertEquals(ExchangeDraft.Step.GIVE, rebased.step)
        assertSame(d, d.rebase(wallet.shuffled()))
    }

    @Test
    fun emptyTrayCannotGoToTheCounter() {
        val d = ExchangeDraft.of(wallet)
        assertSame(d, d.toReceive())
        assertFalse(d.isValid)
    }
}
