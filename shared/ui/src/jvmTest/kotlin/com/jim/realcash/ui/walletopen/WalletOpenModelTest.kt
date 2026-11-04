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
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
    fun dropsDuringWriteSaveLatestTrayInOrderBeforeNavigation() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val writes = PausedWalletWrites(StandardTestDispatcher(testScheduler))
            val data = RealCashData(driver, clock, FailingIds(), writes)
            data.wallet.receiveAllowance(mapOf(W10000 to 2, W5000 to 1), LocalDate(2026, 11, 1))
            data.settings.load()
            val model = WalletOpenModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.ui.collect {} }
            val units = data.wallet.state.value.inWallet
            val expected = listOf(units[2].id, units[1].id)
            var navigations = 0
            model.setOnTray(units[0].id, true)
            writes.paused = true
            model.confirm {
                assertEquals(expected, data.wallet.state.value.activeHand?.unitIds)
                navigations++
            }
            runCurrent()
            assertTrue(model.submitting.value)
            assertTrue(data.wallet.state.value.inHand.isEmpty(), "the first write is suspended")
            model.setOnTray(units[2].id, true)
            model.setOnTray(units[0].id, false)
            model.setOnTray(units[1].id, true)
            runCurrent()
            assertEquals(expected, model.ui.value.inHand.map { it.id })
            // Even if another debounce would have elapsed, the in-flight snapshot must survive.
            advanceTimeBy(500); runCurrent()
            assertEquals(0, navigations)
            writes.resume()
            runCurrent()
            assertEquals(expected, data.wallet.state.value.activeHand?.unitIds)
            assertEquals(expected, model.ui.value.inHand.map { it.id })
            assertEquals(2, data.wallet.history.value.filterIsInstance<MovedToHand>().size)
            assertEquals(1, navigations)
            assertFalse(model.submitting.value)
            assertFalse(model.ui.value.changed)
            assertTrue(data.wallet.state.value.invariantViolations().isEmpty())
            val loaded = RealCashData(driver, clock, FailingIds(), EmptyCoroutineContext).wallet.load()
            assertEquals(expected, loaded.activeHand?.unitIds)
        } finally { driver.close() }
    }

    @Test
    fun returningLastUnitDuringWriteSavesEmptyTrayAndThenNavigates() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val writes = PausedWalletWrites(StandardTestDispatcher(testScheduler))
            val data = RealCashData(driver, clock, FailingIds(), writes)
            data.wallet.receiveAllowance(mapOf(W10000 to 1), LocalDate(2026, 11, 1))
            data.settings.load()
            val model = WalletOpenModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock))
            val id = data.wallet.state.value.inWallet.single().id
            model.setOnTray(id, true)
            writes.paused = true
            model.confirm()
            runCurrent()
            assertTrue(model.submitting.value)
            model.setOnTray(id, false)
            var navigations = 0
            model.confirm { navigations++ }
            writes.resume()
            runCurrent()
            assertEquals(null, data.wallet.state.value.activeHand)
            assertEquals(1, navigations)
            assertFalse(model.submitting.value)
        } finally { driver.close() }
    }

    @Test
    fun queuedNoOpDoesNotWriteAgainAndReorderingDoes() = runTest {
        for (reorder in listOf(false, true)) {
            val driver = DriverFactory().createDriver()
            try {
                val writes = PausedWalletWrites(StandardTestDispatcher(testScheduler))
                val data = RealCashData(driver, clock, FailingIds(), writes)
                data.wallet.receiveAllowance(mapOf(W10000 to 2), LocalDate(2026, 11, 1))
                data.settings.load()
                val model = WalletOpenModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock))
                backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.ui.collect {} }
                val ids = data.wallet.state.value.inWallet.map { it.id }
                ids.forEach { model.setOnTray(it, true) }
                writes.paused = true
                var navigations = 0
                model.confirm { navigations++ }
                runCurrent()
                assertTrue(model.submitting.value)
                if (reorder) model.setOnTray(ids.first(), false)
                model.setOnTray(ids.first(), true)
                writes.resume()
                runCurrent()
                assertEquals(if (reorder) ids.reversed() else ids, data.wallet.state.value.activeHand?.unitIds)
                assertEquals(data.wallet.state.value.activeHand?.unitIds, model.ui.value.inHand.map { it.id })
                assertEquals(if (reorder) 2 else 1, data.wallet.history.value.filterIsInstance<MovedToHand>().size)
                assertEquals(1, navigations)
                assertFalse(model.submitting.value)
                assertFalse(model.ui.value.changed)
            } finally { driver.close() }
        }
    }

    @Test
    fun queuedDropSurvivesPartialReplacementFailureWithoutNavigating() = runTest {
        val driver = DriverFactory().createDriver()
        try {
            val writes = PausedWalletWrites(StandardTestDispatcher(testScheduler))
            val ids = FailingIds()
            val data = RealCashData(driver, clock, ids, writes)
            data.wallet.receiveAllowance(mapOf(W10000 to 1, W5000 to 1, C500 to 1), LocalDate(2026, 11, 1))
            data.settings.load()
            val units = data.wallet.state.value.inWallet
            data.wallet.moveToHand(listOf(units[0].id))
            val model = WalletOpenModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock))
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.ui.collect {} }
            model.setOnTray(units[1].id, true)
            writes.paused = true
            var navigations = 0
            model.confirm { navigations++ }
            runCurrent() // returnHand is waiting for its database append
            assertTrue(model.submitting.value)
            ids.failAt = ids.n + 1 // fail the following replacement's hand id
            model.setOnTray(units[2].id, true)
            writes.resume()
            runCurrent()
            assertEquals(listOf(units[2].id), data.wallet.state.value.activeHand?.unitIds)
            assertEquals(listOf(units[2].id), model.ui.value.inHand.map { it.id })
            assertEquals(0, navigations)
            assertFalse(model.submitting.value)
            assertFalse(model.ui.value.changed)
            assertTrue(data.wallet.state.value.invariantViolations().isEmpty())
        } finally { driver.close() }
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

/** Stops a database write after its hand snapshot has been built, without blocking a thread. */
internal class PausedWalletWrites(private val delegate: CoroutineDispatcher) : CoroutineDispatcher() {
    var paused = false
    private val pending = ArrayDeque<Pair<CoroutineContext, Runnable>>()
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (paused) pending.addLast(context to block) else delegate.dispatch(context, block)
    }
    fun resume() {
        paused = false
        while (pending.isNotEmpty()) {
            val (context, block) = pending.removeFirst()
            delegate.dispatch(context, block)
        }
    }
}

