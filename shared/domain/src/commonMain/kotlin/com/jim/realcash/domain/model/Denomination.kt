package com.jim.realcash.domain.model

import kotlinx.serialization.Serializable

/** The eight Korean cash denominations, from the 50,000원 bill down to the 10원 coin. */
@Serializable
enum class Denomination(
    /** Face value in won. */
    val won: Int,
    /** Whether this denomination is a bill or a coin. */
    val kind: Kind,
) {
    /** 50,000원 bill. */
    W50000(50_000, Kind.BILL),

    /** 10,000원 bill. */
    W10000(10_000, Kind.BILL),

    /** 5,000원 bill. */
    W5000(5_000, Kind.BILL),

    /** 1,000원 bill. */
    W1000(1_000, Kind.BILL),

    /** 500원 coin. */
    C500(500, Kind.COIN),

    /** 100원 coin. */
    C100(100, Kind.COIN),

    /** 50원 coin. */
    C50(50, Kind.COIN),

    /** 10원 coin. */
    C10(10, Kind.COIN),
    ;

    /** Face value as [Won]. */
    val amount: Won get() = Won(won)

    /** Physical kind of a denomination. */
    enum class Kind {
        /** Paper bill. */
        BILL,

        /** Metal coin. */
        COIN,
    }

    /** Lookup helpers. */
    companion object {
        /** All denominations ordered from largest to smallest face value. */
        val descending: List<Denomination> = entries.sortedByDescending { it.won }

        /** All denominations ordered from smallest to largest face value. */
        val ascending: List<Denomination> = descending.reversed()

        /** The denomination whose face value is exactly [won], or `null` if there is none. */
        fun of(won: Int): Denomination? = entries.firstOrNull { it.won == won }
    }
}

/** Total value of a denomination-to-count map. */
fun Map<Denomination, Int>.total(): Won = Won(entries.sumOf { (d, n) -> d.won * n })
