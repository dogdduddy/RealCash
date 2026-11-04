package com.jim.realcash.domain.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** Lifecycle state of a [CashUnit]. A unit is in exactly one state at a time. */
@Serializable
enum class CashUnitState {
    /** Sitting in the wallet. */
    IN_WALLET,

    /** Taken out into the active [Hand]. */
    IN_HAND,

    /** Handed over in a settled payment. */
    SPENT,

    /** Given away in a user-initiated exchange (e.g. one 50,000원 bill for five 10,000원 bills). */
    EXCHANGED_AWAY,
}

/** How a [CashUnit] came into the wallet. */
@Serializable
enum class CashUnitSource {
    /** Part of a periodic allowance. */
    ALLOWANCE,

    /** Change returned after a payment. */
    CHANGE,

    /** Received in an exchange. */
    EXCHANGE,

    /** Added by a manual correction. */
    ADJUSTMENT,

    /** Returned by a refunded payment. */
    REFUND,
}

/** One bill or one coin. Units are individual objects and are never re-organised automatically. */
@Serializable
data class CashUnit(
    /** Stable identity of this unit. */
    val id: CashUnitId,
    /** Face value. */
    val denomination: Denomination,
    /** Current lifecycle state. */
    val state: CashUnitState,
    /** When the unit entered the wallet. */
    val acquiredAt: Instant,
    /** How the unit entered the wallet. */
    val source: CashUnitSource,
) {
    /** Face value as [Won]. */
    val amount: Won get() = denomination.amount
}

/** Total face value of the given units. */
fun Iterable<CashUnit>.total(): Won = Won(sumOf { it.denomination.won })

/** Number of units per denomination, largest denomination first, zero counts omitted. */
fun Iterable<CashUnit>.countsByDenomination(): Map<Denomination, Int> {
    val counts = groupingBy { it.denomination }.eachCount()
    return Denomination.descending.filter { it in counts }.associateWith { counts.getValue(it) }
}
