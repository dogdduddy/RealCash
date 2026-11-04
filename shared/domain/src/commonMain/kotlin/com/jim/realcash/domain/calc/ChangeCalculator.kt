package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.IdGenerator
import kotlin.time.Instant

/** Computes change the way a shop hands it out: the fewest possible units. */
object ChangeCalculator {

    /**
     * Fewest-units breakdown of [amount], largest denomination first, zero counts omitted.
     *
     * Korean denominations form a canonical coin system, so greedy is optimal.
     * Throws [IllegalArgumentException] if [amount] is negative or not a multiple of 10 won.
     */
    fun minimalChange(amount: Won): Map<Denomination, Int> {
        require(amount.value >= 0) { "amount must not be negative, was $amount" }
        require(amount.isCashRepresentable) { "amount must be a multiple of ${Won.CASH_UNIT}원, was $amount" }
        return greedy(amount.value, Denomination.descending)
    }

    /** Greedy breakdown of [won] over [denominations] (must be descending). Remainder, if any, is dropped. */
    internal fun greedy(won: Int, denominations: List<Denomination>): Map<Denomination, Int> {
        var rest = won
        val result = LinkedHashMap<Denomination, Int>()
        for (d in denominations) {
            val n = rest / d.won
            if (n > 0) {
                result[d] = n
                rest -= n * d.won
            }
        }
        return result
    }
}

/** Turns denomination counts into concrete, newly identified [CashUnit]s. */
object CashUnitFactory {

    /**
     * Creates one IN_WALLET unit per counted bill/coin, largest denomination first.
     * Throws [IllegalArgumentException] on negative counts.
     */
    fun create(
        counts: Map<Denomination, Int>,
        acquiredAt: Instant,
        source: CashUnitSource,
        ids: IdGenerator,
    ): List<CashUnit> {
        require(counts.values.all { it >= 0 }) { "counts must not be negative: $counts" }
        return Denomination.descending.flatMap { d ->
            List(counts[d] ?: 0) {
                CashUnit(
                    id = CashUnitId(ids.next()),
                    denomination = d,
                    state = CashUnitState.IN_WALLET,
                    acquiredAt = acquiredAt,
                    source = source,
                )
            }
        }
    }
}
