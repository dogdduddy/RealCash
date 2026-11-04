package com.jim.realcash.ui.settings

import com.jim.realcash.data.AppSettings
import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.domain.model.BudgetPeriodPolicy.Cycle.MONTHLY
import com.jim.realcash.domain.model.BudgetPeriodPolicy.Cycle.WEEKLY
import com.jim.realcash.domain.model.BudgetPeriodPolicy.Cycle.MONTHLY
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.parser.DefaultRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class SettingsUiTest {
    @Test
    fun defaultsMapToTheScreen() {
        val ui = SettingsUi.from(AppSettings.DEFAULT)
        assertEquals(MONTHLY, ui.cycle)
        assertEquals(1, ui.startDay)
        assertEquals(PullPolicy.LARGEST_FIRST, ui.pullPolicy)
        assertEquals(6, ui.ttlHours)
        assertEquals(0.8f, ui.threshold)
        assertTrue(ui.samsungEnabled)
        assertTrue(ui.tossEnabled)
        assertTrue(ui.otherPackages.isEmpty())
    }

    @Test
    fun customValuesMap() {
        val s = AppSettings(
            budgetPeriodPolicy = BudgetPeriodPolicy(MONTHLY, 25),
            pullPolicy = PullPolicy.EXACT_IF_POSSIBLE,
            handTtl = 90.minutes,
            autoSettleThreshold = 0.3f,
            enabledSourceApps = setOf(DefaultRules.TOSS, "com.z.card", "com.a.card"),
        )
        val ui = SettingsUi.from(s)
        assertEquals(MONTHLY, ui.cycle)
        assertEquals(25, ui.startDay)
        assertNull(ui.ttlHours, "90 minutes is not an option")
        assertEquals(0.5f, ui.threshold, "clamped to the slider range")
        assertFalse(ui.samsungEnabled)
        assertEquals(listOf("com.a.card", "com.z.card"), ui.otherPackages)
    }

    @Test
    fun thresholdSnapsToGrid() {
        assertEquals(0.85f, SettingsRules.snapThreshold(0.8634f))
        assertEquals(0.5f, SettingsRules.snapThreshold(0.12f))
        assertEquals(1.0f, SettingsRules.snapThreshold(1.4f))
        assertEquals(0.75f, SettingsRules.snapThreshold(0.7499f))
        assertEquals(9, SettingsRules.THRESHOLD_SLIDER_STEPS)
        val stops = (0..10).map { SettingsRules.THRESHOLD_MIN + it * SettingsRules.THRESHOLD_STEP }
        assertEquals(SettingsRules.THRESHOLD_SLIDER_STEPS + 2, stops.size)
    }

    @Test
    fun periodRules() {
        val weekly = BudgetPeriodPolicy(WEEKLY, 3)
        assertEquals(weekly, SettingsRules.withCycle(weekly, WEEKLY))
        assertEquals(BudgetPeriodPolicy(MONTHLY, 1), SettingsRules.withCycle(weekly, MONTHLY))
        assertEquals(BudgetPeriodPolicy(WEEKLY, 7), SettingsRules.withStartDay(weekly, 9))
        assertEquals(BudgetPeriodPolicy(MONTHLY, 28), SettingsRules.withStartDay(BudgetPeriodPolicy(MONTHLY, 1), 31))
        assertEquals(BudgetPeriodPolicy(MONTHLY, 1), SettingsRules.withStartDay(BudgetPeriodPolicy(MONTHLY, 2), 0))
    }

    @Test
    fun ttlOptions() {
        assertEquals(listOf(1, 3, 6, 12, 24), SettingsRules.TTL_HOURS)
        assertEquals(12, SettingsRules.ttlOption(12.hours))
        assertNull(SettingsRules.ttlOption(2.hours))
    }

    @Test
    fun sourceAppsAndPackageValidation() {
        val apps = setOf(DefaultRules.SAMSUNG_WALLET)
        assertEquals(setOf(DefaultRules.SAMSUNG_WALLET, DefaultRules.TOSS), SettingsRules.toggle(apps, DefaultRules.TOSS, true))
        assertEquals(emptySet(), SettingsRules.toggle(apps, DefaultRules.SAMSUNG_WALLET, false))
        assertEquals(PackageInput.Empty, SettingsRules.validatePackage("  ", apps))
        assertEquals(PackageInput.Invalid, SettingsRules.validatePackage("shinhan", apps))
        assertEquals(PackageInput.Invalid, SettingsRules.validatePackage("com.shin han", apps))
        assertEquals(PackageInput.Invalid, SettingsRules.validatePackage("1com.card", apps))
        assertEquals(PackageInput.Duplicate, SettingsRules.validatePackage(DefaultRules.SAMSUNG_WALLET, apps))
        assertEquals(PackageInput.Valid("com.shinhan.sbanking"), SettingsRules.validatePackage(" com.shinhan.sbanking ", apps))
    }
}
