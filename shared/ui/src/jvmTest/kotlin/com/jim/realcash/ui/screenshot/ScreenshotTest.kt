package com.jim.realcash.ui.screenshot

import androidx.compose.runtime.Composable
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.model.Denomination.C100
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.parser.DefaultRules
import com.jim.realcash.domain.util.SequentialIdGenerator
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.AppState
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.RealCashRoot
import com.jim.realcash.ui.theme.RealCashTheme
import com.jim.realcash.ui.Screen
import com.jim.realcash.ui.UiMessages
import java.io.File
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
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
 * Renders every T5a screen offscreen with [ImageComposeScene] at 390×844 logical px (density 2) over a real
 * [RealCashData] on an in-memory database, and writes PNGs to `shared/ui/build/screenshots/`.
 *
 * The clock is frozen at 2026-11-04 18:00 KST (a Wednesday; weekly period Mon 11.2 – Sun 11.8).
 */
class ScreenshotTest {
    private val now = Instant.parse("2026-11-04T09:00:00Z")
    private val seoul = TimeZone.of("Asia/Seoul")
    private val periodStart = LocalDate(2026, 11, 2)
    private val fixedClock = object : Clock {
        override fun now(): Instant = now
    }

    private val outDir = File(System.getProperty("realcash.screenshotDir") ?: "build/screenshots").apply { mkdirs() }

    /** Fresh data layer; SQLite calls run inline (no dispatcher hop), so every mutation completes synchronously. */
    private fun newData(): RealCashData =
        RealCashData(DriverFactory().createDriver(), fixedClock, SequentialIdGenerator("id-"), ioContext = EmptyCoroutineContext)

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

    private fun render(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(WIDTH * SCALE, HEIGHT * SCALE, Density(SCALE.toFloat())) {
            RealCashTheme(darkTheme = dark, content = content)
        }
        try {
            // Advance the frame clock so enter animations (sheet, ripples) settle before the final frame.
            var t = 0L
            repeat(40) {
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
