package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.BudgetPeriodPolicy
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Computes allowance period boundaries. */
object BudgetPeriods {

    /** The period (both ends inclusive) that contains [today] under [policy]. */
    fun currentPeriod(policy: BudgetPeriodPolicy, today: LocalDate): ClosedRange<LocalDate> = when (policy.cycle) {
        BudgetPeriodPolicy.Cycle.WEEKLY -> {
            val back = (today.dayOfWeek.isoDayNumber - policy.startDay + 7) % 7
            val start = today.minus(back, DateTimeUnit.DAY)
            start..start.plus(6, DateTimeUnit.DAY)
        }
        BudgetPeriodPolicy.Cycle.MONTHLY -> {
            val thisMonthStart = LocalDate(today.year, today.month, policy.startDay)
            val start = if (today >= thisMonthStart) thisMonthStart else thisMonthStart.minus(DatePeriod(months = 1))
            val end = start.plus(DatePeriod(months = 1)).minus(1, DateTimeUnit.DAY)
            start..end
        }
    }
}
