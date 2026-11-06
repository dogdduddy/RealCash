package com.jim.realcash.ui.allowance

import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.ledger.AllowanceReceived
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.SequentialIdGenerator
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.UiMessages
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
import kotlinx.datetime.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class ReceiveAllowanceModelTest {
    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-11-06T09:00:00Z")
    }

    @Test
    fun submitStoresTheEditedSplitWithoutRegroupingAndReportsItsSum() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator("allowance-"), EmptyCoroutineContext)
            val messages = UiMessages()
            val receivedMessages = mutableListOf<String>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
                messages.messages.collect { receivedMessages.add(it) }
            }
            var doneCount = 0
            val model = ReceiveAllowanceModel(
                backgroundScope,
                AppEnvironment(data, Haptics.None, messages, clock, TimeZone.of("Asia/Seoul")),
                onDone = { doneCount++ },
            )
            model.onAmountChange("30000")
            model.increment(W10000)
            model.increment(W10000) // Five 10,000 bills must stay five bills, rather than one 50,000 bill.
            val split = model.draft.value.split()
            assertEquals(mapOf(W10000 to 5), split)
            assertTrue(model.draft.value.isValid)

            model.submit()
            model.submit() // A second tap while saving must not duplicate the allowance.
            assertTrue(model.submitting.value)
            runCurrent()

            assertFalse(model.submitting.value)
            assertEquals(1, doneCount)
            assertEquals(listOf("₩50,000을 지갑에 넣었어요"), receivedMessages)
            val event = data.wallet.history.value.filterIsInstance<AllowanceReceived>().single()
            assertEquals(split, event.units.groupingBy { it.denomination }.eachCount())
            assertEquals(Won(50_000), data.wallet.state.value.balance)

            val reloaded = RealCashData(driver, clock, SequentialIdGenerator("reload-"), EmptyCoroutineContext)
            assertEquals(split, reloaded.wallet.load().inWallet.groupingBy { it.denomination }.eachCount())
        } finally {
            driver.close()
        }
    }

    @Test
    fun submitDoesNothingAfterRemovingTheLastBill() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator("allowance-"), EmptyCoroutineContext)
            var done = false
            val model = ReceiveAllowanceModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock)) { done = true }
            model.onAmountChange("10000")
            model.decrement(W10000)
            model.submit()
            runCurrent()

            assertFalse(model.draft.value.isValid)
            assertFalse(model.submitting.value)
            assertFalse(done)
            assertTrue(data.wallet.history.value.isEmpty())
            assertEquals(Won.ZERO, data.wallet.state.value.balance)
        } finally {
            driver.close()
        }
    }
}
