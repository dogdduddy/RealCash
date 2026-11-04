package com.jim.realcash.domain.calc

import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.domain.util.IdGenerator
import kotlin.time.Instant

/** Matches a detected payment against the active hand and the wallet and produces a [Settlement]. */
object Settler {

    /**
     * Builds the settlement for [payment] (matching rules of design doc 4.3):
     *
     * - hand == amount -> pay the hand.
     * - hand >  amount -> pay the hand, change = minimal change of the difference.
     * - hand <  amount -> pay the hand + [PullSelector] (LARGEST_FIRST) over the wallet for the difference,
     *   change, flag [SettlementFlag.ADDED_FROM_WALLET].
     * - no hand        -> [PullSelector] with [policy] over the wallet, change, flag [SettlementFlag.NOT_HAND_PICKED].
     * - not enough cash -> shortfall > 0, flag [SettlementFlag.INSUFFICIENT_FUNDS]; the wallet is emptied.
     *
     * Amounts that are not multiples of 10 won are rounded up to the next 10 won before matching
     * (see [Won.roundUpToCash]). Refunds are not handled here; use [refundUnits] and a `Refunded` event.
     * Throws [IllegalArgumentException] for refunds or non-positive amounts.
     */
    fun settle(
        state: WalletState,
        payment: DetectedPayment,
        policy: PullPolicy,
        now: Instant,
        ids: IdGenerator,
    ): Settlement {
        require(!payment.isRefund) { "refund ${payment.id} must be applied as a Refunded event" }
        require(payment.amount.value > 0) { "payment amount must be positive, was ${payment.amount}" }
        // NOTE: cash has no unit below 10원, so e.g. 6,505원 is settled as 6,510원.
        val amount = payment.amount.roundUpToCash()
        val hand = state.activeHand
        val flags = mutableSetOf<SettlementFlag>()

        val paidUnits: List<CashUnit>
        val shortfall: Won
        if (hand != null) {
            val handUnits = hand.unitIds.map { state.units.getValue(it) }
            val handTotal = handUnits.total()
            if (handTotal >= amount) {
                paidUnits = handUnits
                shortfall = Won.ZERO
            } else {
                val pull = PullSelector.select(state.inWallet, amount - handTotal, PullPolicy.LARGEST_FIRST)
                // NOTE: ADDED_FROM_WALLET is only set when at least one wallet unit was actually added.
                if (pull.unitIds.isNotEmpty()) flags += SettlementFlag.ADDED_FROM_WALLET
                paidUnits = handUnits + pull.unitIds.map { state.units.getValue(it) }
                shortfall = pull.shortfall
            }
        } else {
            flags += SettlementFlag.NOT_HAND_PICKED
            val pull = PullSelector.select(state.inWallet, amount, policy)
            paidUnits = pull.unitIds.map { state.units.getValue(it) }
            shortfall = pull.shortfall
        }
        if (shortfall > Won.ZERO) flags += SettlementFlag.INSUFFICIENT_FUNDS

        val overpaid = paidUnits.total() - amount
        val changeUnits = if (overpaid > Won.ZERO) {
            CashUnitFactory.create(ChangeCalculator.minimalChange(overpaid), now, CashUnitSource.CHANGE, ids)
        } else {
            emptyList()
        }

        return Settlement(
            paymentId = payment.id,
            handId = hand?.id,
            paidUnitIds = paidUnits.map { it.id },
            changeUnits = changeUnits,
            shortfall = shortfall,
            flags = flags,
        )
    }

    /**
     * Units returned to the wallet for a refund of [refund]: the minimal-change breakdown of its amount
     * (rounded up to 10 won, mirroring [settle]) with source [CashUnitSource.REFUND].
     * Throws [IllegalArgumentException] if [refund] is not a refund or its amount is not positive.
     */
    fun refundUnits(refund: DetectedPayment, now: Instant, ids: IdGenerator): List<CashUnit> {
        require(refund.isRefund) { "payment ${refund.id} is not a refund" }
        require(refund.amount.value > 0) { "refund amount must be positive, was ${refund.amount}" }
        val counts = ChangeCalculator.minimalChange(refund.amount.roundUpToCash())
        return CashUnitFactory.create(counts, now, CashUnitSource.REFUND, ids)
    }
}
