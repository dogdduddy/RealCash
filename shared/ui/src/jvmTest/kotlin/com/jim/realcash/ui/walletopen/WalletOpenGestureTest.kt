package com.jim.realcash.ui.walletopen

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.jim.realcash.data.RealCashData
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.model.*
import com.jim.realcash.domain.model.Denomination.*
import com.jim.realcash.domain.util.SequentialIdGenerator
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.ResourceMoneyArt
import com.jim.realcash.ui.theme.RealCashTheme
import java.io.File
import kotlin.test.*
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat

@OptIn(ExperimentalCoroutinesApi::class)
class WalletOpenGestureTest {
    @Test
    fun w2_secondLongPressSurvivesAutoSave() = runTest {
        val driver = DriverFactory().createDriver()
        val clock = object : Clock { override fun now() = Instant.parse("2026-11-04T09:00:00Z") }
        try {
            val writes = PausedWalletWrites(StandardTestDispatcher(testScheduler))
            val data = RealCashData(driver, clock, SequentialIdGenerator(), writes)
            val counts = mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5, C500 to 2)
            data.wallet.receiveAllowance(counts, LocalDate(2026, 11, 1))
            data.settings.load()
            val model = WalletOpenModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock), seed = 1)
            val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
                RealCashTheme(darkTheme = false) {
                    CompositionLocalProvider(LocalMoneyArt provides ResourceMoneyArt) {
                        Surface { WalletOpenScreen(model, {}, {}) }
                    }
                }
            }
            var frameTime = 0L
            fun frame() {
                Snapshot.sendApplyNotifications()
                scene.render(frameTime)
                frameTime += 16_000_000L
            }
            fun press(point: Offset) = scene.sendPointerEvent(PointerEventType.Press, point, timeMillis = testScheduler.currentTime)
            fun move(point: Offset) = scene.sendPointerEvent(PointerEventType.Move, point, timeMillis = testScheduler.currentTime)
            fun release(point: Offset) {
                scene.sendPointerEvent(PointerEventType.Release, point, timeMillis = testScheduler.currentTime)
            }
            try {
                repeat(40) { frame(); runCurrent() }
                val layout = WalletOpenLayout.calculate(390f, 844f)
                fun pocketPoint() = pocketBillGroups(
                    model.ui.value.inWallet.filter { it.denomination.kind == Denomination.Kind.BILL }
                        .groupingBy { it.denomination }.eachCount(), layout.pocket,
                ).first { it.denomination == W10000 }.let { Offset(it.frontLeft + 2f, it.top + it.exposedHeight / 2f) }

                // The first bill is really picked up and dropped, starting the 300ms save debounce.
                val firstPoint = pocketPoint()
                press(firstPoint)
                advanceTimeBy(400); runCurrent(); frame()
                val firstId = assertNotNull(model.physics.picked).unit.id
                move(layout.trayInner.center)
                release(layout.trayInner.center)
                runCurrent()
                repeat(90) { frame(); runCurrent() } // finish the flight on the scene's frame clock
                assertNull(model.physics.picked)
                assertEquals(listOf(firstId), model.ui.value.inHand.map { it.id })
                assertTrue(data.wallet.state.value.inHand.isEmpty())

                // Press the next bill 100ms after the drop. Saving starts 200ms into this hold.
                advanceTimeBy(100); runCurrent(); frame()
                val nextPoint = pocketPoint()
                val nextId = model.ui.value.inWallet.first { it.denomination == W10000 }.id
                press(nextPoint)
                writes.paused = true
                advanceTimeBy(200); runCurrent(); frame()
                assertTrue(model.submitting.value)
                assertNull(model.physics.picked)
                advanceTimeBy(200); runCurrent(); frame()
                assertEquals(nextId, assertNotNull(model.physics.picked).unit.id,
                    "the 400ms long press must survive the save that began at 300ms after the first drop")

                val dir = File(System.getProperty("realcash.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
                File(dir, "w2-wallet-open-held.png").writeBytes(
                    checkNotNull(scene.render(frameTime).encodeToData(EncodedImageFormat.PNG)).bytes,
                )

                // A drop from the ongoing drag is accepted even while the first save is suspended.
                move(layout.trayInner.center)
                frame()
                assertTrue(model.physics.overTray)
                release(layout.trayInner.center)
                runCurrent(); frame()
                assertEquals(listOf(firstId, nextId), model.ui.value.inHand.map { it.id })
                writes.resume()
                runCurrent(); frame()
                assertEquals(listOf(firstId, nextId), data.wallet.state.value.activeHand?.unitIds)
                assertFalse(model.submitting.value)
                assertFalse(model.ui.value.changed)
            } finally { scene.close() }
        } finally { driver.close() }
    }

    @Test
    fun pocketReflowDuringLongPressKeepsTheBillPressedAtDown() = runTest {
        val units = listOf(W50000, W10000, W10000, W5000, W1000).mapIndexed { index, denomination ->
            CashUnit(CashUnitId("bill-$index"), denomination, CashUnitState.IN_WALLET,
                Instant.parse("2026-11-04T09:00:00Z"), CashUnitSource.ALLOWANCE)
        }
        val ui = mutableStateOf(WalletOpenUi(units, emptyList(), false))
        val physics = WalletOpenPhysics()
        val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
            RealCashTheme { WalletOpenContent(ui.value, 1, false, {}, {}, {}, physics = physics) }
        }
        var time = 0L
        fun frame() {
            Snapshot.sendApplyNotifications()
            scene.render(time)
            time += 16_000_000L
        }
        try {
            repeat(10) { frame(); runCurrent() }
            val pocket = WalletOpenLayout.calculate(390f, 844f).pocket
            val before = pocketBillGroups(units.groupingBy { it.denomination }.eachCount(), pocket)
            val index = before.indexOfFirst { it.denomination == W10000 }
            val point = pocketBillHitBounds(before, index).center
            scene.sendPointerEvent(PointerEventType.Press, point)
            advanceTimeBy(150); runCurrent()
            // Removing another denomination changes both the recognizer's hit geometry and sway list.
            ui.value = WalletOpenUi(units.drop(1), units.take(1), true)
            frame(); runCurrent(); frame()
            val after = pocketBillGroups(units.drop(1).groupingBy { it.denomination }.eachCount(), pocket)
            assertNotEquals(before[index].frontLeft, after.first { it.denomination == W10000 }.frontLeft)
            advanceTimeBy(250); runCurrent(); frame()
            assertEquals(units[1].id, assertNotNull(physics.picked).unit.id)
            scene.sendPointerEvent(PointerEventType.Release, point)
        } finally { scene.close() }
    }

    @Test
    fun downDuringSaveStaysIgnoredUntilANewPress() = runTest {
        val bill = CashUnit(CashUnitId("bill"), W10000, CashUnitState.IN_WALLET,
            Instant.parse("2026-11-04T09:00:00Z"), CashUnitSource.ALLOWANCE)
        val submitting = mutableStateOf(true)
        val physics = WalletOpenPhysics()
        val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
            RealCashTheme { WalletOpenContent(WalletOpenUi(listOf(bill), emptyList(), false), 1,
                submitting.value, {}, {}, {}, physics = physics) }
        }
        var time = 0L
        fun frame() {
            Snapshot.sendApplyNotifications()
            scene.render(time)
            time += 16_000_000L
        }
        try {
            repeat(10) { frame(); runCurrent() }
            val group = pocketBillGroups(mapOf(W10000 to 1), WalletOpenLayout.calculate(390f, 844f).pocket).single()
            val point = Offset(group.frontLeft + 2f, group.top + group.exposedHeight / 2f)
            scene.sendPointerEvent(PointerEventType.Press, point)
            advanceTimeBy(200); runCurrent()
            submitting.value = false
            frame(); runCurrent(); frame()
            advanceTimeBy(400); runCurrent(); frame()
            assertNull(physics.picked, "enabling must not adopt a down that was ignored during saving")
            scene.sendPointerEvent(PointerEventType.Release, point)
            scene.sendPointerEvent(PointerEventType.Press, point)
            advanceTimeBy(400); runCurrent(); frame()
            assertEquals(bill.id, assertNotNull(physics.picked).unit.id)
            scene.sendPointerEvent(PointerEventType.Release, point)
        } finally { scene.close() }
    }

}
