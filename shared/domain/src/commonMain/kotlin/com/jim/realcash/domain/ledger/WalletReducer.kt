package com.jim.realcash.domain.ledger

import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.CashUnitState.EXCHANGED_AWAY
import com.jim.realcash.domain.model.CashUnitState.IN_HAND
import com.jim.realcash.domain.model.CashUnitState.IN_WALLET
import com.jim.realcash.domain.model.CashUnitState.SPENT
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total

/** Thrown when an event cannot be applied because it would break a wallet invariant. */
class InvalidLedgerEventException(
    /** The rejected event. */
    val event: LedgerEvent,
    message: String,
) : IllegalStateException("${event::class.simpleName}(${event.id}): $message")

/**
 * Applies ledger events to a [WalletState].
 *
 * Application is order dependent and not idempotent: every event is validated against the current state
 * and rejected with [InvalidLedgerEventException] if it would break an invariant (unknown or duplicated
 * units, a second active hand, unbalanced exchange, the same event applied twice, ...).
 */
object WalletReducer {

    /** Returns the state after applying [event] to [state]; throws [InvalidLedgerEventException] on violation. */
    fun reduce(state: WalletState, event: LedgerEvent): WalletState {
        val ctx = Ctx(event)
        ctx.check(event.id !in state.appliedEventIds) { "event already applied" }
        val next = when (event) {
            is AllowanceReceived -> ctx.allowance(state, event)
            is MovedToHand -> ctx.movedToHand(state, event)
            is HandReturned -> ctx.handReturned(state, event)
            is PaymentDetected -> ctx.paymentDetected(state, event)
            is PaymentRejected -> ctx.paymentRejected(state, event)
            is Settled -> ctx.settled(state, event)
            is Refunded -> ctx.refunded(state, event)
            is Exchanged -> ctx.exchanged(state, event)
            is Adjusted -> ctx.adjusted(state, event)
        }
        return next.copy(appliedEventIds = state.appliedEventIds + event.id)
    }

    /** Validation helpers bound to the event being applied, so every failure names it. */
    private class Ctx(val event: LedgerEvent) {

        inline fun check(condition: Boolean, message: () -> String) {
            if (!condition) throw InvalidLedgerEventException(event, message())
        }

        fun fail(message: String): Nothing = throw InvalidLedgerEventException(event, message)

        fun allowance(state: WalletState, e: AllowanceReceived): WalletState {
            check(e.units.isNotEmpty()) { "allowance without units" }
            return state.copy(units = addNewUnits(state, e.units))
        }

        fun movedToHand(state: WalletState, e: MovedToHand): WalletState {
            val active = state.activeHand
            check(active == null) { "hand ${active?.id} is still active" }
            val ids = e.hand.unitIds
            check(ids.isNotEmpty()) { "hand ${e.hand.id} holds no units" }
            checkDistinct(ids)
            val units = state.units.toMutableMap()
            ids.forEach { id -> units[id] = requireUnit(state, id, IN_WALLET).copy(state = IN_HAND) }
            return state.copy(units = units, activeHand = e.hand)
        }

        fun handReturned(state: WalletState, e: HandReturned): WalletState {
            val hand = state.activeHand ?: fail("no active hand to return")
            check(hand.id == e.handId) { "hand ${e.handId} is not the active hand ${hand.id}" }
            val units = state.units.toMutableMap()
            hand.unitIds.forEach { id -> units[id] = requireUnit(state, id, IN_HAND).copy(state = IN_WALLET) }
            return state.copy(units = units, activeHand = null)
        }

        fun paymentDetected(state: WalletState, e: PaymentDetected): WalletState {
            val id = e.payment.id
            check(id !in state.pendingPayments) { "payment $id is already pending" }
            check(state.settlements.none { it.paymentId == id }) { "payment $id is already settled" }
            check(e.payment.amount.value > 0) { "payment amount must be positive, was ${e.payment.amount}" }
            return state.copy(pendingPayments = state.pendingPayments + (id to e.payment))
        }

        fun paymentRejected(state: WalletState, e: PaymentRejected): WalletState {
            check(e.paymentId in state.pendingPayments) { "payment ${e.paymentId} is not pending" }
            return state.copy(pendingPayments = state.pendingPayments - e.paymentId)
        }

