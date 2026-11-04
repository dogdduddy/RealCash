package com.jim.realcash.domain.model

import kotlin.jvm.JvmInline
import kotlinx.serialization.Serializable

/**
 * An amount of Korean won as an integer number of won.
 *
 * Non-negativity is not validated on construction; the domain operations guarantee it in their results.
 */
@Serializable
@JvmInline
value class Won(val value: Int) : Comparable<Won> {

    /** Sum of two amounts. */
    operator fun plus(other: Won): Won = Won(value + other.value)

    /** Difference of two amounts (may be negative; callers decide how to handle that). */
    operator fun minus(other: Won): Won = Won(value - other.value)

    /** This amount multiplied by [count]. */
    operator fun times(count: Int): Won = Won(value * count)

    /** Compares by numeric value. */
    override fun compareTo(other: Won): Int = value.compareTo(other.value)

    /** True when the amount can be represented with Korean cash (multiples of 10 won). */
    val isCashRepresentable: Boolean get() = value % CASH_UNIT == 0

    /**
     * The amount rounded **up** to the next multiple of 10 won, i.e. what has to leave the wallet in cash.
     * A 6,505원 card payment costs 6,510원 in cash because there is no 5원 coin.
     */
    fun roundUpToCash(): Won {
        val rem = value % CASH_UNIT
        return if (rem == 0) this else Won(value - rem + CASH_UNIT)
    }

    /** Human-readable form, e.g. `6,500원`. */
    override fun toString(): String = "${formatGrouped(value)}원"

    /** Constants for [Won]. */
    companion object {
        /** Zero won. */
        val ZERO: Won = Won(0)

        /** Smallest Korean cash denomination in won. */
        const val CASH_UNIT: Int = 10

        private fun formatGrouped(v: Int): String {
            val digits = kotlin.math.abs(v.toLong()).toString()
            val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
            return if (v < 0) "-$grouped" else grouped
        }
    }
}

/** Sum of all amounts; [Won.ZERO] for an empty collection. */
fun Iterable<Won>.sum(): Won = fold(Won.ZERO) { acc, w -> acc + w }
