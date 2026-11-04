package com.jim.realcash.ui.takeout

import com.jim.realcash.domain.calc.ChangeCalculator
import com.jim.realcash.domain.calc.PullSelector
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.format.AmountInput
import com.jim.realcash.ui.wallet.DenominationCount

/** The live line under the expected amount: how the picked cash compares with what the user expects to pay. */
sealed interface TakeOutFeedback {
    /** No expected amount entered: nothing to compare. */
    data object None : TakeOutFeedback

    /**
     * The picked cash does not cover the expected amount.
     *
     * @property missing what is still missing.
     * @property walletTotal set when even everything available cannot cover the expected amount ("지갑에 ₩x 남았어요");
     *   [missing] is then the shortfall of the whole wallet.
     */
    data class Short(val missing: Won, val walletTotal: Won? = null) : TakeOutFeedback

    /** The picked cash matches the expected amount exactly. */
    data object Exact : TakeOutFeedback

    /**
     * The picked cash exceeds the expected amount; [change] is what a shop would hand back (fewest units).
     *
     * @property over picked total minus the (cash-rounded) expected amount.
     */
    data class Over(val over: Won, val change: Map<Denomination, Int>) : TakeOutFeedback {
        /** Total of [change]; equals [over]. */
        val changeTotal: Won get() = change.total()
    }
}

/** What the last helper button ("잔돈으로 딱 맞추기" / "가장 적은 장수로") did; shown under the helpers. */
sealed interface TakeOutHelperResult {
    /** An exact subset was found and picked. */
    data class Exact(val total: Won) : TakeOutHelperResult

    /** No exact subset exists; the LARGEST_FIRST fallback of [PullSelector] was picked instead. */
    data class NotExact(val total: Won) : TakeOutHelperResult

    /** FEWEST_UNITS picked [count] units worth [total]. */
    data class Fewest(val count: Int, val total: Won) : TakeOutHelperResult

    /** Everything available was picked and still [shortfall] is missing. */
    data class Shortfall(val shortfall: Won) : TakeOutHelperResult
}

/**
 * Editable state of the 꺼내기 screen (pure; unit-tested).
 *
 * [available] are the units that may be picked (the wallet, plus the current hand when refilling). Picking a
 * denomination takes its oldest unpicked unit (the same order [PullSelector] uses); putting one back releases the
 * most recently picked unit of that denomination.
 *
 * NOTE: comparisons use the expected amount rounded up to 10원 ([Won.roundUpToCash]), because that is what a
 * 6,505원 card payment costs in cash; the change estimate therefore always is a valid cash amount.
 */
