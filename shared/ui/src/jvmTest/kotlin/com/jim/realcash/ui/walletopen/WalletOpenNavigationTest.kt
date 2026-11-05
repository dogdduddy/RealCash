package com.jim.realcash.ui.walletopen

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.model.Denomination.*
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.domain.util.SequentialIdGenerator
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.AppState
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.RealCashRoot
import com.jim.realcash.ui.theme.RealCashTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.*
import kotlinx.datetime.LocalDate

class WalletOpenNavigationTest {
    @Test
    fun longPressFrontBillDropReturnAndPaymentEntryWork() = runBlocking {
        val driver = DriverFactory().createDriver()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val clock = object : Clock { override fun now() = Instant.parse("2026-11-04T09:00:00Z") }
        var pickups = 0
        var successes = 0
        var taps = 0
        val haptics = object : Haptics {
            override fun pickUp() { pickups++ }
            override fun success() { successes++ }
            override fun tap() { taps++ }
        }
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator(), EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W50000 to 1, W10000 to 3, W5000 to 1, W1000 to 1, C500 to 1), LocalDate(2026, 11, 1))
            val app = AppState(AppEnvironment(data, haptics, clock = clock), scope)
            app.openWallet()
            val scene = ImageComposeScene(780, 1688, Density(2f), coroutineContext = coroutineContext) { RealCashTheme { RealCashRoot(app) } }
            var time = 0L
            fun settle() { repeat(35) { scene.render(time); time += 50_000_000L } }
            fun pointer(type: PointerEventType, point: Offset) { scene.sendPointerEvent(type, point * 2f) }
            fun tap(point: Offset) { pointer(PointerEventType.Press, point); pointer(PointerEventType.Release, point); settle() }
            suspend fun drag(from: Offset, to: Offset, expectPick: Boolean = true, onDropped: () -> Unit = {}) {
                pointer(PointerEventType.Press, from)
                delay(450)
                scene.render(time); time += 50_000_000L
                assertEquals(expectPick, app.walletOpen.physics.picked != null)
                pointer(PointerEventType.Move, to)
                scene.render(time); time += 50_000_000L
                pointer(PointerEventType.Release, to)
                onDropped()
                settle()
            }
            try {
                settle()
                val layout = WalletOpenLayout.calculate(390f, 844f)
                val group = pocketBillGroups(mapOf(W50000 to 1, W10000 to 3, W5000 to 1, W1000 to 1), layout.pocket)[1]
                val point = Offset(group.left + 1.5f, group.top + group.exposedHeight / 2)
                val frontId = data.wallet.state.value.inWallet.last { it.denomination == W10000 }.id
                // A short tap and a leather long press cannot take paper out.
                tap(point)
                assertEquals(Won.ZERO, app.walletOpen.ui.value.handTotal)
                drag(Offset(layout.wallet.left + 2f, layout.wallet.center.y), layout.trayInner.center, expectPick = false)
                assertEquals(Won.ZERO, app.walletOpen.ui.value.handTotal)
                drag(point, layout.trayInner.center)
                assertEquals(Won(10_000), app.walletOpen.ui.value.handTotal)
                assertEquals(frontId, app.walletOpen.ui.value.inHand.single().id)
                delay(350); settle()
                assertEquals(listOf(frontId), data.wallet.state.value.activeHand?.unitIds)
                assertEquals(1, successes)
                // Off-tray release springs back to the original tray slot without changing the hand.
                val trayPoint = traySlots(app.walletOpen.ui.value.inHand, layout.trayInner, emptyMap()).getValue(frontId).center
                drag(trayPoint, Offset(5f, 280f))
                assertEquals(listOf(frontId), data.wallet.state.value.activeHand?.unitIds)
                assertNull(app.walletOpen.physics.picked)
                drag(trayPoint, layout.wallet.center)
                delay(350); settle()
                assertNull(data.wallet.state.value.activeHand)
                assertEquals(Won.ZERO, app.walletOpen.ui.value.handTotal)
                assertEquals(3, pickups)
                assertEquals(2, successes)
                assertTrue(taps >= 2)
                // A coin stays at the precise release point through saving and can return to its scatter slot.
                val coin = data.wallet.state.value.inWallet.single { it.denomination == C500 }
                val coinPosition = CoinScatter.place(app.walletOpen.seed, 1, layout.coins).single()
                val coinSource = Offset(coinPosition.x, coinPosition.y)
                val coinDrop = Offset(layout.trayInner.right - 25f, layout.trayInner.top + 25f)
                drag(coinSource, coinDrop, onDropped = {
                    assertEquals(coinDrop, app.walletOpen.physics.flight?.target?.center)
                    assertEquals(0f, app.walletOpen.physics.flight?.rotation)
                })
                delay(350); settle()
                assertEquals(listOf(coin.id), data.wallet.state.value.activeHand?.unitIds)
                drag(coinDrop, coinSource)
                delay(350); settle()
                assertNull(data.wallet.state.value.activeHand)
                assertTrue(app.walletOpen.ui.value.inHand.isEmpty())
                tap(Offset(348f, layout.tray.bottom + 22f))
                assertTrue(app.walletOpen.paymentInfoOpen.value)
                tap(Offset(292f, 490f))
                assertTrue(app.manualSheetOpen)
                assertFalse(app.walletOpen.paymentInfoOpen.value)
                assertEquals(Won.ZERO, data.wallet.state.value.inHand.total())
            } finally { scene.close() }
        } finally { scope.cancel(); driver.close() }
    }
}
