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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate

class WalletOpenNavigationTest {
    @Test
    fun exposedBillAndTrayTapsConfirmAndManualPaymentEntryWork() = runBlocking {
        val driver = DriverFactory().createDriver()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val clock = object : Clock { override fun now() = Instant.parse("2026-11-04T09:00:00Z") }
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator(), EmptyCoroutineContext)
            data.wallet.receiveAllowance(mapOf(W50000 to 1, W10000 to 1, W5000 to 1, W1000 to 1, C500 to 1), LocalDate(2026, 11, 1))
            val app = AppState(AppEnvironment(data, Haptics.None, clock = clock), scope)
            app.openWallet()
            val scene = ImageComposeScene(780, 1688, Density(2f)) { RealCashTheme { RealCashRoot(app) } }
            var time = 0L
            fun settle() { repeat(30) { scene.render(time); time += 50_000_000L } }
            fun tap(x: Float, y: Float) {
                scene.sendPointerEvent(PointerEventType.Press, Offset(x * 2f, y * 2f))
                scene.sendPointerEvent(PointerEventType.Release, Offset(x * 2f, y * 2f))
                settle()
            }
            try {
                settle()
                val layout = WalletOpenLayout.calculate(390f, 844f)
                val group = pocketBillGroups(mapOf(W50000 to 1, W10000 to 1, W5000 to 1, W1000 to 1), layout.pocket)[1]
                tap(group.left + group.billWidth / 2, group.top + group.exposedHeight / 2)
                assertEquals(Won(10_000), app.walletOpen.ui.value.handTotal)
                assertTrue(data.wallet.state.value.inHand.isEmpty())
                // Tapping the leather below the lip does not select a covered bill.
                tap(group.left + group.billWidth / 2, layout.pocket.top + 20f)
                assertEquals(Won(10_000), app.walletOpen.ui.value.handTotal)
                tap(layout.trayInner.left + 50f, layout.trayInner.top + layout.trayInner.height / 2)
                assertEquals(Won.ZERO, app.walletOpen.ui.value.handTotal)
                tap(group.left + group.billWidth / 2, group.top + group.exposedHeight / 2)
                tap(264f, layout.tray.bottom + 24f)
                assertEquals(Won(10_000), data.wallet.state.value.inHand.total())
                tap(348f, layout.tray.bottom + 22f)
                assertTrue(app.walletOpen.paymentInfoOpen.value)
                tap(292f, 490f)
                assertTrue(app.manualSheetOpen)
                assertTrue(!app.walletOpen.paymentInfoOpen.value)
            } finally { scene.close() }
        } finally { scope.cancel(); driver.close() }
    }
}
