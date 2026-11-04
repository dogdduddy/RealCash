package com.jim.realcash.ui.manual

import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.ledger.PaymentDetected
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.IdGenerator
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.AppState
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.ledger.LedgerRow
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class ManualPaymentModelTest {
    private val now = Instant.parse("2026-11-04T09:00:00Z")
    private val clock = object : Clock { override fun now(): Instant = now }
    private val periodStart = LocalDate(2026, 11, 2)

    private class FailingIds : IdGenerator {
        var calls = 0
        var failAt = -1
        override fun next(): String {
            calls++
            check(calls != failAt) { "저장 실패 테스트" }
            return "manual-test-$calls"
        }
    }

    @Test
    fun settlesWithHandAndAppearsInLedgerAfterReloadWithoutSimulator() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val data = RealCashData(driver, clock, FailingIds(), EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W10000 to 1), periodStart)
            data.wallet.moveToHand(data.wallet.state.value.inWallet.map { it.id }, ttl = 6.hours)
            val app = AppState(AppEnvironment(data, Haptics.None, clock = clock, timeZone = TimeZone.of("Asia/Seoul")), backgroundScope)
            app.start()
            app.openManualSheet()
            val model = app.manualPayment
            model.onAmountChange("6,500")
            model.onMerchantChange("  동네 카페  ")
            model.submit()
            model.submit() // Rapid repeated taps before the coroutine runs.
            model.beginSession()
            model.onAmountChange("1000")
            app.closeManualSheet()
            assertTrue(app.manualSheetOpen)
            assertTrue(model.submitting.value)
            runCurrent()
            model.submit() // Completed result is also guarded.
            val result = assertNotNull(model.draft.value.result)
            assertEquals(Won(10_000), result.paid)
            assertEquals(Won(3_500), result.change)
            assertEquals(setOf(SettlementFlag.MANUAL), result.settlement.flags)
            assertEquals(1, data.wallet.state.value.settlements.size)
            assertNull(data.wallet.state.value.activeHand)
            assertTrue(data.wallet.state.value.pendingPayments.isEmpty())
            val payment = data.wallet.history.value.filterIsInstance<PaymentDetected>().single().payment
            assertEquals("manual", payment.sourceApp)
            assertEquals("", payment.rawText)
            assertEquals(1f, payment.confidence)
            assertFalse(payment.isRefund)
            assertEquals(now, payment.detectedAt)
            assertEquals("동네 카페", payment.merchant)
            val reloadedData = RealCashData(driver, clock, FailingIds(), EmptyCoroutineContext)
            reloadedData.wallet.load()
            val reloadedApp = AppState(AppEnvironment(reloadedData, Haptics.None, clock = clock, timeZone = TimeZone.of("Asia/Seoul")), backgroundScope)
            val row = reloadedApp.ledger.ui.value.days.flatMap { it.rows }.filterIsInstance<LedgerRow.Payment>().single()
            assertEquals(Won(6_500), row.amount)
            assertEquals("동네 카페", row.merchant)
            assertEquals(setOf(SettlementFlag.MANUAL), row.flags)
            val home = reloadedApp.walletHome.ui.value.lastSettlement
            assertEquals("동네 카페", home?.merchant)
            assertEquals(Won(6_500), home?.amount)
            app.closeManualSheet()
            assertFalse(app.manualSheetOpen)
            app.openManualSheet()
            assertEquals(ManualPaymentDraft(), model.draft.value)
        } finally {
            driver.close()
        }
    }

    @Test
    fun noHandUsesSavedPullPolicyAndAllowsEmptyMerchant() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val data = RealCashData(driver, clock, FailingIds(), EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W10000 to 1, W5000 to 1, W1000 to 1, C500 to 1), periodStart)
            data.settings.setPullPolicy(PullPolicy.EXACT_IF_POSSIBLE)
            val model = ManualPaymentModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock))
            model.onAmountChange("6500")
            model.submit()
            runCurrent()
            val result = assertNotNull(model.draft.value.result)
            assertEquals(Won(6_500), result.paid)
            assertEquals(Won.ZERO, result.change)
            assertEquals(setOf(SettlementFlag.NOT_HAND_PICKED, SettlementFlag.MANUAL), result.settlement.flags)
            assertNull(data.wallet.history.value.filterIsInstance<PaymentDetected>().single().payment.merchant)
        } finally {
            driver.close()
        }
    }

    @Test
    fun settlementFailureShowsSnackbarAndRetriesSameRecordedPayment() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val ids = FailingIds()
            val data = RealCashData(driver, clock, ids, EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W10000 to 1), periodStart)
            val env = AppEnvironment(data, Haptics.None, clock = clock)
            val messages = mutableListOf<String>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { env.messages.messages.collect { messages += it } }
            val model = ManualPaymentModel(backgroundScope, env)
            model.onAmountChange("6500")
            ids.failAt = ids.calls + 3 // payment id, detection event id, then settlement/change id.
            model.submit()
            runCurrent()
            assertFalse(model.submitting.value)
            assertNull(model.draft.value.result)
            assertTrue(model.recorded.value)
            assertTrue(messages.single().contains("저장 실패 테스트"))
            val id = data.wallet.state.value.pendingPayments.keys.single()
            model.beginSession()
            model.onAmountChange("1000")
            model.onMerchantChange("수정 불가")
            assertEquals("6500", model.draft.value.digits)
            assertEquals("", model.draft.value.merchant)
            model.submit()
            runCurrent()
            assertEquals(id, assertNotNull(model.draft.value.result).settlement.paymentId)
            assertEquals(1, data.wallet.history.value.filterIsInstance<PaymentDetected>().size)
            assertEquals(1, data.wallet.state.value.settlements.size)
        } finally {
            driver.close()
        }
    }

    @Test
    fun detectionFailureKeepsDraftEditableAndInvalidDraftDoesNotWrite() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val ids = FailingIds()
            val data = RealCashData(driver, clock, ids, EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W10000 to 1), periodStart)
            val model = ManualPaymentModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock))
            model.submit()
            model.onAmountChange("6505")
            model.submit()
            runCurrent()
            assertTrue(data.wallet.state.value.pendingPayments.isEmpty())
            assertTrue(data.wallet.state.value.settlements.isEmpty())
            model.onAmountChange("6500")
            ids.failAt = ids.calls + 2 // Detection event creation fails before anything is persisted.
            model.submit()
            runCurrent()
            assertFalse(model.recorded.value)
            assertFalse(model.submitting.value)
            model.onAmountChange("5000")
            model.onMerchantChange("다시 입력한 가게")
            model.submit()
            runCurrent()
            assertNotNull(model.draft.value.result)
            val payment = data.wallet.history.value.filterIsInstance<PaymentDetected>().single().payment
            assertEquals(Won(5_000), payment.amount)
            assertEquals("다시 입력한 가게", payment.merchant)
        } finally {
            driver.close()
        }
    }

    @Test
    fun resolvedPendingPaymentDoesNotTrapTheNextSession() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val ids = FailingIds()
            val data = RealCashData(driver, clock, ids, EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W10000 to 1), periodStart)
            val model = ManualPaymentModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock))
            model.onAmountChange("6500")
            ids.failAt = ids.calls + 3
            model.submit()
            runCurrent()
            val id = data.wallet.state.value.pendingPayments.keys.single()
            // Equivalent to confirming the saved pending entry from the separate confirmation sheet.
            data.wallet.settle(id, PullPolicy.LARGEST_FIRST, manual = true)
            model.beginSession()
            assertEquals(ManualPaymentDraft(), model.draft.value)
            assertFalse(model.recorded.value)
            model.onAmountChange("1000")
            assertEquals("1000", model.draft.value.digits)
        } finally {
            driver.close()
        }
    }
}
