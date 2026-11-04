package com.jim.realcash.ui.walletopen

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.Density
import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.model.Denomination.*
import com.jim.realcash.domain.util.SequentialIdGenerator
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.AppState
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.RealCashRoot
import com.jim.realcash.ui.theme.RealCashTheme
import java.io.File
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat

class WalletOpenScreenshotTest {
    @Test
    fun w_longPressBillHeldAboveTray() = runBlocking {
        val driver = DriverFactory().createDriver()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val clock = object : Clock { override fun now() = Instant.parse("2026-11-04T09:00:00Z") }
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator(), EmptyCoroutineContext)
            val counts = mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5, C500 to 2)
            data.wallet.receiveAllowance(counts, LocalDate(2026, 11, 1))
            val app = AppState(AppEnvironment(data, Haptics.None, clock = clock), scope)
            app.openWallet()
            val scene = ImageComposeScene(780, 1688, Density(2f), coroutineContext = coroutineContext) { RealCashTheme(darkTheme = false) { RealCashRoot(app) } }
            try {
                repeat(40) { scene.render(it * 50_000_000L) }
                val layout = WalletOpenLayout.calculate(390f, 844f)
                val group = pocketBillGroups(counts, layout.pocket)[1]
                val start = Offset(group.left + 2f, group.top + group.exposedHeight / 2f)
                scene.sendPointerEvent(PointerEventType.Press, start * 2f)
                delay(450)
                scene.render(2_050_000_000L)
                assertTrue(app.walletOpen.physics.picked != null, "a real long press picks up the front unit")
                val held = Offset(layout.trayInner.center.x, layout.trayInner.bottom - 10f)
                scene.sendPointerEvent(PointerEventType.Move, held * 2f)
                Snapshot.sendApplyNotifications()
                repeat(15) { scene.render(2_100_000_000L + it * 50_000_000L) }
                assertTrue(app.walletOpen.physics.overTray)
                assertTrue(data.wallet.state.value.inHand.isEmpty(), "holding is not a drop")
                val png = checkNotNull(scene.render(2_900_000_000L).encodeToData(EncodedImageFormat.PNG)).bytes
                val dir = File("src/jvmTest/kotlin/com/jim/realcash/ui/walletopen/screenshots").apply { mkdirs() }
                val file = File(dir, "w-wallet-open-held-above-tray.png")
                file.writeBytes(png)
                assertTrue(file.length() > 0)
                println("screenshot: ${file.absolutePath}")
                scene.sendPointerEvent(PointerEventType.Release, held * 2f)
            } finally { scene.close() }
        } finally { scope.cancel(); driver.close() }
    }

    @Test
    fun x_openWalletThirtyBills() = runBlocking {
        val driver = DriverFactory().createDriver()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val clock = object : Clock { override fun now() = Instant.parse("2026-11-04T09:00:00Z") }
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator(), EmptyCoroutineContext)
            val counts = mapOf(W50000 to 2, W10000 to 10, W5000 to 8, W1000 to 10)
            data.wallet.receiveAllowance(counts, LocalDate(2026, 11, 1))
            val app = AppState(AppEnvironment(data, Haptics.None, clock = clock), scope)
            app.openWallet()
            val scene = ImageComposeScene(780, 1688, Density(2f), coroutineContext = coroutineContext) { RealCashTheme(darkTheme = false) { RealCashRoot(app) } }
            try {
                repeat(40) { scene.render(it * 50_000_000L) }
                assertEquals(30, app.walletOpen.ui.value.inWallet.size)
                val groups = pocketBillGroups(counts, WalletOpenLayout.calculate(390f, 844f).pocket)
                assertTrue(groups.all { it.visibleCount == it.count || it.fanStep == 3f })
                val png = checkNotNull(scene.render(2_000_000_000L).encodeToData(EncodedImageFormat.PNG)).bytes
                val dir = File(System.getProperty("realcash.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
                val file = File(dir, "x-wallet-open-thirty-bills.png")
                file.writeBytes(png)
                assertTrue(file.length() > 0)
                println("screenshot: ${file.absolutePath}")
            } finally { scene.close() }
        } finally { scope.cancel(); driver.close() }
    }
}
