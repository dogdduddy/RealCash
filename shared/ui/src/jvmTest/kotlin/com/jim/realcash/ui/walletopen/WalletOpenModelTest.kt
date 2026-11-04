package com.jim.realcash.ui.walletopen

import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.ledger.MovedToHand
import com.jim.realcash.domain.model.Denomination.*
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.domain.util.IdGenerator
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.AppState
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.Screen
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class WalletOpenModelTest {
    private val clock = object : Clock { override fun now() = Instant.parse("2026-11-04T09:00:00Z") }
    private class FailingIds : IdGenerator {
        var n = 0
        var failAt = -1
        override fun next(): String { n++; check(n != failAt) { "저장 실패" }; return "open-$n" }
    }

    @Test
    fun tapsAreLocalAndConfirmWritesExactlyOneHandDespiteRepeatedTaps() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val data = RealCashData(driver, clock, FailingIds(), EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W10000 to 1, W5000 to 1, C500 to 1), LocalDate(2026, 11, 1))
            val model = WalletOpenModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.ui.collect {} }
            data.wallet.state.value.inWallet.forEach { model.toggle(it.id) }
            runCurrent()
            assertEquals(Won(15_500), model.ui.value.handTotal)
            assertTrue(data.wallet.state.value.inHand.isEmpty())
            model.confirm()
            model.confirm()
            assertTrue(model.submitting.value)
            runCurrent()
            assertFalse(model.submitting.value)
            assertEquals(1, data.wallet.history.value.filterIsInstance<MovedToHand>().size)
            assertEquals(Won(15_500), data.wallet.state.value.inHand.total())
            assertFalse(model.ui.value.changed)
            model.confirm()
            runCurrent()
            assertEquals(1, data.wallet.history.value.filterIsInstance<MovedToHand>().size)
            val loaded = RealCashData(driver, clock, FailingIds(), EmptyCoroutineContext).wallet.load()
            assertEquals(Won(15_500), loaded.inHand.total())
        } finally { driver.close() }
    }

    @Test
    fun backSavesDraftAndNextVisitCreatesFreshModelThenCanReturnEveryUnit() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val data = RealCashData(driver, clock, FailingIds(), EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W10000 to 1, C500 to 1), LocalDate(2026, 11, 1))
            val app = AppState(AppEnvironment(data, Haptics.None, clock = clock), backgroundScope)
            app.openWallet()
            val first = app.walletOpen
            val id = data.wallet.state.value.inWallet.first().id
            first.toggle(id)
            assertTrue(app.back())
            assertEquals(Screen.WalletOpen, app.current) // navigation waits for the save
            runCurrent()
            assertEquals(Screen.WalletHome, app.current)
            assertEquals(listOf(id), data.wallet.state.value.activeHand?.unitIds)
            app.openWallet()
            assertNotSame(first, app.walletOpen)
            app.walletOpen.toggle(id)
            app.back()
            runCurrent()
            assertTrue(data.wallet.state.value.inHand.isEmpty())
            assertEquals(Screen.WalletHome, app.current)
        } finally { driver.close() }
    }

    @Test
    fun replacementFailureRestoresRepositoryStateAndDoesNotNavigate() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val ids = FailingIds()
            val data = RealCashData(driver, clock, ids, EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W10000 to 1, W5000 to 1), LocalDate(2026, 11, 1))
            val ten = data.wallet.state.value.inWallet.first { it.denomination == W10000 }.id
            val five = data.wallet.state.value.inWallet.first { it.denomination == W5000 }.id
            data.wallet.moveToHand(listOf(ten))
            val app = AppState(AppEnvironment(data, Haptics.None, clock = clock), backgroundScope)
            app.openWallet()
            val model = app.walletOpen
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.ui.collect {} }
            val messages = mutableListOf<String>()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { app.env.messages.messages.collect { messages += it } }
            model.toggle(five)
            ids.failAt = ids.n + 2 // return succeeds; new hand id fails
            app.back()
            runCurrent()
            assertEquals(Screen.WalletOpen, app.current)
            assertTrue(data.wallet.state.value.inHand.isEmpty())
            assertEquals(Won.ZERO, model.ui.value.handTotal)
            assertEquals(1, messages.size)
            assertTrue(messages.single().contains("되돌렸어요"))
            assertFalse(model.submitting.value)
            app.back()
            runCurrent()
            assertEquals(Screen.WalletHome, app.current)
            assertEquals(Won.ZERO, data.wallet.state.value.inHand.total())
            assertTrue(data.wallet.state.value.invariantViolations().isEmpty())
        } finally { driver.close() }
    }
    @Test
    fun dropsDebounceWholeTrayReplacementAndEmptyTrayReturnsHand() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val data = RealCashData(driver, clock, FailingIds(), EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W10000 to 2, C500 to 1), LocalDate(2026, 11, 1))
            val model = WalletOpenModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.ui.collect {} }
            val units = data.wallet.state.value.inWallet
            model.setOnTray(units[0].id, true)
            runCurrent()
            advanceTimeBy(299)
            assertTrue(data.wallet.state.value.inHand.isEmpty())
            model.setOnTray(units[1].id, true)
            runCurrent()
            advanceTimeBy(299)
            assertTrue(data.wallet.state.value.inHand.isEmpty())
            advanceTimeBy(1); runCurrent()
            assertEquals(setOf(units[0].id, units[1].id), data.wallet.state.value.activeHand?.unitIds?.toSet())
            assertEquals(model.ui.value.inHand.map { it.id }.toSet(), data.wallet.state.value.activeHand?.unitIds?.toSet())
            assertEquals(1, data.wallet.history.value.filterIsInstance<MovedToHand>().size)
            model.setOnTray(units[0].id, false)
            model.setOnTray(units[2].id, true)
            runCurrent(); advanceTimeBy(300); runCurrent()
            assertEquals(listOf(units[1].id, units[2].id), data.wallet.state.value.activeHand?.unitIds)
            assertEquals(listOf(units[1].id, units[2].id), model.ui.value.inHand.map { it.id })
            assertFalse(model.ui.value.changed)
            model.ui.value.inHand.forEach { model.setOnTray(it.id, false) }
            runCurrent(); advanceTimeBy(300); runCurrent()
            assertTrue(data.wallet.state.value.activeHand == null)
            assertTrue(model.ui.value.inHand.isEmpty())
            assertTrue(data.wallet.state.value.invariantViolations().isEmpty())
        } finally { driver.close() }
    }

}
