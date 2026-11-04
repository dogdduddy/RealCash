package com.jim.realcash.ui.wallet

import com.jim.realcash.data.AppSettings
import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.SequentialIdGenerator
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.AppState
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.Screen
import com.jim.realcash.ui.Strings
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class WalletHomeModelTest {
    private val now = Instant.parse("2026-11-04T09:00:00Z")
    private val clock = object : Clock { override fun now(): Instant = now }
    private val zone = TimeZone.of("Asia/Seoul")
    private val periodStart = LocalDate(2026, 11, 1)

    @Test
    fun monthlyDefaultAndSavedWeeklyPolicyUseDomainPeriods() {
        val monthly = WalletHomeUi.from(WalletState.EMPTY, AppSettings.DEFAULT, now, zone, true)
        assertEquals(Strings.THIS_MONTH, monthly.periodLabel)
        assertEquals("11.1 – 11.30", monthly.periodRange)
        assertEquals(26, monthly.daysLeft)
        val weekly = WalletHomeUi.from(
            WalletState.EMPTY,
            AppSettings(budgetPeriodPolicy = BudgetPeriodPolicy(BudgetPeriodPolicy.Cycle.WEEKLY, 1)),
            now, zone, true,
        )
        assertEquals(Strings.THIS_WEEK, weekly.periodLabel)
        assertEquals("11.2 – 11.8", weekly.periodRange)
        assertEquals(4, weekly.daysLeft)
    }

    @Test
    fun thicknessBoundariesAndHandBalanceFollowRepositoryState() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator(), EmptyCoroutineContext)
            for ((bills, layers) in listOf(0 to 0, 1 to 1, 5 to 1, 6 to 2, 25 to 5, 26 to 6, 30 to 6)) {
                val empty = WalletHomeUi.from(WalletState.EMPTY, AppSettings.DEFAULT, now, zone, true)
                assertEquals(layers, empty.copy(billCount = bills).sideLayers)
            }
            data.wallet.receiveAllowance(mapOf(W10000 to 6, C500 to 2), periodStart)
            data.wallet.moveToHand(data.wallet.state.value.inWallet.filter { it.denomination == W10000 }.take(2).map { it.id })
            val home = WalletHomeUi.from(data.wallet.state.value, AppSettings.DEFAULT, now, zone, true)
            assertEquals(Won(61_000), home.balance)
            assertEquals(Won(20_000), home.handTotal)
            assertEquals(4, home.billCount)
            assertEquals(1, home.sideLayers)
            assertFalse(home.walletEmpty)
            data.wallet.returnHand(com.jim.realcash.domain.ledger.ReturnReason.USER)
            assertEquals(2, WalletHomeUi.from(data.wallet.state.value, AppSettings.DEFAULT, now, zone, true).sideLayers)
        } finally {
            driver.close()
        }
    }

    @Test
    fun coinOnlyWalletIsNonemptyWithoutSideLayers() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator(), EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(C500 to 30), periodStart)
            val home = WalletHomeUi.from(data.wallet.state.value, AppSettings.DEFAULT, now, zone, true)
            assertFalse(home.walletEmpty)
            assertEquals(0, home.sideLayers)
        } finally {
            driver.close()
        }
    }

    @Test
    fun walletTapHapticsAndPushedDestinationsReturnToHome() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            var taps = 0
            val haptics = object : Haptics {
                override fun tap() { taps++ }
                override fun success() {}
                override fun pickUp() {}
            }
            val data = RealCashData(driver, clock, SequentialIdGenerator(), EmptyCoroutineContext)
            val app = AppState(AppEnvironment(data, haptics, clock = clock, timeZone = zone), backgroundScope)
            app.openWallet()
            app.openWallet()
            assertEquals(1, taps)
            assertEquals(Screen.WalletOpen, app.current)
            assertTrue(app.back())
            for (destination in listOf(Screen.Ledger, Screen.Settings, Screen.ReceiveAllowance, Screen.Exchange, Screen.NotificationSimulator)) {
                app.navigate(destination)
                assertEquals(destination, app.current)
                assertTrue(app.back())
                assertEquals(Screen.WalletHome, app.current)
            }
            assertFalse(app.back())
        } finally {
            driver.close()
        }
    }

    @Test
    fun newSettlementShowsOneCompactMessageWithoutReplayingHistory() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator(), EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W10000 to 2), periodStart)
            val env = AppEnvironment(data, Haptics.None, clock = clock, timeZone = zone)
            val messages = mutableListOf<String>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.messages.messages.collect { messages += it } }
            val model = WalletHomeModel(backgroundScope, env)
            runCurrent()
            val payment = DetectedPayment(PaymentId("settled"), Won(6_500), "카페", "manual", "", now, 1f, false)
            data.wallet.recordDetectedPayment(payment)
            runCurrent()
            assertEquals(1, model.ui.value.pendingCount)
            data.wallet.settle(payment.id, PullPolicy.LARGEST_FIRST)
            runCurrent()
            assertEquals(listOf("₩6,500 정산 완료"), messages)
            assertEquals(0, model.ui.value.pendingCount)
            WalletHomeModel(backgroundScope, env)
            runCurrent()
            assertEquals(1, messages.size)
        } finally {
            driver.close()
        }
    }
}
