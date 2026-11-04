package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total

/** Chooses which units to pull out of the wallet to cover an amount. */
object PullSelector {

    /** Largest amount for which [PullPolicy.EXACT_IF_POSSIBLE] runs the exact subset-sum DP. */
    val EXACT_DP_LIMIT: Won = Won(5_000_000)

    /**
     * Search budget (visited nodes) for the [PullPolicy.FEWEST_UNITS] branch-and-bound. When exhausted, the best
     * solution found so far is used; it always has the minimal unit count, only the overpayment may not be minimal.
     */
    internal const val FEWEST_UNITS_NODE_BUDGET: Int = 200_000

    /** Units chosen by [select]. */
    data class PullResult(
        /** Chosen units, largest denomination first. */
        val unitIds: List<CashUnitId>,
        /** Total face value of the chosen units. */
        val total: Won,
        /** Part of the amount the available units could not cover. Zero unless everything was chosen. */
        val shortfall: Won,
    )

    /**
     * Picks units from [available] whose total is at least [amount].
     *
     * - [PullPolicy.EXACT_IF_POSSIBLE]: a subset summing exactly to [amount] if one exists (bounded DP for
     *   amounts up to [EXACT_DP_LIMIT], a greedy attempt above that), otherwise [PullPolicy.LARGEST_FIRST].
     * - [PullPolicy.LARGEST_FIRST]: the "lazy cash payer". Repeat: if some remaining unit covers what is still
     *   owed, add one unit of the *smallest* such denomination and stop; otherwise hand over the largest remaining
     *   unit and continue with the rest. E.g. {10000×2, 5000, 1000×2} for 12,000 -> 10000 + 5000 (change 3,000).
     * - [PullPolicy.FEWEST_UNITS]: fewest units; ties broken by the smallest overpayment.
     *
     * If [available] cannot cover [amount], every unit is chosen and the rest is reported as shortfall.
     * Within one denomination, older units (earlier `acquiredAt`, then id) are taken first.
     */
    fun select(available: List<CashUnit>, amount: Won, policy: PullPolicy): PullResult {
        if (amount.value <= 0) return PullResult(emptyList(), Won.ZERO, Won.ZERO)
        val sorted = available.sortedWith(UNIT_ORDER)
        val total = sorted.total()
        if (total < amount) return PullResult(sorted.map { it.id }, total, amount - total)

        val chosen = when (policy) {
            PullPolicy.LARGEST_FIRST -> largestFirst(sorted, amount)
            PullPolicy.FEWEST_UNITS -> fewestUnits(sorted, amount)
            PullPolicy.EXACT_IF_POSSIBLE -> exact(sorted, amount) ?: largestFirst(sorted, amount)
        }
        val chosenTotal = chosen.total()
        return PullResult(chosen.map { it.id }, chosenTotal, Won.ZERO)
    }

    /** Largest denomination first; within a denomination the oldest unit first; id as final tiebreaker. */
    private val UNIT_ORDER: Comparator<CashUnit> =
        compareByDescending<CashUnit> { it.denomination.won }
            .thenBy { it.acquiredAt }
            .thenBy { it.id.value }

    /**
     * LARGEST_FIRST: while nothing covers the remaining amount on its own, hand over the largest unit; then finish
     * with one unit of the smallest denomination that covers the rest. [sorted] must cover [amount] in total.
     */
    private fun largestFirst(sorted: List<CashUnit>, amount: Won): List<CashUnit> {
        val groups = groupByDenomination(sorted) // descending face value, oldest unit first within a group
        val next = IntArray(groups.size) // index of the next unused unit in each group
        val result = mutableListOf<CashUnit>()
        var remaining = amount.value
        while (true) {
            var covering = -1
            var largest = -1
            for (g in groups.indices) {
                if (next[g] >= groups[g].second.size) continue
                if (largest < 0) largest = g
                if (groups[g].first.won >= remaining) covering = g // later groups are smaller
            }
            if (covering >= 0) {
                result += groups[covering].second[next[covering]]
                return result
            }
            // Cannot run dry: select() already checked that the total covers the amount.
            val unit = groups[largest].second[next[largest]++]
            result += unit
            remaining -= unit.denomination.won
        }
    }

    // ---------------------------------------------------------------------------------------------
    // FEWEST_UNITS: minimal k such that the k largest units cover the amount, then branch-and-bound over
    // per-denomination counts (exactly k units) minimising the total.
    // ---------------------------------------------------------------------------------------------

    private fun fewestUnits(sorted: List<CashUnit>, amount: Won): List<CashUnit> {
        var k = 0
        var topSum = 0
        while (topSum < amount.value) {
            topSum += sorted[k].denomination.won
            k++
        }
        val groups = groupByDenomination(sorted)
        val values = IntArray(groups.size) { groups[it].first.won }
        val counts = IntArray(groups.size) { groups[it].second.size }
        val search = FewestUnitsSearch(values, counts, amount.value, k)
        val picks = search.run()
        return groups.indices.flatMap { i -> groups[i].second.take(picks[i]) }
    }

