package com.jim.realcash.ui.walletopen

import androidx.compose.ui.ImageComposeScene
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
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat

class WalletOpenScreenshotTest {
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
            val scene = ImageComposeScene(780, 1688, Density(2f)) { RealCashTheme(darkTheme = false) { RealCashRoot(app) } }
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
