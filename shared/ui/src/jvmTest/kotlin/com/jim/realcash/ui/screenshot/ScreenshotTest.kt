package com.jim.realcash.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.model.Denomination.C100
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.C50
import com.jim.realcash.domain.model.Denomination.C10
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.parser.DefaultRules
import com.jim.realcash.domain.parser.RawNotification
import com.jim.realcash.domain.util.SequentialIdGenerator
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.AppState
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.RealCashRoot
import com.jim.realcash.ui.theme.RealCashTheme
import com.jim.realcash.ui.Screen
import com.jim.realcash.ui.UiMessages
import com.jim.realcash.ui.takeout.TakeOutFeedback
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.jetbrains.skia.EncodedImageFormat

/**
 * Renders every screen offscreen with [ImageComposeScene] at 390×844 logical px (density 2) over a real
 * [RealCashData] on an in-memory database, and writes PNGs to `shared/ui/build/screenshots/`.
 *
 * The clock is frozen at 2026-11-04 18:00 KST (a Wednesday; monthly period 11.1 – 11.30).
 */
class ScreenshotTest {
    private val now = Instant.parse("2026-11-04T09:00:00Z")
    private val seoul = TimeZone.of("Asia/Seoul")
    private val periodStart = LocalDate(2026, 11, 1)
    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }

    private val outDir = File(System.getProperty("realcash.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    /** Fresh data layer; SQLite calls run inline (no dispatcher hop), so every mutation completes synchronously. */
    private fun newData(clock: Clock = fixedClock): RealCashData =
        RealCashData(DriverFactory().createDriver(), clock, SequentialIdGenerator("id-"), ioContext = EmptyCoroutineContext)

    /** A data-layer clock that can be moved, so ledger events land on different days (the UI clock stays fixed). */
    private class MovableClock(var now: Instant) : Clock {
        override fun now(): Instant = now
    }

    /** An [AppState] whose scope runs inline, with settings and ledger loaded. */
    private fun newAppState(data: RealCashData): AppState {
        val env = AppEnvironment(data, Haptics.None, UiMessages(), fixedClock, seoul)
        return AppState(env, CoroutineScope(Dispatchers.Unconfined)).also { it.start() }
    }

    private suspend fun RealCashData.seedAllowance() {
        wallet.receiveAllowance(mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5, C500 to 3, C100 to 4), periodStart)
    }

    private suspend fun RealCashData.seedHand(vararg denominations: Denomination, expected: Won? = null) {
        val inWallet = wallet.state.value.inWallet.toMutableList()
        val ids = denominations.map { d -> inWallet.first { it.denomination == d }.also(inWallet::remove).id }
        wallet.moveToHand(ids, expectedAmount = expected, ttl = 6.hours)
    }

    private suspend fun RealCashData.seedPending() {
        wallet.recordDetectedPayment(
            DetectedPayment(PaymentId("pay-1"), Won(4_800), "이디야커피 역삼점", DefaultRules.TOSS, "4,800원 결제 | 이디야커피 역삼점", now - 2.hours, 0.62f, false),
        )
        wallet.recordDetectedPayment(
            DetectedPayment(PaymentId("pay-2"), Won(12_300), null, DefaultRules.SAMSUNG_WALLET, "12,300원 결제", now - 20.minutes, 0.55f, false),
        )
    }

    @Test
    fun a_walletHomeEmpty() = runBlocking {
        val app = newAppState(newData())
        render("a-wallet-empty") { RealCashRoot(app, showDebugShortcut = true) }
    }

    @Test
    fun b_walletHomeWithAllowance() = runBlocking {
        val data = newData().apply { seedAllowance() }
        val app = newAppState(data)
        render("b-wallet-allowance") { RealCashRoot(app, showDebugShortcut = true) }
    }

    @Test
    fun c_walletHomeWithHandAndPending() = runBlocking {
        val data = newData().apply {
            seedAllowance()
            seedHand(W10000, W5000, expected = Won(13_000))
            seedPending()
        }
        val app = newAppState(data)
        render("c-wallet-hand-pending") { RealCashRoot(app, showDebugShortcut = true) }
    }

    @Test
    fun d_receiveAllowance() = runBlocking {
        val app = newAppState(newData())
        app.navigate(Screen.ReceiveAllowance)
        app.receiveAllowance.onAmountChange("100000")
        render("d-receive-allowance") { RealCashRoot(app) }
    }

    @Test
    fun e_confirmPaymentsSheet() = runBlocking {
        val data = newData().apply {
            seedAllowance()
            seedHand(W10000, W5000)
            seedPending()
        }
        val app = newAppState(data)
        app.openConfirmSheet()
        render("e-confirm-payments") { RealCashRoot(app) }
    }

    @Test
    fun f_simulatorAfterAutoSettle() = runBlocking {
        val data = newData().apply {
            seedAllowance()
            seedHand(W5000)
        }
        val app = newAppState(data)
        app.navigate(Screen.NotificationSimulator)
        app.simulator.send() // Samsung preset: 스타벅스 강남점 6,500원 -> hand 5,000 + wallet pull, change back.
        assertTrue(app.simulator.result.value != null, "the simulator should have a result")
        render("f-simulator-autosettled") { RealCashRoot(app) }
    }

    @Test
    fun g_walletHomeDark() = runBlocking {
        val data = newData().apply {
            seedAllowance()
            seedHand(W10000, W5000, expected = Won(13_000))
        }
        val app = newAppState(data)
        render("g-wallet-dark", dark = true) { RealCashRoot(app, showDebugShortcut = true) }
    }

    @Test
    fun h_takeOutOverExpected() = runBlocking {
        val data = newData().apply { seedAllowance() }
        val app = newAppState(data)
        app.navigate(Screen.TakeOut(refill = false))
        app.takeOut.onExpectedChange("13000")
        app.takeOut.tapAdd(W10000)
        app.takeOut.tapAdd(W5000)
        val feedback = assertIs<TakeOutFeedback.Over>(app.takeOut.draft.value.feedback)
        assertEquals(Won(2_000), feedback.changeTotal)
        render("h-takeout-over") { RealCashRoot(app) }
    }

    @Test
    fun i_takeOutMidDrag() = runBlocking {
        val data = newData().apply { seedAllowance() }
        val app = newAppState(data)
        app.navigate(Screen.TakeOut(refill = false))
        val model = app.takeOut
        model.onExpectedChange("13000")
        model.tapAdd(W10000)
        render("i-takeout-dragging", midway = {
            // Drive the hoisted drag state directly (no pointer events): a 5,000 bill held over the hand zone.
            val zone = checkNotNull(model.drag.zoneBounds) { "the hand zone reports its bounds after layout" }
            model.drag.start(W5000, zone.center + Offset(zone.width * 0.18f, 0f))
            Snapshot.sendApplyNotifications()
        }) { RealCashRoot(app) }
        assertTrue(model.drag.isOverZone, "the ghost is over the hand zone")
    }

    @Test
    fun j_exchangeReceiveStep() = runBlocking {
        val data = newData().apply { seedAllowance() }
        val app = newAppState(data)
        app.navigate(Screen.Exchange)
        app.exchange.addToTray(W50000)
        app.exchange.toReceive()
        assertTrue(app.exchange.draft.value.isValid)
        render("j-exchange-receive") { RealCashRoot(app) }
    }

    @Test
    fun k_ledger() = runBlocking {
        val clock = MovableClock(Instant.parse("2026-11-02T00:30:00Z")) // Mon 09:30 KST
        val data = newData(clock).apply {
            seedAllowance()
            clock.now = Instant.parse("2026-11-03T03:10:00Z") // Tue 12:10: take 10,000 out, pay 6,500 with it
            seedHand(W10000, expected = Won(7_000))
            intake.onNotification(RawNotification(DefaultRules.SAMSUNG_WALLET, "결제 완료", "스타벅스 강남점에서 6,500원 결제", null, clock.now))
            clock.now = Instant.parse("2026-11-04T01:20:00Z") // Wed 10:20: exchange the 50,000 bill
            val fifty = wallet.state.value.inWallet.first { it.denomination == W50000 }
            wallet.exchange(listOf(fifty.id), mapOf(W10000 to 5))
            clock.now = Instant.parse("2026-11-04T08:40:00Z") // Wed 17:40: paid without taking cash out
            intake.onNotification(RawNotification(DefaultRules.TOSS, "토스페이", "12,000원 결제 | 김밥천국 역삼점", null, clock.now))
        }
        val app = newAppState(data)
        app.navigate(Screen.Ledger)
        val summary = app.ledger.ui.value.summary
        assertEquals(2, summary.paymentCount)
        assertEquals(1, summary.directCount)
        render("k-ledger") { RealCashRoot(app) }
    }

    @Test
    fun l_settings() = runBlocking {
        val app = newAppState(newData())
        app.navigate(Screen.Settings)
        // Taller than a phone so the whole tab, including 감지 앱 and the 디버그 section, is visible in one image.
        render("l-settings", height = 1420) { RealCashRoot(app) }
    }

    @Test
    fun m_walletHomeAllBills() = runBlocking {
        val data = newData().apply {
            wallet.receiveAllowance(mapOf(W50000 to 4, W10000 to 3, W5000 to 2, W1000 to 1, C500 to 3, C100 to 4, C50 to 2, C10 to 5), periodStart)
        }
        val app = newAppState(data)
        assertEquals(Denomination.entries.toSet(), data.wallet.state.value.inWallet.map { it.denomination }.toSet())
        render("m-wallet-all-bills") { RealCashRoot(app) }
    }

    @Test
    fun n_confirmedChangeChips() = runBlocking {
        val data = newData().apply {
            seedAllowance()
            seedHand(W10000, W5000)
            seedPending()
        }
        val app = newAppState(data)
        app.openConfirmSheet()
        app.confirmPayments.confirm(PaymentId("pay-1"))
        app.confirmPayments.reject(PaymentId("pay-2"))
        assertEquals(mapOf(W10000 to 1, C100 to 2), app.confirmPayments.ui.value.confirmed.single().changeCounts)
        render("n-confirmed-change") { RealCashRoot(app) }
    }

    @Test
    fun o_walletHomeAllCoins() = runBlocking {
        val data = newData().apply {
            wallet.receiveAllowance(mapOf(C500 to 3, C100 to 4, C50 to 2, C10 to 5), periodStart)
        }
        val app = newAppState(data)
        assertEquals(
            mapOf(C500 to 3, C100 to 4, C50 to 2, C10 to 5),
            data.wallet.state.value.inWallet.groupingBy { it.denomination }.eachCount(),
        )
        render("o-wallet-all-coins") { RealCashRoot(app) }
    }

    @Test
    fun p_takeOutMidCoinDrag() = runBlocking {
        val data = newData().apply { seedAllowance() }
        val app = newAppState(data)
        app.navigate(Screen.TakeOut(refill = false))
        app.takeOut.tapAdd(C100) // Also exercise the 20dp coin chip in the hand zone.
        render("p-takeout-coin-dragging", midway = {
            val zone = checkNotNull(app.takeOut.drag.zoneBounds)
            app.takeOut.drag.start(C500, zone.center + Offset(zone.width * 0.18f, 0f))
            Snapshot.sendApplyNotifications()
        }) { RealCashRoot(app) }
        assertTrue(app.takeOut.drag.isOverZone)
    }

    @Test
    fun q_walletHomeAllCoinsDark() = runBlocking {
        val data = newData().apply {
            wallet.receiveAllowance(mapOf(C500 to 3, C100 to 4, C50 to 2, C10 to 5), periodStart)
        }
        val app = newAppState(data)
        render("q-wallet-all-coins-dark", dark = true) { RealCashRoot(app) }
    }

    @Test
    fun p_manualPaymentSheet() = runBlocking {
        val data = newData().apply { seedAllowance(); seedHand(W10000) }
        val app = newAppState(data)
        app.openManualSheet()
        app.manualPayment.onAmountChange("6500")
        render("p-manual-payment-sheet") { RealCashRoot(app) }
    }

    @Test
    fun q_manualPaymentResult() = runBlocking {
        val data = newData().apply { seedAllowance(); seedHand(W10000) }
        val app = newAppState(data)
        app.openManualSheet()
        app.manualPayment.onAmountChange("6500")
        app.manualPayment.onMerchantChange("동네 카페")
        app.manualPayment.submit()
        val result = checkNotNull(app.manualPayment.draft.value.result)
        assertEquals(Won(10_000), result.paid)
        assertEquals(Won(3_500), result.change)
        assertTrue(com.jim.realcash.domain.model.SettlementFlag.MANUAL in result.settlement.flags)
        render("q-manual-payment-result") { RealCashRoot(app) }
    }

    @Test
    fun r_walletHomeThirtyBills() = runBlocking {
        val data = newData().apply { wallet.receiveAllowance(mapOf(W10000 to 30), periodStart) }
        val app = newAppState(data)
        assertEquals(30, app.walletHome.ui.value.billCount)
        assertEquals(6, app.walletHome.ui.value.sideLayers)
        render("r-wallet-thick") { RealCashRoot(app) }
    }

    @Test
    fun s_walletHomeMenuOpen() = runBlocking {
        val data = newData().apply { seedAllowance() }
        val app = newAppState(data)
        render("s-wallet-menu", interact = { scene ->
            val menu = Offset(354f * SCALE, 32f * SCALE)
            scene.sendPointerEvent(PointerEventType.Press, menu)
            scene.sendPointerEvent(PointerEventType.Release, menu)
        }) { RealCashRoot(app) }
    }

    @Test
    fun t_walletTapAndBack() = runBlocking {
        val data = newData().apply { seedAllowance() }
        val app = newAppState(data)
        render("t-wallet-open", interact = { scene ->
            val wallet = Offset(195f * SCALE, 480f * SCALE)
            scene.sendPointerEvent(PointerEventType.Press, wallet)
            scene.sendPointerEvent(PointerEventType.Release, wallet)
        }) { RealCashRoot(app) }
        assertEquals(Screen.WalletOpen, app.current)
        assertTrue(app.back())
        assertEquals(Screen.WalletHome, app.current)
        assertTrue(!app.canGoBack)
    }

    private fun render(
        name: String,
        dark: Boolean = false,
        height: Int = HEIGHT,
        midway: (() -> Unit)? = null,
        interact: ((ImageComposeScene) -> Unit)? = null,
        content: @Composable () -> Unit,
    ) {
        val scene = ImageComposeScene(WIDTH * SCALE, height * SCALE, Density(SCALE.toFloat())) {
            RealCashTheme(darkTheme = dark, content = content)
        }
        try {
            // Advance the frame clock so enter animations (sheet, ripples) settle before the final frame.
            var t = 0L
            repeat(40) { frame ->
                if (frame == 20) {
                    midway?.invoke()
                    interact?.invoke(scene)
                }
                scene.render(t)
                t += FRAME_NANOS
            }
            val image = scene.render(t)
            val png = checkNotNull(image.encodeToData(EncodedImageFormat.PNG)) { "PNG encoding failed" }.bytes
            val file = File(outDir, "$name.png")
            file.writeBytes(png)
            println("screenshot: ${file.absolutePath}")
            assertTrue(file.length() > 0, "empty screenshot $name")
        } finally {
            scene.close()
        }
    }

    private companion object {
        const val WIDTH = 390
        const val HEIGHT = 844
        const val SCALE = 2
        const val FRAME_NANOS = 50_000_000L
    }
}
