package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.domain.model.BudgetPeriodPolicy.Cycle.MONTHLY
import com.jim.realcash.domain.model.BudgetPeriodPolicy.Cycle.WEEKLY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.datetime.LocalDate

class BudgetPeriodsTest {
    private fun period(policy: BudgetPeriodPolicy, today: String): Pair<LocalDate, LocalDate> {
        val range = BudgetPeriods.currentPeriod(policy, LocalDate.parse(today))
        return range.start to range.endInclusive
    }

    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun weeklyStartingMonday() {
        val monday = BudgetPeriodPolicy(WEEKLY, 1)
        // 2026-11-04 is a Wednesday.
        assertEquals(d("2026-11-02") to d("2026-11-08"), period(monday, "2026-11-04"))
        assertEquals(d("2026-11-02") to d("2026-11-08"), period(monday, "2026-11-02"))
        assertEquals(d("2026-11-02") to d("2026-11-08"), period(monday, "2026-11-08"))
        assertEquals(d("2026-11-09") to d("2026-11-15"), period(monday, "2026-11-09"))
    }

    @Test
    fun weeklyAcrossYearBoundaryAndOtherStartDay() {
        assertEquals(d("2026-12-28") to d("2027-01-03"), period(BudgetPeriodPolicy(WEEKLY, 1), "2027-01-01"))
        assertEquals(d("2026-11-01") to d("2026-11-07"), period(BudgetPeriodPolicy(WEEKLY, 7), "2026-11-04"))
    }

    @Test
    fun monthlyStartingOnThe25thCrossesMonthEnd() {
        val payday = BudgetPeriodPolicy(MONTHLY, 25)
        assertEquals(d("2025-12-25") to d("2026-01-24"), period(payday, "2026-01-24"))
        assertEquals(d("2026-01-25") to d("2026-02-24"), period(payday, "2026-01-25"))
        assertEquals(d("2025-12-25") to d("2026-01-24"), period(payday, "2025-12-31"))
        assertEquals(d("2026-02-25") to d("2026-03-24"), period(payday, "2026-02-28"))
        assertEquals(d("2026-02-25") to d("2026-03-24"), period(payday, "2026-03-01"))
    }

    @Test
    fun monthlyStartingOnTheFirstFollowsCalendarMonths() {
        assertEquals(d("2028-02-01") to d("2028-02-29"), period(BudgetPeriodPolicy(MONTHLY, 1), "2028-02-15"))
    }

    @Test
    fun invalidStartDayIsRejected() {
        assertFailsWith<IllegalArgumentException> { BudgetPeriodPolicy(WEEKLY, 0) }
        assertFailsWith<IllegalArgumentException> { BudgetPeriodPolicy(WEEKLY, 8) }
        assertFailsWith<IllegalArgumentException> { BudgetPeriodPolicy(MONTHLY, 29) }
    }
}
