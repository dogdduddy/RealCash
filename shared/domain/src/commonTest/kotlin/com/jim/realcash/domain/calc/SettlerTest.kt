package com.jim.realcash.domain.calc

import com.jim.realcash.domain.ledger.Settled
import com.jim.realcash.domain.ledger.WalletReducer
import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.countsByDenomination
import com.jim.realcash.domain.model.total
import com.jim.realcash.domain.testing.Fixture
import com.jim.realcash.domain.testing.T0
import com.jim.realcash.domain.testing.assertConsistent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettlerTest {
    private val fx = Fixture()

    /** Settles [payment], applies the result, and checks the invariants that hold for every branch. */
    private fun settleAndApply(
        state: WalletState,
        payment: DetectedPayment,
        policy: PullPolicy = PullPolicy.LARGEST_FIRST,
    ): Pair<Settlement, WalletState> {
        val settlement = Settler.settle(state, payment, policy, T0, fx.ids)
        val after = WalletReducer.reduce(state, Settled(fx.eventId(), T0, settlement))
        assertConsistent(after)
        // Balance drops by exactly the covered part of the (cash-rounded) amount.
        val cashAmount = payment.amount.roundUpToCash()
        assertEquals(state.balance - (cashAmount - settlement.shortfall), after.balance)
        assertNull(after.activeHand, "hand is released after settlement")
        settlement.paidUnitIds.forEach { assertEquals(CashUnitState.SPENT, after.units.getValue(it).state) }
        settlement.changeUnits.forEach {
            assertEquals(CashUnitState.IN_WALLET, after.units.getValue(it.id).state)
            assertEquals(CashUnitSource.CHANGE, it.source)
        }
        assertTrue(payment.id !in after.pendingPayments)
        return settlement to after
    }

    private fun Settlement.changeCounts(): Map<Denomination, Int> = changeUnits.countsByDenomination()

    @Test
    fun handEqualsAmount() {
        val hand = fx.units(W10000 to 1)
        val payment = fx.payment(10_000)
        val state = fx.state(fx.units(W10000 to 1, W1000 to 5), hand, listOf(payment))
        val (s, _) = settleAndApply(state, payment)
        assertEquals(hand.map { it.id }, s.paidUnitIds)
        assertEquals(state.activeHand?.id, s.handId)
        assertTrue(s.changeUnits.isEmpty())
        assertEquals(Won.ZERO, s.shortfall)
        assertEquals(emptySet(), s.flags)
    }

    @Test
    fun handExceedsAmountGivesMinimalChange() {
        val hand = fx.units(W10000 to 1)
        val payment = fx.payment(6_500)
        val state = fx.state(fx.units(W1000 to 2), hand, listOf(payment))
        val (s, after) = settleAndApply(state, payment)
        assertEquals(hand.map { it.id }, s.paidUnitIds)
        assertEquals(mapOf(W1000 to 3, C500 to 1), s.changeCounts())
        assertEquals(emptySet(), s.flags)
        assertEquals(Won(5_500), after.balance)
    }

    @Test
    fun handBelowAmountPullsLargestFirstFromWallet() {
        val hand = fx.units(W1000 to 5)
        val wallet = fx.units(W50000 to 1, W10000 to 2)
        val payment = fx.payment(12_000)
        val state = fx.state(wallet, hand, listOf(payment))
        // Policy is ignored for the top-up: the spec fixes LARGEST_FIRST here.
        val (s, _) = settleAndApply(state, payment, PullPolicy.FEWEST_UNITS)
        val paid = s.paidUnitIds.map { state.units.getValue(it).denomination }
        assertEquals(List(5) { W1000 } + W10000, paid)
        assertEquals(mapOf(W1000 to 3), s.changeCounts())
        assertEquals(setOf(SettlementFlag.ADDED_FROM_WALLET), s.flags)
    }

    @Test
    fun noHandPullsWithPolicyAndFlagsNotHandPicked() {
        val payment = fx.payment(6_500)
        val state = fx.state(fx.units(W50000 to 1, W10000 to 2, W1000 to 3, C500 to 2), payments = listOf(payment))
        val (s, _) = settleAndApply(state, payment)
        assertNull(s.handId)
        assertEquals(listOf(W10000), s.paidUnitIds.map { state.units.getValue(it).denomination })
        assertEquals(mapOf(W1000 to 3, C500 to 1), s.changeCounts())
        assertEquals(setOf(SettlementFlag.NOT_HAND_PICKED), s.flags)
    }

    @Test
    fun noHandWithExactPolicyPaysExactly() {
        val payment = fx.payment(6_500)
        val state = fx.state(fx.units(W10000 to 1, W1000 to 6, C500 to 1), payments = listOf(payment))
        val (s, _) = settleAndApply(state, payment, PullPolicy.EXACT_IF_POSSIBLE)
        assertEquals(Won(6_500), s.paidUnitIds.map { state.units.getValue(it) }.total())
        assertTrue(s.changeUnits.isEmpty())
        assertEquals(setOf(SettlementFlag.NOT_HAND_PICKED), s.flags)
    }

    @Test
    fun insufficientFundsWithoutHandEmptiesWallet() {
        val payment = fx.payment(20_000)
        val state = fx.state(fx.units(W10000 to 1, W1000 to 2), payments = listOf(payment))
        val (s, after) = settleAndApply(state, payment)
        assertEquals(Won(8_000), s.shortfall)
        assertTrue(s.changeUnits.isEmpty())
        assertEquals(setOf(SettlementFlag.NOT_HAND_PICKED, SettlementFlag.INSUFFICIENT_FUNDS), s.flags)
        assertEquals(Won.ZERO, after.balance)
    }

    @Test
    fun insufficientFundsWithHand() {
        val payment = fx.payment(5_000)
        val state = fx.state(fx.units(W1000 to 1), fx.units(W1000 to 1), listOf(payment))
        val (s, after) = settleAndApply(state, payment)
        assertEquals(Won(3_000), s.shortfall)
        assertEquals(setOf(SettlementFlag.ADDED_FROM_WALLET, SettlementFlag.INSUFFICIENT_FUNDS), s.flags)
        assertEquals(Won.ZERO, after.balance)
    }

    @Test
    fun handBelowAmountWithEmptyWalletDoesNotClaimWalletTopUp() {
        val payment = fx.payment(5_000)
        val hand = fx.units(W1000 to 2)
        val state = fx.state(emptyList(), hand, listOf(payment))
        val (s, _) = settleAndApply(state, payment)
        assertEquals(setOf(SettlementFlag.INSUFFICIENT_FUNDS), s.flags)
        assertEquals(Won(3_000), s.shortfall)
    }

    @Test
    fun amountNotMultipleOfTenIsRoundedUpForCash() {
        val payment = fx.payment(6_505)
        val state = fx.state(fx.units(W1000 to 1), fx.units(W10000 to 1), listOf(payment))
        val (s, after) = settleAndApply(state, payment)
        assertEquals(Won(3_490), s.changeUnits.total())
        assertEquals(Won(1_000 + 3_490), after.balance)
    }

    @Test
    fun handLargerThanNeededStillPaysWholeHand() {
        val hand = fx.units(W50000 to 1, W5000 to 1)
        val payment = fx.payment(3_000)
        val state = fx.state(emptyList(), hand, listOf(payment))
        val (s, _) = settleAndApply(state, payment)
        assertEquals(hand.map { it.id }, s.paidUnitIds)
        assertEquals(Won(52_000), s.changeUnits.total())
    }

    @Test
    fun refundsAreRejected() {
        val refund = fx.payment(6_500, isRefund = true)
        val state = fx.state(fx.units(W10000 to 1))
        assertFailsWith<IllegalArgumentException> {
            Settler.settle(state, refund, PullPolicy.LARGEST_FIRST, T0, fx.ids)
        }
    }

    @Test
    fun refundUnitsUseTheChangeRule() {
        val refund = fx.payment(6_505, isRefund = true)
        val units = Settler.refundUnits(refund, T0, fx.ids)
        assertEquals(mapOf(W5000 to 1, W1000 to 1, C500 to 1, Denomination.C10 to 1), units.countsByDenomination())
        assertTrue(units.all { it.source == CashUnitSource.REFUND && it.state == CashUnitState.IN_WALLET })
        assertFailsWith<IllegalArgumentException> { Settler.refundUnits(fx.payment(1_000), T0, fx.ids) }
    }
}