        fun settled(state: WalletState, e: Settled): WalletState {
            val s = e.settlement
            // NOTE: a settlement must refer to a pending (detected or manually entered) payment, so the
            // amounts can be cross-checked. Manual entries are recorded as PaymentDetected first.
            val payment = state.pendingPayments[s.paymentId] ?: fail("payment ${s.paymentId} is not pending")
            check(!payment.isRefund) { "refund ${s.paymentId} cannot be settled; use Refunded" }
            check(s.shortfall.value >= 0) { "negative shortfall ${s.shortfall}" }
            checkDistinct(s.paidUnitIds)

            val hand = state.activeHand
            val handUnitIds = if (s.handId != null) {
                check(hand != null && hand.id == s.handId) { "hand ${s.handId} is not the active hand" }
                val ids = hand!!.unitIds.toSet()
                check(s.paidUnitIds.containsAll(ids)) { "settlement must pay every unit of hand ${hand.id}" }
                ids
            } else {
                emptySet()
            }

            val units = state.units.toMutableMap()
            s.paidUnitIds.forEach { id ->
                val expected = if (id in handUnitIds) IN_HAND else IN_WALLET
                units[id] = requireUnit(state, id, expected).copy(state = SPENT)
            }
            s.changeUnits.forEach { check(it.state == IN_WALLET) { "change unit ${it.id} must be IN_WALLET" } }
            check(s.shortfall == Won.ZERO || s.changeUnits.isEmpty()) { "change returned despite a shortfall" }

            val paid = s.paidUnitIds.map { state.units.getValue(it) }.total()
            val change = s.changeUnits.total()
            val cashAmount = payment.amount.roundUpToCash()
            check(paid - change == cashAmount - s.shortfall) {
                "paid $paid - change $change != amount $cashAmount - shortfall ${s.shortfall}"
            }

            return state.copy(
                units = addNewUnits(state, s.changeUnits, base = units),
                activeHand = if (s.handId != null) null else hand,
                pendingPayments = state.pendingPayments - s.paymentId,
                settlements = state.settlements + s,
            )
        }

        fun refunded(state: WalletState, e: Refunded): WalletState {
            check(e.units.isNotEmpty()) { "refund without units" }
            val refundTotal = e.units.total()
            val pending = state.pendingPayments[e.paymentId]
            var pendingPayments = state.pendingPayments
            if (pending != null) {
                check(pending.isRefund) { "pending payment ${e.paymentId} is not a refund" }
                val expected = pending.amount.roundUpToCash()
                check(refundTotal == expected) { "refund units $refundTotal != refund amount $expected" }
                pendingPayments = pendingPayments - e.paymentId
            } else {
                // NOTE: refunding a settled payment directly is allowed (e.g. manual refund entry). The total
                // must not exceed the cash that actually left the wallet for it. Repeated partial refunds of the
                // same payment are not tracked across events.
                val settlement = state.settlements.firstOrNull { it.paymentId == e.paymentId }
                    ?: fail("payment ${e.paymentId} is neither a pending refund nor settled")
                val spent = settlement.paidUnitIds.map { state.units.getValue(it) }.total() -
                    settlement.changeUnits.total()
                check(refundTotal <= spent) { "refund $refundTotal exceeds settled cash $spent" }
            }
            return state.copy(units = addNewUnits(state, e.units), pendingPayments = pendingPayments)
        }

        fun exchanged(state: WalletState, e: Exchanged): WalletState {
            check(e.fromUnitIds.isNotEmpty()) { "exchange gives nothing away" }
            check(e.toUnits.isNotEmpty()) { "exchange receives nothing" }
            checkDistinct(e.fromUnitIds)
            val units = state.units.toMutableMap()
            // NOTE: only wallet units can be exchanged; exchanging held units would break the hand invariant.
            val from = e.fromUnitIds.map { id -> requireUnit(state, id, IN_WALLET) }
            check(from.total() == e.toUnits.total()) {
                "exchange is unbalanced: ${from.total()} -> ${e.toUnits.total()}"
            }
            from.forEach { units[it.id] = it.copy(state = EXCHANGED_AWAY) }
            return state.copy(units = addNewUnits(state, e.toUnits, base = units))
        }

        fun adjusted(state: WalletState, e: Adjusted): WalletState {
            check(e.removedUnitIds.isNotEmpty() || e.addedUnits.isNotEmpty()) { "adjustment changes nothing" }
            checkDistinct(e.removedUnitIds)
            val units = state.units.toMutableMap()
            // NOTE: the spec has no "removed" unit state. Removed units are dropped from the unit map so they
            // never count as spent or exchanged in statistics; the event itself keeps the history.
            e.removedUnitIds.forEach { id ->
                requireUnit(state, id, IN_WALLET)
                units.remove(id)
            }
            return state.copy(units = addNewUnits(state, e.addedUnits, base = units))
        }

        private fun requireUnit(state: WalletState, id: CashUnitId, expected: CashUnitState): CashUnit {
            val unit = state.units[id] ?: fail("unknown unit ${id.value}")
            check(unit.state == expected) { "unit ${id.value} is ${unit.state}, expected $expected" }
            return unit
        }

        private fun checkDistinct(ids: List<CashUnitId>) {
            check(ids.size == ids.toSet().size) { "unit listed twice" }
        }

        /** Adds brand-new IN_WALLET units, rejecting identifiers that already exist. */
        private fun addNewUnits(
            state: WalletState,
            newUnits: List<CashUnit>,
            base: Map<CashUnitId, CashUnit> = state.units,
        ): Map<CashUnitId, CashUnit> {
            checkDistinct(newUnits.map { it.id })
            val result = base.toMutableMap()
            newUnits.forEach { unit ->
                check(unit.id !in state.units) { "unit ${unit.id.value} already exists" }
                check(unit.state == IN_WALLET) { "new unit ${unit.id.value} must be IN_WALLET, was ${unit.state}" }
                result[unit.id] = unit
            }
            return result
        }
    }
}

/** Folds the events in order, starting from [initial] (an empty wallet by default). */
fun Iterable<LedgerEvent>.fold(initial: WalletState = WalletState.EMPTY): WalletState =
    fold(initial) { state, event -> WalletReducer.reduce(state, event) }