    private class FewestUnitsSearch(
        private val values: IntArray,
        private val counts: IntArray,
        private val target: Int,
        private val k: Int,
    ) {
        private val n = values.size
        private val current = IntArray(n)
        private var best: IntArray
        private var bestSum: Long
        private var nodes = 0

        init {
            // Initial solution: the k largest units (always valid, minimal count).
            best = IntArray(n)
            var left = k
            for (i in 0 until n) {
                val take = minOf(left, counts[i])
                best[i] = take
                left -= take
            }
            bestSum = sumOf(best)
        }

        fun run(): IntArray {
            if (bestSum > target) dfs(0, k, 0L)
            return best
        }

        private fun sumOf(picks: IntArray): Long = picks.indices.sumOf { picks[it].toLong() * values[it] }

        /** Largest total achievable with exactly [r] units from groups [from]..; -1 if impossible. */
        private fun maxRest(from: Int, r: Int): Long {
            var left = r
            var sum = 0L
            for (i in from until n) {
                val take = minOf(left, counts[i])
                sum += take.toLong() * values[i]
                left -= take
            }
            return if (left == 0) sum else -1
        }

        /** Smallest total achievable with exactly [r] units from groups [from]..; -1 if impossible. */
        private fun minRest(from: Int, r: Int): Long {
            var left = r
            var sum = 0L
            for (i in n - 1 downTo from) {
                val take = minOf(left, counts[i])
                sum += take.toLong() * values[i]
                left -= take
            }
            return if (left == 0) sum else -1
        }

        private fun dfs(i: Int, r: Int, sum: Long) {
            if (bestSum == target.toLong() || ++nodes > FEWEST_UNITS_NODE_BUDGET) return
            if (i == n) {
                if (r == 0 && sum >= target && sum < bestSum) {
                    bestSum = sum
                    best = current.copyOf()
                }
                return
            }
            val max = maxRest(i, r)
            if (max < 0 || sum + max < target) return
            val lowerBound = maxOf(target.toLong(), sum + minRest(i, r))
            if (lowerBound >= bestSum) return
            for (take in minOf(counts[i], r) downTo 0) {
                current[i] = take
                dfs(i + 1, r - take, sum + take.toLong() * values[i])
            }
            current[i] = 0
        }
    }

    // ---------------------------------------------------------------------------------------------
    // EXACT_IF_POSSIBLE
    // ---------------------------------------------------------------------------------------------

    private fun exact(sorted: List<CashUnit>, amount: Won): List<CashUnit>? {
        if (!amount.isCashRepresentable) return null
        return if (amount <= EXACT_DP_LIMIT) exactDp(sorted, amount) else exactGreedy(sorted, amount)
    }

    /** Greedy exact attempt used above [EXACT_DP_LIMIT]: take each unit (largest first) if it still fits. */
    private fun exactGreedy(sorted: List<CashUnit>, amount: Won): List<CashUnit>? {
        val result = mutableListOf<CashUnit>()
        var sum = 0
        for (unit in sorted) {
            if (sum + unit.denomination.won <= amount.value) {
                result += unit
                sum += unit.denomination.won
            }
        }
        return if (sum == amount.value) result else null
    }

    /**
     * Bounded subset-sum DP over amounts in 10원 steps (at most 500,001 cells).
     *
     * NOTE: denominations are processed from the smallest up, so when several exact subsets exist the one
     * that uses small change first is returned ("잔돈 털기", design doc 5.4). The spec only requires *an*
     * exact subset.
     */
    private fun exactDp(sorted: List<CashUnit>, amount: Won): List<CashUnit>? {
        val target = amount.value / Won.CASH_UNIT
        val groups = groupByDenomination(sorted).reversed() // ascending face value
        val reachable = BooleanArray(target + 1)
        val layer = ByteArray(target + 1) { -1 }
        val usedInLayer = IntArray(target + 1)
        reachable[0] = true

        for ((li, group) in groups.withIndex()) {
            val step = group.first.won / Won.CASH_UNIT
            val limit = group.second.size
            for (s in step..target) {
                if (reachable[s]) continue
                val prev = s - step
                if (!reachable[prev]) continue
                val used = if (layer[prev].toInt() == li) usedInLayer[prev] else 0
                if (used < limit) {
                    reachable[s] = true
                    layer[s] = li.toByte()
                    usedInLayer[s] = used + 1
                }
            }
            if (reachable[target]) break
        }
        if (!reachable[target]) return null

        val picks = IntArray(groups.size)
        var s = target
        while (s > 0) {
            val li = layer[s].toInt()
            val k = usedInLayer[s]
            picks[li] = k
            s -= k * (groups[li].first.won / Won.CASH_UNIT)
        }
        // Return largest denomination first, like every other policy.
        return groups.indices.reversed().flatMap { li -> groups[li].second.take(picks[li]) }
    }

    /** Groups already-sorted units by denomination, preserving the descending order. */
    private fun groupByDenomination(sorted: List<CashUnit>): List<Pair<Denomination, List<CashUnit>>> =
        sorted.groupBy { it.denomination }.map { (d, units) -> d to units }
}
