package com.jim.realcash.domain.ledger

import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.Hand
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total

/** Wallet state derived from the ledger. Never stored as the source of truth. */
data class WalletState(
    /** Every unit ever seen, in any state (except units removed by [Adjusted]). */
    val units: Map<CashUnitId, CashUnit>,
    /** The currently active hand, if any. */
    val activeHand: Hand?,
    /** Detected payments waiting for confirmation or settlement. */
    val pendingPayments: Map<PaymentId, DetectedPayment>,
    /** Settlements in the order they were applied. */
    val settlements: List<Settlement>,
    /**
     * Identifiers of every applied event.
     *
     * NOTE: not in the original spec; added so that applying the same event twice is always rejected,
     * even for events whose effect would otherwise be a no-op.
     */
    val appliedEventIds: Set<String> = emptySet(),
) {
    /** Units currently in the wallet. */
    val inWallet: List<CashUnit> get() = units.values.filter { it.state == CashUnitState.IN_WALLET }

    /** Units currently held in the active hand. */
    val inHand: List<CashUnit> get() = units.values.filter { it.state == CashUnitState.IN_HAND }

    /** Money the user still owns in cash: wallet plus hand. Never negative. */
    val balance: Won get() = inWallet.total() + inHand.total()

    /** Number of units per denomination in the given [state], largest denomination first, zeros omitted. */
    fun countsByDenomination(state: CashUnitState): Map<Denomination, Int> {
        val counts = units.values.filter { it.state == state }.groupingBy { it.denomination }.eachCount()
        return Denomination.descending.filter { it in counts }.associateWith { counts.getValue(it) }
    }

    /**
     * Returns the list of violated invariants (empty when the state is consistent).
     *
     * The reducer keeps these invariants by construction; this full check is O(n) and meant for tests
     * and debug assertions rather than for every event.
     */
    fun invariantViolations(): List<String> {
        val problems = mutableListOf<String>()
        units.forEach { (key, unit) ->
            if (key != unit.id) problems += "unit stored under foreign key: $key -> ${unit.id}"
        }
        val handIds = activeHand?.unitIds.orEmpty()
        if (handIds.size != handIds.toSet().size) problems += "active hand lists a unit twice"
        val inHandIds = inHand.map { it.id }.toSet()
        if (inHandIds != handIds.toSet()) {
            problems += "IN_HAND units $inHandIds differ from active hand units ${handIds.toSet()}"
        }
        if (activeHand != null && handIds.isEmpty()) problems += "active hand holds no units"
        if (balance.value < 0) problems += "negative balance $balance"
        settlements.forEach { s ->
            if (s.shortfall.value < 0) problems += "negative shortfall in ${s.paymentId}"
            s.paidUnitIds.forEach { id ->
                if (units[id]?.state != CashUnitState.SPENT) problems += "paid unit $id is not SPENT"
            }
        }
        if (settlements.map { it.paymentId }.toSet().size != settlements.size) problems += "payment settled twice"
        settlements.forEach { s ->
            if (s.paymentId in pendingPayments) problems += "settled payment ${s.paymentId} still pending"
        }
        return problems
    }

    /** Factory for the initial state. */
    companion object {
        /** A wallet before any event has been applied. */
        val EMPTY: WalletState = WalletState(
            units = emptyMap(),
            activeHand = null,
            pendingPayments = emptyMap(),
            settlements = emptyList(),
        )
    }
}
