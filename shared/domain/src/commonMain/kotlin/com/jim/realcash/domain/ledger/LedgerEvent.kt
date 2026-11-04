package com.jim.realcash.domain.ledger

import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.Hand
import com.jim.realcash.domain.model.HandId
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Settlement
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One append-only ledger entry. The wallet state is derived by folding events in order with [WalletReducer].
 *
 * Serial names are part of the persisted format; do not rename them.
 */
@Serializable
sealed interface LedgerEvent {
    /** Unique event identifier. Applying the same event twice is rejected. */
    val id: String

    /** When the event happened. */
    val at: Instant
}

/** An allowance was received; [units] enter the wallet. */
@Serializable
@SerialName("AllowanceReceived")
data class AllowanceReceived(
    override val id: String,
    override val at: Instant,
    /** New units, all in state IN_WALLET. */
    val units: List<CashUnit>,
    /** First day of the budget period this allowance belongs to. */
    val periodStart: LocalDate,
) : LedgerEvent

/** The user took [hand] out of the wallet. Only allowed while no other hand is active. */
@Serializable
@SerialName("MovedToHand")
data class MovedToHand(
    override val id: String,
    override val at: Instant,
    /** The new active hand. */
    val hand: Hand,
) : LedgerEvent

/** The active hand went back into the wallet unused. */
@Serializable
@SerialName("HandReturned")
data class HandReturned(
    override val id: String,
    override val at: Instant,
    /** The hand being returned; must be the active one. */
    val handId: HandId,
    /** Why the hand was returned. */
    val reason: ReturnReason,
) : LedgerEvent

/** Why a hand went back into the wallet. */
@Serializable
enum class ReturnReason {
    /** The hand's TTL elapsed without a matching payment. */
    EXPIRED,

    /** The user put the cash back. */
    USER,

    /**
     * The hand was closed in connection with a settlement.
     *
     * NOTE: a [Settled] event already releases the hand it used, so this reason is only meaningful for a
     * hand that is still active at that point (e.g. a payment was settled without using the hand).
     */
    SETTLED,
}

/** A payment or refund notification was detected; it waits for settlement or rejection. */
@Serializable
@SerialName("PaymentDetected")
data class PaymentDetected(
    override val id: String,
    override val at: Instant,
    /** The detected payment. */
    val payment: DetectedPayment,
) : LedgerEvent

/** The user said a pending detection was not a real payment ("아니에요"). */
@Serializable
@SerialName("PaymentRejected")
data class PaymentRejected(
    override val id: String,
    override val at: Instant,
    /** The rejected pending payment. */
    val paymentId: PaymentId,
) : LedgerEvent

/** A pending payment was settled against the hand and/or wallet. */
@Serializable
@SerialName("Settled")
data class Settled(
    override val id: String,
    override val at: Instant,
    /** The settlement to apply. */
    val settlement: Settlement,
) : LedgerEvent

/** A payment was refunded; [units] (built with the change rule) go back into the wallet. */
@Serializable
@SerialName("Refunded")
data class Refunded(
    override val id: String,
    override val at: Instant,
    /** Either a pending refund detection, or a previously settled payment. */
    val paymentId: PaymentId,
    /** Refunded units, all in state IN_WALLET. */
    val units: List<CashUnit>,
) : LedgerEvent

/** The user exchanged wallet units for others of the same total value. */
@Serializable
@SerialName("Exchanged")
data class Exchanged(
    override val id: String,
    override val at: Instant,
    /** Wallet units given away. */
    val fromUnitIds: List<CashUnitId>,
    /** New units received, all in state IN_WALLET. */
    val toUnits: List<CashUnit>,
) : LedgerEvent

/** Manual correction of the wallet contents. */
@Serializable
@SerialName("Adjusted")
data class Adjusted(
    override val id: String,
    override val at: Instant,
    /** Wallet units to remove. */
    val removedUnitIds: List<CashUnitId>,
    /** Units to add, all in state IN_WALLET. */
    val addedUnits: List<CashUnit>,
    /** Why the correction was made. */
    val reason: String,
) : LedgerEvent
