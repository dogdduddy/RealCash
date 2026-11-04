package com.jim.realcash.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** A card/mobile payment (or refund) detected from a notification or entered manually. */
@Serializable
data class DetectedPayment(
    /** Identity of this payment. */
    val id: PaymentId,
    /** Paid (or refunded) amount as reported by the source. */
    val amount: Won,
    /** Merchant name, if it could be extracted. */
    val merchant: String?,
    /** Android package name or iOS app identifier the notification came from. */
    val sourceApp: String,
    /** Raw notification text kept for re-parsing. Never leaves the device. */
    val rawText: String,
    /** When the payment was detected. */
    val detectedAt: Instant,
    /** Parser confidence in `0..1`. */
    val confidence: Float,
    /** True for a cancellation / refund notification. */
    val isRefund: Boolean,
)

/** Markers describing how a [Settlement] came about. */
@Serializable
enum class SettlementFlag {
    /** No hand was active; units were pulled from the wallet automatically. */
    NOT_HAND_PICKED,

    /** The hand did not cover the payment; extra units were pulled from the wallet. */
    ADDED_FROM_WALLET,

    /** Wallet and hand together could not cover the payment; see [Settlement.shortfall]. */
    INSUFFICIENT_FUNDS,

    /** The settlement was entered or confirmed manually by the user. */
    MANUAL,
}

/** Result of matching a payment against the hand and wallet. */
@Serializable
data class Settlement(
    /** The settled payment. */
    val paymentId: PaymentId,
    /** The hand that was used, if any. */
    val handId: HandId?,
    /** Units handed over (they become [CashUnitState.SPENT]). */
    val paidUnitIds: List<CashUnitId>,
    /** Newly created change units (source [CashUnitSource.CHANGE], state [CashUnitState.IN_WALLET]). */
    val changeUnits: List<CashUnit>,
    /** Part of the payment the hand and wallet could not cover. Usually zero, never negative. */
    val shortfall: Won,
    /** How the settlement came about. */
    val flags: Set<SettlementFlag>,
)

/** Strategy for automatically pulling units out of the wallet. */
@Serializable
enum class PullPolicy {
    /** Prefer large denominations so that change is produced (intentional friction; the default). */
    LARGEST_FIRST,

    /** Use as few units as possible; ties broken by the smallest overpayment. */
    FEWEST_UNITS,

    /** Pay the exact amount if some subset allows it, otherwise fall back to [LARGEST_FIRST]. */
    EXACT_IF_POSSIBLE,
}