data class TakeOutDraft(
    /** Pickable units, largest denomination first, oldest first within a denomination. Build with [of]. */
    val available: List<CashUnit>,
    /** Picked unit ids in pick order. Always a subset of [available]. */
    val picked: List<CashUnitId> = emptyList(),
    /** Digits of the expected amount field (no separators). */
    val expectedDigits: String = "",
    /** Result of the last helper, cleared by any manual change. */
    val helper: TakeOutHelperResult? = null,
) {
    private val pickedSet: Set<CashUnitId> by lazy { picked.toSet() }

    /** The expected amount, or `null` when the field is empty or zero. */
    val expected: Won? get() = AmountInput.parse(expectedDigits)?.takeIf { it.value > 0 }

    /** [expected] rounded up to 10원, the cash that has to leave the wallet. */
    val cashExpected: Won? get() = expected?.roundUpToCash()

    /** Picked units, largest denomination first. */
    val pickedUnits: List<CashUnit> get() = available.filter { it.id in pickedSet }

    /** Picked unit ids, largest denomination first (the order handed to `moveToHand`). */
    val pickedIds: List<CashUnitId> get() = pickedUnits.map { it.id }

    /** Total of the picked units. */
    val pickedTotal: Won get() = pickedUnits.total()

    /** Total of everything available. */
    val availableTotal: Won get() = available.total()

    /** Picked count per denomination, largest first, zeros omitted. */
    val pickedCounts: List<DenominationCount> get() = countsOf(pickedUnits)

    /** Bills still available per denomination (zero counts kept, so emptied stacks stay visible dimmed). */
    val remainingBills: List<DenominationCount> get() = remainingOfKind(Denomination.Kind.BILL)

    /** Coins still available per denomination (zero counts kept). */
    val remainingCoins: List<DenominationCount> get() = remainingOfKind(Denomination.Kind.COIN)

    /** Units of [denomination] that can still be picked. */
    fun remaining(denomination: Denomination): Int = available.count { it.denomination == denomination && it.id !in pickedSet }

    /** Picked units of [denomination]. */
    fun pickedCount(denomination: Denomination): Int = available.count { it.denomination == denomination && it.id in pickedSet }

    /** The live comparison with the expected amount. */
    val feedback: TakeOutFeedback
        get() {
            val target = cashExpected ?: return TakeOutFeedback.None
            val total = pickedTotal
            return when {
                availableTotal < target && total < target -> TakeOutFeedback.Short(target - availableTotal, availableTotal)
                total < target -> TakeOutFeedback.Short(target - total)
                total == target -> TakeOutFeedback.Exact
                else -> (total - target).let { TakeOutFeedback.Over(it, ChangeCalculator.minimalChange(it)) }
            }
        }

    /** True when "손에 들기" can run. */
    val canConfirm: Boolean get() = picked.isNotEmpty()

    /** True when the helper buttons can run (they need an expected amount). */
    val helpersEnabled: Boolean get() = cashExpected != null && available.isNotEmpty()

    /** Picks the oldest unpicked unit of [denomination]; unchanged when none is left. */
    fun add(denomination: Denomination): TakeOutDraft {
        val unit = available.firstOrNull { it.denomination == denomination && it.id !in pickedSet } ?: return this
        return copy(picked = picked + unit.id, helper = null)
    }

    /** Puts back the most recently picked unit of [denomination]; unchanged when none is picked. */
    fun remove(denomination: Denomination): TakeOutDraft {
        val byId = available.associateBy { it.id }
        val index = picked.indexOfLast { byId[it]?.denomination == denomination }
        if (index < 0) return this
        return copy(picked = picked.filterIndexed { i, _ -> i != index }, helper = null)
    }

    /** Puts everything back. */
    fun clear(): TakeOutDraft = copy(picked = emptyList(), helper = null)

    /** New expected amount text (digits only are kept). */
    fun withExpectedText(text: String): TakeOutDraft = copy(expectedDigits = AmountInput.sanitize(text), helper = null)

    /**
     * "잔돈으로 딱 맞추기": replaces the picks with [PullSelector] EXACT_IF_POSSIBLE over everything available
     * (small change first when several exact subsets exist). Unchanged without an expected amount.
     */
    fun fitExact(): TakeOutDraft = pull(PullPolicy.EXACT_IF_POSSIBLE)

    /** "가장 적은 장수로": replaces the picks with [PullSelector] FEWEST_UNITS. Unchanged without an expected amount. */
    fun fewest(): TakeOutDraft = pull(PullPolicy.FEWEST_UNITS)

    /** Same draft over a new set of [units] (the wallet changed underneath); picks that disappeared are dropped. */
    fun rebase(units: List<CashUnit>): TakeOutDraft {
        val sorted = units.sortedWith(UNIT_ORDER)
        if (sorted == available) return this
        val ids = sorted.mapTo(HashSet()) { it.id }
        return copy(available = sorted, picked = picked.filter { it in ids })
    }

    private fun pull(policy: PullPolicy): TakeOutDraft {
        val target = cashExpected ?: return this
        if (available.isEmpty()) return this
        val result = PullSelector.select(available, target, policy)
        val outcome = when {
            result.shortfall.value > 0 -> TakeOutHelperResult.Shortfall(result.shortfall)
            policy == PullPolicy.FEWEST_UNITS -> TakeOutHelperResult.Fewest(result.unitIds.size, result.total)
            result.total == target -> TakeOutHelperResult.Exact(result.total)
            else -> TakeOutHelperResult.NotExact(result.total)
        }
        return copy(picked = result.unitIds, helper = outcome)
    }

    private fun remainingOfKind(kind: Denomination.Kind): List<DenominationCount> =
        Denomination.descending
            .filter { it.kind == kind && available.any { u -> u.denomination == it } }
            .map { DenominationCount(it, remaining(it)) }

    /** Builders. */
    companion object {
        /** Largest denomination first; oldest unit first within a denomination; id as the final tiebreaker. */
        internal val UNIT_ORDER: Comparator<CashUnit> =
            compareByDescending<CashUnit> { it.denomination.won }.thenBy { it.acquiredAt }.thenBy { it.id.value }

        /** A draft over [available] with optional initial [picked] ids (unknown ids are dropped) and [expected] amount. */
        fun of(available: List<CashUnit>, picked: List<CashUnitId> = emptyList(), expected: Won? = null): TakeOutDraft {
            val sorted = available.sortedWith(UNIT_ORDER)
            val ids = sorted.mapTo(HashSet()) { it.id }
            return TakeOutDraft(
                available = sorted,
                picked = picked.filter { it in ids }.distinct(),
                expectedDigits = expected?.takeIf { it.value > 0 }?.value?.toString().orEmpty(),
            )
        }

        private fun countsOf(units: List<CashUnit>): List<DenominationCount> {
            val counts = units.groupingBy { it.denomination }.eachCount()
            return Denomination.descending.filter { it in counts }.map { DenominationCount(it, counts.getValue(it)) }
        }
    }
}
