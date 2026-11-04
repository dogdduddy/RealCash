package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total

/** Style of the default denomination split offered on the allowance screen. */
enum class SplitStyle {
    /** ATM style: as many large bills as possible (same as [ChangeCalculator.minimalChange]). */
    LARGEST_FIRST,

    /** More 10,000원 bills: at least 50,000원 (or the whole amount, if smaller) is kept out of 50,000원 bills. */
    BALANCED,
}

/** Suggests and validates how an allowance amount is split into denominations. */
object SplitSuggester {

    private const val BALANCED_RESERVE = 50_000

    /**
     * Default split of [amount] for the given [style]; the counts always sum to [amount].
     *
     * [SplitStyle.BALANCED]: `n50000 = max(0, (amount - 50,000) / 50,000)`, the rest is broken down greedily
     * without 50,000원 bills. E.g. 100,000원 -> 50,000×1 + 10,000×5.
     * Throws [IllegalArgumentException] if [amount] is negative or not a multiple of 10 won.
     */
    fun suggest(amount: Won, style: SplitStyle): Map<Denomination, Int> = when (style) {
        SplitStyle.LARGEST_FIRST -> ChangeCalculator.minimalChange(amount)
        SplitStyle.BALANCED -> {
            ChangeCalculator.minimalChange(amount) // validates the amount
            val fifties = maxOf(0, (amount.value - BALANCED_RESERVE) / Denomination.W50000.won)
            val rest = amount.value - fifties * Denomination.W50000.won
            val smaller = ChangeCalculator.greedy(rest, Denomination.descending - Denomination.W50000)
            if (fifties > 0) linkedMapOf(Denomination.W50000 to fifties) + smaller else smaller
        }
    }

    /** True when every count is non-negative and the split sums exactly to [amount]. */
    fun validate(amount: Won, split: Map<Denomination, Int>): Boolean =
        amount.value >= 0 && split.values.all { it >= 0 } && split.total() == amount
}
