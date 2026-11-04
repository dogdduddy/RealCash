package com.jim.realcash.data

import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.parser.DefaultRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.test.runTest

class SettingsRepositoryTest {

    @Test
    fun defaults() = runTest {
        val driver = inMemoryDriver()
        val settings = dataOf(driver).settings.load()
        assertEquals(BudgetPeriodPolicy(BudgetPeriodPolicy.Cycle.WEEKLY, 1), settings.budgetPeriodPolicy)
        assertEquals(PullPolicy.LARGEST_FIRST, settings.pullPolicy)
        assertEquals(6.hours, settings.handTtl)
        assertEquals(0.8f, settings.autoSettleThreshold)
        assertEquals(setOf(DefaultRules.SAMSUNG_WALLET, DefaultRules.TOSS), settings.enabledSourceApps)
        assertEquals(120.seconds, settings.dedupeWindow)
        assertEquals(AppSettings.DEFAULT, settings)
        assertEquals(0L, driver.rowCount("app_setting"), "defaults are not written")
        driver.close()
    }

    @Test
    fun roundTrip() = runTest {
        val driver = inMemoryDriver()
        val repo = dataOf(driver).settings
        repo.setBudgetPeriodPolicy(BudgetPeriodPolicy(BudgetPeriodPolicy.Cycle.MONTHLY, 25))
        repo.setPullPolicy(PullPolicy.EXACT_IF_POSSIBLE)
        repo.setHandTtl(90.minutes)
        repo.setAutoSettleThreshold(0.95f)
        repo.setEnabledSourceApps(setOf("a.b", DefaultRules.TOSS))
        val expected = repo.setDedupeWindow(45.seconds)
        assertEquals(expected, repo.settings.value)

        val reloaded = dataOf(driver).settings.load()
        assertEquals(expected, reloaded)
        assertEquals(
            AppSettings(
                budgetPeriodPolicy = BudgetPeriodPolicy(BudgetPeriodPolicy.Cycle.MONTHLY, 25),
                pullPolicy = PullPolicy.EXACT_IF_POSSIBLE,
                handTtl = 90.minutes,
                autoSettleThreshold = 0.95f,
                enabledSourceApps = setOf("a.b", DefaultRules.TOSS),
                dedupeWindow = 45.seconds,
            ),
            reloaded,
        )
        driver.close()
    }

    @Test
    fun invalidValuesAreRejectedOnWriteAndIgnoredOnRead() = runTest {
        val driver = inMemoryDriver()
        val data = dataOf(driver)
        data.settings.setPullPolicy(PullPolicy.FEWEST_UNITS)
        assertFailsWith<IllegalArgumentException> { data.settings.setAutoSettleThreshold(1.5f) }
        assertFailsWith<IllegalArgumentException> { data.settings.setHandTtl(0.seconds) }
        assertEquals(0.8f, data.settings.settings.value.autoSettleThreshold)

        // Corrupted rows fall back to the default of that field only.
        val store = SettingsStore(data.database, kotlin.coroutines.EmptyCoroutineContext)
        store.put(SettingsRepository.KEY_AUTO_SETTLE_THRESHOLD, "2.5")
        store.put(SettingsRepository.KEY_HAND_TTL, "not a duration")
        store.put(SettingsRepository.KEY_BUDGET_PERIOD, """{"cycle":"WEEKLY","startDay":9}""")
        val reloaded = dataOf(driver).settings.load()
        assertEquals(PullPolicy.FEWEST_UNITS, reloaded.pullPolicy)
        assertEquals(AppSettings.DEFAULT.autoSettleThreshold, reloaded.autoSettleThreshold)
        assertEquals(AppSettings.DEFAULT.handTtl, reloaded.handTtl)
        assertEquals(AppSettings.DEFAULT.budgetPeriodPolicy, reloaded.budgetPeriodPolicy)
        driver.close()
    }
}
