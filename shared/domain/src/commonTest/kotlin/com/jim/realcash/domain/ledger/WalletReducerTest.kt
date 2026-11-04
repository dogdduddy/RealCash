package com.jim.realcash.domain.ledger

import com.jim.realcash.domain.calc.Settler
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.CashUnitState.EXCHANGED_AWAY
import com.jim.realcash.domain.model.CashUnitState.IN_HAND
import com.jim.realcash.domain.model.CashUnitState.IN_WALLET
import com.jim.realcash.domain.model.CashUnitState.SPENT
import com.jim.realcash.domain.model.Denomination.C100
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.HandId
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.testing.Fixture
import com.jim.realcash.domain.testing.T0
import com.jim.realcash.domain.testing.assertConsistent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WalletReducerTest {
    private val fx = Fixture()

    @Test
    fun fullScenarioKeepsInvariants() {
        val events = mutableListOf<LedgerEvent>()
        var state = WalletState.EMPTY
        fun apply(e: LedgerEvent) {
            events += e
            state = WalletReducer.reduce(state, e)
            assertConsistent(state)
        }

        // 1. Allowance: 50,000×1 + 10,000×3 + 5,000×1 + 1,000×5 = 90,000원.
        val allowance = fx.units(W50000 to 1, W10000 to 3, W5000 to 1, W1000 to 5)
        apply(fx.allowance(allowance))
        assertEquals(Won(90_000), state.balance)

        // 2. Take one 10,000원 bill into the hand.
        val tenK = allowance.first { it.denomination == W10000 }
        val hand = fx.hand(listOf(tenK))
        apply(fx.moved(hand))
        assertEquals(listOf(tenK.id), state.inHand.map { it.id })
        assertEquals(Won(90_000), state.balance, "holding cash does not change the balance")

        // 3. Pay 6,500원; 4. settle with change.
        val coffee = fx.payment(6_500)
        apply(fx.detected(coffee))
        val settlement = Settler.settle(state, coffee, PullPolicy.LARGEST_FIRST, T0, fx.ids)
        apply(Settled(fx.eventId(), T0, settlement))
        assertEquals(Won(83_500), state.balance)
        assertNull(state.activeHand)
        assertEquals(SPENT, state.units.getValue(tenK.id).state)
        assertEquals(mapOf(W1000 to 3, C500 to 1), settlement.changeUnits.groupingBy { it.denomination }.eachCount())

        // 5. Exchange the 50,000원 bill for five 10,000원 bills.
        val fiftyK = allowance.first { it.denomination == W50000 }
        apply(Exchanged(fx.eventId(), T0, listOf(fiftyK.id), fx.units(W10000 to 5, source = CashUnitSource.EXCHANGE)))
        assertEquals(EXCHANGED_AWAY, state.units.getValue(fiftyK.id).state)
        assertEquals(Won(83_500), state.balance)

        // 6. Refund of the coffee comes back as change-rule units.
        val refund = fx.payment(6_500, isRefund = true)
        apply(fx.detected(refund))
        apply(Refunded(fx.eventId(), T0, refund.id, Settler.refundUnits(refund, T0, fx.ids)))
        assertEquals(Won(90_000), state.balance)
        assertTrue(state.pendingPayments.isEmpty())

        // 7. A second hand expires unused.
        val hand2 = fx.hand(state.inWallet.filter { it.denomination == W5000 }.take(1))
        apply(fx.moved(hand2))
        apply(HandReturned(fx.eventId(), T0, hand2.id, ReturnReason.EXPIRED))
        assertTrue(state.inHand.isEmpty())

        // 8. A false detection is rejected.
        val bogus = fx.payment(3_000)
        apply(fx.detected(bogus))
        apply(PaymentRejected(fx.eventId(), T0, bogus.id))

        // 9. Manual adjustment: a 500원 coin turned out to be five 100원 coins.
        val coin = state.inWallet.first { it.denomination == C500 }
        apply(Adjusted(fx.eventId(), T0, listOf(coin.id), fx.units(C100 to 5, source = CashUnitSource.ADJUSTMENT), "miscounted"))
        assertTrue(coin.id !in state.units)

        // Folding the whole log gives the same state.
        val folded = events.fold()
        assertEquals(state, folded)
        assertConsistent(folded)
        assertEquals(Won(90_000), folded.balance)
        assertEquals(folded.inWallet.sumOf { it.denomination.won }, folded.balance.value)
        assertEquals(mapOf(W10000 to 1), folded.countsByDenomination(SPENT))
        assertEquals(mapOf(W50000 to 1), folded.countsByDenomination(EXCHANGED_AWAY))
        assertEquals(
            mapOf(W10000 to 7, W5000 to 2, W1000 to 9, C500 to 1, C100 to 5),
            folded.countsByDenomination(IN_WALLET),
        )
        assertEquals(1, folded.settlements.size)
        assertEquals(events.map { it.id }.toSet(), folded.appliedEventIds)
    }

    @Test
    fun applyingTheSameEventTwiceFails() {
        val allowance = fx.allowance(fx.units(W10000 to 1))
        val state = WalletReducer.reduce(WalletState.EMPTY, allowance)
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(state, allowance) }
        // Same units under a new event id are rejected too.
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(state, allowance.copy(id = fx.eventId()))
        }
        // Even an otherwise valid event cannot be replayed.
        val payment = fx.payment(1_000)
        val detected = fx.detected(payment)
        val withPayment = WalletReducer.reduce(state, detected)
        val rejected = PaymentRejected(fx.eventId(), T0, payment.id)
        val afterReject = WalletReducer.reduce(withPayment, rejected)
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(afterReject, rejected) }
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(afterReject, detected) }
    }

    @Test
    fun secondHandWhileOneIsActiveFails() {
        val units = fx.units(W10000 to 2)
        val state = listOf(fx.allowance(units), fx.moved(fx.hand(units.take(1)))).fold()
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(state, fx.moved(fx.hand(units.drop(1)))) }
    }

    @Test
    fun handMayOnlyHoldWalletUnits() {
        val units = fx.units(W10000 to 2)
        val state = listOf(fx.allowance(units), fx.moved(fx.hand(units.take(1)))).fold()
        val returned = WalletReducer.reduce(state, HandReturned(fx.eventId(), T0, state.activeHand!!.id, ReturnReason.USER))
        assertEquals(IN_WALLET, returned.units.getValue(units[0].id).state)
        val unknown = fx.units(W1000 to 1)
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(returned, fx.moved(fx.hand(unknown))) }
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(returned, fx.moved(fx.hand(listOf(units[0], units[0]))))
        }
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(returned, fx.moved(fx.hand(emptyList()))) }
    }

    @Test
    fun returningAHandThatIsNotActiveFails() {
        val units = fx.units(W10000 to 1)
        val state = listOf(fx.allowance(units), fx.moved(fx.hand(units))).fold()
        assertEquals(IN_HAND, state.units.getValue(units[0].id).state)
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(state, HandReturned(fx.eventId(), T0, HandId("other"), ReturnReason.USER))
        }
        val noHand = listOf(fx.allowance(fx.units(W1000 to 1))).fold()
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(noHand, HandReturned(fx.eventId(), T0, HandId("x"), ReturnReason.EXPIRED))
        }
    }

    @Test
    fun unbalancedExchangeFails() {
        val units = fx.units(W50000 to 1)
        val state = listOf(fx.allowance(units)).fold()
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(state, Exchanged(fx.eventId(), T0, units.map { it.id }, fx.units(W10000 to 4)))
        }
    }

    @Test
    fun heldUnitsCannotBeExchanged() {
        val units = fx.units(W10000 to 1)
        val state = listOf(fx.allowance(units), fx.moved(fx.hand(units))).fold()
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(state, Exchanged(fx.eventId(), T0, units.map { it.id }, fx.units(W5000 to 2)))
        }
    }

    @Test
    fun settlementMustReferToAPendingPayment() {
        val state = fx.state(fx.units(W10000 to 1))
        val payment = fx.payment(6_500)
        val settlement = Settler.settle(state, payment, PullPolicy.LARGEST_FIRST, T0, fx.ids)
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(state, Settled(fx.eventId(), T0, settlement)) }
    }

    @Test
    fun settlementMustPayTheWholeHand() {
        val hand = fx.units(W1000 to 2)
        val payment = fx.payment(1_000)
        val state = fx.state(emptyList(), hand, listOf(payment))
        val partial = Settler.settle(state, payment, PullPolicy.LARGEST_FIRST, T0, fx.ids)
            .copy(paidUnitIds = listOf(hand[0].id), changeUnits = emptyList())
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(state, Settled(fx.eventId(), T0, partial)) }
    }

    @Test
    fun settlementAmountsMustAddUp() {
        val payment = fx.payment(6_500)
        val state = fx.state(fx.units(W10000 to 1), payments = listOf(payment))
        val good = Settler.settle(state, payment, PullPolicy.LARGEST_FIRST, T0, fx.ids)
        val noChange = good.copy(changeUnits = emptyList())
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(state, Settled(fx.eventId(), T0, noChange)) }
        val bogusShortfall = good.copy(shortfall = Won(-10))
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(state, Settled(fx.eventId(), T0, bogusShortfall)) }
        val ok = WalletReducer.reduce(state, Settled(fx.eventId(), T0, good))
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(ok, Settled(fx.eventId(), T0, good)) }
    }

    @Test
    fun settlementWithoutHandLeavesActiveHandAlone() {
        val held = fx.units(W10000 to 1)
        val wallet = fx.units(W5000 to 1)
        val payment = fx.payment(5_000)
        val state = fx.state(wallet, held, listOf(payment))
        val settlement = Settlement(
            paymentId = payment.id,
            handId = null,
            paidUnitIds = wallet.map { it.id },
            changeUnits = emptyList(),
            shortfall = Won.ZERO,
            flags = setOf(SettlementFlag.MANUAL),
        )
        val after = WalletReducer.reduce(state, Settled(fx.eventId(), T0, settlement))
        assertConsistent(after)
        assertEquals(state.activeHand, after.activeHand)
        // Paying a held unit without naming the hand would break the hand invariant.
        val payment2 = fx.payment(10_000)
        val withPayment = WalletReducer.reduce(after, fx.detected(payment2))
        val sneaky = settlement.copy(paymentId = payment2.id, paidUnitIds = held.map { it.id })
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(withPayment, Settled(fx.eventId(), T0, sneaky)) }
    }

    @Test
    fun refundRules() {
        val payment = fx.payment(6_500)
        val start = fx.state(fx.units(W10000 to 1), payments = listOf(payment))
        val settlement = Settler.settle(start, payment, PullPolicy.LARGEST_FIRST, T0, fx.ids)
        val settled = WalletReducer.reduce(start, Settled(fx.eventId(), T0, settlement))

        // Direct refund of a settled payment, capped by what was spent.
        val partial = WalletReducer.reduce(settled, Refunded(fx.eventId(), T0, payment.id, fx.units(W5000 to 1)))
        assertConsistent(partial)
        assertEquals(Won(8_500), partial.balance)
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(settled, Refunded(fx.eventId(), T0, payment.id, fx.units(W10000 to 1)))
        }
        // Unknown payment.
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(settled, Refunded(fx.eventId(), T0, fx.payment(1_000).id, fx.units(W1000 to 1)))
        }
        // A pending refund must be refunded in full.
        val refund = fx.payment(6_500, isRefund = true)
        val pending = WalletReducer.reduce(settled, fx.detected(refund))
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(pending, Refunded(fx.eventId(), T0, refund.id, fx.units(W5000 to 1)))
        }
        // A pending refund cannot be settled.
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(
                pending,
                Settled(fx.eventId(), T0, settlement.copy(paymentId = refund.id, paidUnitIds = emptyList(), changeUnits = emptyList())),
            )
        }
    }

    @Test
    fun adjustmentRules() {
        val units = fx.units(W1000 to 2)
        val state = listOf(fx.allowance(units)).fold()
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(state, Adjusted(fx.eventId(), T0, emptyList(), emptyList(), "nothing"))
        }
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(state, Adjusted(fx.eventId(), T0, fx.units(W1000 to 1).map { it.id }, emptyList(), "unknown"))
        }
        val adjusted = WalletReducer.reduce(state, Adjusted(fx.eventId(), T0, listOf(units[0].id), emptyList(), "lost"))
        assertEquals(Won(1_000), adjusted.balance)
        assertConsistent(adjusted)
    }

    @Test
    fun newUnitsMustBeInWallet() {
        val spentUnit = fx.units(W1000 to 1).map { it.copy(state = SPENT) }
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(WalletState.EMPTY, fx.allowance(spentUnit)) }
    }

    @Test
    fun paymentRules() {
        val payment = fx.payment(1_000)
        val state = WalletReducer.reduce(WalletState.EMPTY, fx.detected(payment))
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(state, fx.detected(payment)) }
        assertFailsWith<InvalidLedgerEventException> {
            WalletReducer.reduce(state, PaymentRejected(fx.eventId(), T0, fx.payment(2_000).id))
        }
        assertFailsWith<InvalidLedgerEventException> { WalletReducer.reduce(WalletState.EMPTY, fx.detected(fx.payment(0))) }
    }

    @Test
    fun invariantCheckDetectsCorruptedState() {
        val units = fx.units(W1000 to 1)
        val state = listOf(fx.allowance(units)).fold()
        val corrupted = state.copy(units = state.units.mapValues { it.value.copy(state = IN_HAND) })
        assertTrue(corrupted.invariantViolations().isNotEmpty())
    }
}
