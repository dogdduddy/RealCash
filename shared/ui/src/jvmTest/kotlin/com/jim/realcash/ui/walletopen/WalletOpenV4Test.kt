package com.jim.realcash.ui.walletopen

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.jim.realcash.domain.model.*
import com.jim.realcash.domain.model.Denomination.*
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.ResourceMoneyArt
import com.jim.realcash.ui.theme.RealCashTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.EncodedImageFormat

@OptIn(ExperimentalCoroutinesApi::class)
class WalletOpenV4Test {
    private val allowance = mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5, C500 to 2, C100 to 3, C10 to 2)
    private fun units(counts: Map<Denomination, Int>) = counts.flatMap { (d, n) -> List(n) { i ->
        CashUnit(CashUnitId("$d-$i"), d, CashUnitState.IN_WALLET,
            Instant.parse("2026-11-05T09:00:00Z"), CashUnitSource.ALLOWANCE)
    } }
    private fun save(scene: ImageComposeScene, time: Long, name: String): ByteArray {
        val bytes = checkNotNull(scene.render(time).encodeToData(EncodedImageFormat.PNG)).bytes
        val dir = File(System.getProperty("realcash.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
        File(dir, name).writeBytes(bytes)
        return bytes
    }

    @Test fun reviewScreenshotsAndEachDenominationHeadPicksItsFrontAtAllSizes() = runTest {
        for ((w, h) in listOf(390 to 844, 320 to 568, 844 to 390)) {
            val cash = units(allowance)
            val ui = mutableStateOf(WalletOpenUi(cash, emptyList(), false))
            val physics = WalletOpenPhysics()
            val scene = ImageComposeScene(w, h, Density(1f), coroutineContext = coroutineContext) {
                RealCashTheme(darkTheme = false) {
                    CompositionLocalProvider(LocalMoneyArt provides ResourceMoneyArt) {
                        Surface { WalletOpenContent(ui.value, 7, false, {}, {}, {}, physics = physics) }
                    }
                }
            }
            var time = 0L
            fun frame() { Snapshot.sendApplyNotifications(); scene.render(time); time += 16_000_000L }
            fun settle() { repeat(100) { frame(); runCurrent() } }
            try {
                settle()
                val png = save(scene, time, if (w == 390) "v4b-open.png" else "v4b-open-${w}x$h.png")
                assertEquals(w, ImageIO.read(png.inputStream()).width)
                assertEquals(h, ImageIO.read(png.inputStream()).height)
                val layout = WalletOpenLayout.calculate(w.toFloat(), h.toFloat())
                val groups = pocketBillGroups(allowance, layout.pocket)
                for (group in groups) {
                    val point = Offset(group.openingX + 2f, group.top + 3f)
                    scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = testScheduler.currentTime)
                    advanceTimeBy(400); runCurrent(); frame()
                    val pick = assertNotNull(physics.picked)
                    assertEquals(cash.first { it.denomination == group.denomination }.id, pick.unit.id)
                    assertEquals(group.face, pick.source)
                    assertEquals(group.billHeight, pick.faceWidth)
                    if (w == 390 && group.denomination == W10000) {
                        scene.sendPointerEvent(PointerEventType.Move, Offset(190f, 300f), timeMillis = testScheduler.currentTime)
                        settle()
                        save(scene, time, "v4-held.png")
                    }
                    scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = testScheduler.currentTime)
                    runCurrent(); settle()
                    assertNull(physics.picked)
                    assertEquals(cash, ui.value.inWallet)
                }
                if (w == 390) {
                    ui.value = WalletOpenUi(units(mapOf(W10000 to 30, C500 to 2)), emptyList(), false)
                    settle()
                    save(scene, time, "v4b-30.png")
                }
            } finally { scene.close() }
        }
    }

    @Test fun renderedPaperNeverChangesPixelsToTheLeftOfOpening() = runTest {
        val ui = mutableStateOf(WalletOpenUi(emptyList(), emptyList(), false))
        val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
            RealCashTheme { CompositionLocalProvider(LocalMoneyArt provides ResourceMoneyArt) {
                Surface { WalletOpenContent(ui.value, 7, false, {}, {}, {}) }
            } }
        }
        var time = 0L
        fun frame() { Snapshot.sendApplyNotifications(); scene.render(time); time += 16_000_000L }
        try {
            repeat(10) { frame(); runCurrent() }
            val before = ImageIO.read(checkNotNull(scene.render(time).encodeToData(EncodedImageFormat.PNG)).bytes.inputStream())
            ui.value = WalletOpenUi(units(allowance.filterKeys { it.kind == Denomination.Kind.BILL }), emptyList(), false)
            repeat(10) { frame(); runCurrent() }
            val after = ImageIO.read(checkNotNull(scene.render(time).encodeToData(EncodedImageFormat.PNG)).bytes.inputStream())
            val pocket = WalletOpenLayout.calculate(390f, 844f).pocket
            for (y in pocket.top.toInt() until pocket.bottom.toInt()) for (x in 0 until pocket.left.toInt()) {
                assertEquals(before.getRGB(x, y), after.getRGB(x, y), "paper leaked inside wallet at $x,$y")
            }
            var changed = 0
            for (y in pocket.top.toInt() until pocket.bottom.toInt()) for (x in pocket.left.toInt() + 1 until 390) {
                if (before.getRGB(x, y) != after.getRGB(x, y)) changed++
            }
            assertTrue(changed > 1000, "the exposed faces must actually be rendered")
        } finally { scene.close() }
    }
}
