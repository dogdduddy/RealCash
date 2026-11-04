package com.jim.realcash.domain.model

import kotlinx.serialization.Serializable

/**
 * How allowance periods are cut.
 *
 * For [Cycle.WEEKLY], [startDay] is the ISO day of week (Monday = 1 .. Sunday = 7).
 * For [Cycle.MONTHLY], [startDay] is the day of month (1..28, so every month has it).
 */
@Serializable
data class BudgetPeriodPolicy(
    /** Period length. */
    val cycle: Cycle,
    /** First day of each period; meaning depends on [cycle]. */
    val startDay: Int,
) {
    init {
        when (cycle) {
            Cycle.WEEKLY -> require(startDay in 1..7) { "Weekly startDay must be 1..7 (Mon..Sun), was $startDay" }
            Cycle.MONTHLY -> require(startDay in 1..28) { "Monthly startDay must be 1..28, was $startDay" }
        }
    }

    /** Period length. */
    @Serializable
    enum class Cycle {
        /** Seven-day periods. */
        WEEKLY,

        /** Calendar-month periods starting on [startDay]. */
        MONTHLY,
    }
}
