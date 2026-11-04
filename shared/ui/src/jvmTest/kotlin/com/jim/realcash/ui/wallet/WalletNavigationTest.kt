package com.jim.realcash.ui.wallet

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.SequentialIdGenerator
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.AppState
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.RealCashRoot
import com.jim.realcash.ui.Screen
import com.jim.realcash.ui.theme.RealCashTheme
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** Pointer-driven checks of the menu, wallet and confirmation entry points, without new test dependencies. */
class WalletNavigationTest {
    private val now = Instant.parse("2026-11-04T09:00:00Z")
    private val clock = object : Clock { override fun now(): Instant = now }
    private val period = LocalDate(2026, 11, 1)

    private fun withHome(empty: Boolean = false, pending: Boolean = false, test: (AppState, Harness) -> Unit) = runBlocking {
        val driver = DriverFactory().createDriver()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator(), EmptyCoroutineContext)
            if (!empty) data.wallet.receiveAllowance(mapOf(W10000 to 1), period)
            if (pending) data.wallet.recordDetectedPayment(DetectedPayment(PaymentId("pending"), Won(4_800), "카페", "manual", "", now, 0.5f, false))
            val app = AppState(AppEnvironment(data, Haptics.None, clock = clock, timeZone = TimeZone.of("Asia/Seoul")), scope)
            app.start()
            val scene = ImageComposeScene(780, 1688, Density(2f)) { RealCashTheme { RealCashRoot(app, showDebugShortcut = true) } }
            try {
                val harness = Harness(scene)
                harness.settle()
                test(app, harness)
            } finally {
                scene.close()
            }
        } finally {
            scope.cancel()
            driver.close()
        }
    }

    @Test
    fun allMenuDestinationsOpenAndPushedScreensHaveWorkingBackButtons() {
        val destinations = listOf(Screen.ReceiveAllowance, Screen.Exchange, null, Screen.Ledger, Screen.Settings, Screen.NotificationSimulator)
        destinations.forEachIndexed { index, destination ->
            withHome { app, scene ->
                scene.tap(354f, 32f)
                scene.settle()
                scene.tap(320f, 88f + 48f * index)
                scene.settle()
                if (destination == null) {
                    assertTrue(app.manualSheetOpen, "direct entry opens the existing manual sheet")
                    app.closeManualSheet()
                } else {
                    assertEquals(destination, app.current)
                    scene.tap(28f, 32f)
                    scene.settle()
                    assertEquals(Screen.WalletHome, app.current)
                }
                assertFalse(app.canGoBack)
            }
        }
    }

    @Test
    fun walletTapThenVisibleBackClosesAndCanReopen() = withHome { app, scene ->
        repeat(2) {
            scene.tap(195f, 440f)
            scene.settle()
            assertEquals(Screen.WalletOpen, app.current)
            scene.tap(28f, 32f)
            scene.settle()
            assertEquals(Screen.WalletHome, app.current)
        }
    }

    @Test
    fun pendingBadgeOpensExistingConfirmationSheet() = withHome(pending = true) { app, scene ->
        scene.tap(195f, 312f)
        scene.settle()
        assertTrue(app.confirmSheetOpen)
    }

    @Test
    fun emptyWalletAllowanceButtonOpensReceiveAllowance() = withHome(empty = true) { app, scene ->
        scene.tap(195f, 600f)
        scene.settle()
        assertEquals(Screen.ReceiveAllowance, app.current)
    }

    private class Harness(private val scene: ImageComposeScene) {
        private var time = 0L
        fun settle() {
            repeat(12) {
                scene.render(time)
                time += 50_000_000L
            }
        }
        fun tap(x: Float, y: Float) {
            val point = Offset(x * 2, y * 2)
            scene.sendPointerEvent(PointerEventType.Press, point)
            scene.sendPointerEvent(PointerEventType.Release, point)
        }
    }
}
