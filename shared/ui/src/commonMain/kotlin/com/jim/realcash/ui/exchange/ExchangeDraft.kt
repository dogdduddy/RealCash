package com.jim.realcash.ui.exchange

import com.jim.realcash.domain.calc.ChangeCalculator
import com.jim.realcash.domain.calc.SplitSuggester
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.takeout.TakeOutDraft
import com.jim.realcash.ui.wallet.DenominationCount

/** Which preset produced the "받을 돈" counts. */
enum class ExchangeSuggestion {
    /** [ChangeCalculator.minimalChange] of the tray total ("가장 적은 장수로"). */
    MINIMAL,

    /** [ExchangeDraft.smallerSplit] ("작은 권종으로"). */
    SMALLER,

    /** Edited with the steppers. */
    CUSTOM,
}

/**
 * Editable state of the 환전 screen (pure; unit-tested). Two steps, like a bank counter:
 * 1. [Step.GIVE] — tap wallet stacks to put units on the tray ("내놓을 돈");
 * 2. [Step.RECEIVE] — choose the split to receive ("받을 돈"), pre-filled from a suggestion and editable per
 *    denomination. It can be exchanged when the split sums to the tray total and differs from what was given.
 */
data class ExchangeDraft(
    /** Wallet units, largest first, oldest first within a denomination. Build with [of]. */
    val available: List<CashUnit>,
    /** Units on the tray in the order they were put there. */
    val tray: List<CashUnitId> = emptyList(),
    /** Current step. */
    val step: Step = Step.GIVE,
    /** Counts to receive (step 2). */
    val toCounts: Map<Denomination, Int> = emptyMap(),
    /** Which preset the counts came from. */
    val suggestion: ExchangeSuggestion = ExchangeSuggestion.MINIMAL,
) {
    /** The two steps of the counter. */
    enum class Step {
        /** Pick what to give. */
        GIVE,

        /** Pick what to receive. */
        RECEIVE,
    }

    private val traySet: Set<CashUnitId> by lazy { tray.toSet() }

    /** Units on the tray, largest first. */
    val trayUnits: List<CashUnit> get() = available.filter { it.id in traySet }

    /** Tray unit ids, largest first (handed to `exchange`). */
    val trayIds: List<CashUnitId> get() = trayUnits.map { it.id }

    /** Total on the tray. */
    val trayTotal: Won get() = trayUnits.total()

    /** Tray counts per denomination, largest first. */
    val trayCounts: Map<Denomination, Int> get() = countsOf(trayUnits)

    /** Units of [denomination] still in the wallet (not on the tray). */
    fun remaining(denomination: Denomination): Int = available.count { it.denomination == denomination && it.id !in traySet }

    /** Remaining wallet stacks of [kind], zero counts kept so emptied stacks stay visible. */
    fun remainingOf(kind: Denomination.Kind): List<DenominationCount> =
        Denomination.descending
            .filter { it.kind == kind && available.any { u -> u.denomination == it } }
            .map { DenominationCount(it, remaining(it)) }

    /** Count to receive of [denomination]. */
    fun toCount(denomination: Denomination): Int = toCounts[denomination] ?: 0

    /** Sum of [toCounts]. */
    val toTotal: Won get() = toCounts.total()

    /** The split to hand to `exchange`: non-zero counts, largest first. */
    val toSplit: Map<Denomination, Int>
        get() = Denomination.descending.mapNotNull { d -> toCount(d).takeIf { it > 0 }?.let { d to it } }.toMap()

    /** True when what would be received is exactly what was given (a pointless exchange). */
    val isSameAsGiven: Boolean get() = trayUnits.isNotEmpty() && toSplit == trayCounts

    /** True when the sums match ([SplitSuggester.validate]). */
    val sumMatches: Boolean get() = SplitSuggester.validate(trayTotal, toCounts)

    /** True when "바꾸기" can run. */
    val isValid: Boolean get() = step == Step.RECEIVE && trayUnits.isNotEmpty() && sumMatches && !isSameAsGiven

    /** True when the "작은 권종으로" preset exists for the current tray. */
    val smallerAvailable: Boolean get() = smallerSplit(trayCounts) != null

    /** Puts the oldest remaining unit of [denomination] on the tray (step 1 only). */
    fun addToTray(denomination: Denomination): ExchangeDraft {
        if (step != Step.GIVE) return this
        val unit = available.firstOrNull { it.denomination == denomination && it.id !in traySet } ?: return this
        return copy(tray = tray + unit.id)
    }

    /** Takes the most recently added unit of [denomination] off the tray (step 1 only). */
    fun removeFromTray(denomination: Denomination): ExchangeDraft {
        if (step != Step.GIVE) return this
        val byId = available.associateBy { it.id }
        val index = tray.indexOfLast { byId[it]?.denomination == denomination }
        if (index < 0) return this
        return copy(tray = tray.filterIndexed { i, _ -> i != index })
    }

    /**
     * "창구에 내기": goes to step 2 with the counts pre-filled from [ChangeCalculator.minimalChange].
     *
     * NOTE: when the minimal split is exactly what was given (a single 50,000원 bill, say), the "작은 권종으로"
     * preset is used instead, so the counter never opens on a no-op exchange.
     */
    fun toReceive(): ExchangeDraft {
        if (trayUnits.isEmpty()) return this
        val minimal = ChangeCalculator.minimalChange(trayTotal)
        val smaller = smallerSplit(trayCounts)
        return if (minimal == trayCounts && smaller != null) {
            copy(step = Step.RECEIVE, toCounts = smaller, suggestion = ExchangeSuggestion.SMALLER)
        } else {
            copy(step = Step.RECEIVE, toCounts = minimal, suggestion = ExchangeSuggestion.MINIMAL)
        }
    }

    /** Back to step 1 (the tray is kept). */
    fun backToGive(): ExchangeDraft = copy(step = Step.GIVE)

    /** Applies a preset; [ExchangeSuggestion.CUSTOM] and an unavailable preset leave the draft unchanged. */
    fun withSuggestion(suggestion: ExchangeSuggestion): ExchangeDraft {
        val counts = when (suggestion) {
            ExchangeSuggestion.MINIMAL -> ChangeCalculator.minimalChange(trayTotal)
            ExchangeSuggestion.SMALLER -> smallerSplit(trayCounts) ?: return this
            ExchangeSuggestion.CUSTOM -> return this
        }
        return copy(toCounts = counts, suggestion = suggestion)
    }

    /** One more unit of [denomination] to receive. */
    fun increment(denomination: Denomination): ExchangeDraft =
        copy(toCounts = toCounts + (denomination to toCount(denomination) + 1), suggestion = ExchangeSuggestion.CUSTOM)

    /** One unit less of [denomination] to receive (never below zero). */
    fun decrement(denomination: Denomination): ExchangeDraft {
        val current = toCount(denomination)
        if (current == 0) return this
        return copy(toCounts = toCounts + (denomination to current - 1), suggestion = ExchangeSuggestion.CUSTOM)
    }

    /** Same draft over new wallet [units]; tray units that left the wallet are dropped (back to step 1 if any did). */
    fun rebase(units: List<CashUnit>): ExchangeDraft {
        val sorted = units.sortedWith(TakeOutDraft.UNIT_ORDER)
        if (sorted == available) return this
        val ids = sorted.mapTo(HashSet()) { it.id }
        val kept = tray.filter { it in ids }
        return copy(available = sorted, tray = kept, step = if (kept.size == tray.size) step else Step.GIVE)
    }

    /** Builders and the "작은 권종으로" rule. */
    companion object {
        /** A draft over the wallet [units]. */
        fun of(units: List<CashUnit>): ExchangeDraft = ExchangeDraft(available = units.sortedWith(TakeOutDraft.UNIT_ORDER))

        /**
         * "작은 권종으로": every given unit is broken one step down into the fewest units of the denominations below
         * it, e.g. 50,000 -> 10,000×5, 10,000 -> 5,000×2, 5,000 -> 1,000×5, 1,000 -> 500×2, 500 -> 100×5.
         * Returns `null` when nothing can be broken further (only 10원 coins on the tray, or an empty tray).
         *
         * NOTE: a deliberately simple rule; a real teller might mix (10,000 -> 5,000 + 1,000×5), which the steppers
         * still allow.
         */
        fun smallerSplit(given: Map<Denomination, Int>): Map<Denomination, Int>? {
            val result = LinkedHashMap<Denomination, Int>()
            var broke = false
            for ((d, n) in given) {
                if (n <= 0) continue
                val below = Denomination.descending.filter { it.won < d.won }
                if (below.isEmpty()) {
                    result[d] = (result[d] ?: 0) + n
                    continue
                }
                broke = true
                var rest = d.won * n
                for (b in below) {
                    val k = rest / b.won
                    if (k > 0) {
                        result[b] = (result[b] ?: 0) + k
                        rest -= k * b.won
                    }
                }
            }
            if (!broke) return null
            return Denomination.descending.filter { (result[it] ?: 0) > 0 }.associateWith { result.getValue(it) }
        }

        private fun countsOf(units: List<CashUnit>): Map<Denomination, Int> {
            val counts = units.groupingBy { it.denomination }.eachCount()
            return Denomination.descending.filter { it in counts }.associateWith { counts.getValue(it) }
        }
    }
}
