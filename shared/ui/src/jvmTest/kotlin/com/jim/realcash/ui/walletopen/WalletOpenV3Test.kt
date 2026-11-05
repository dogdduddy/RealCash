package com.jim.realcash.ui.walletopen

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.jetbrains.skia.EncodedImageFormat

@OptIn(ExperimentalCoroutinesApi::class)
class WalletOpenV3Test {
    @Test
    fun rightFloorScatterFitsBesideTheWholeWalletInBothOrientations() {
        for ((width, height) in listOf(320f to 568f, 390f to 844f, 844f to 390f)) {
            val layout = WalletOpenLayout.calculate(width, height)
            val coins = CoinScatter.place(7, listOf(34f, 34f, 28f, 24f), layout.coins)
            assertEquals(4, coins.size)
            assertTrue(layout.wallet.left >= 0f && layout.wallet.right <= width)
            assertTrue(layout.wallet.top >= 0f && layout.wallet.bottom <= height)
            coins.forEachIndexed { index, coin ->
                assertTrue(coin.x - coin.diameter / 2f > layout.wallet.right)
                assertTrue(coin.x + coin.diameter / 2f <= width)
                assertTrue(coin.y - coin.diameter / 2f >= layout.coins.top)
                assertTrue(coin.y + coin.diameter / 2f <= height)
                coins.take(index).forEach { other ->
                    assertTrue(kotlin.math.hypot(coin.x - other.x, coin.y - other.y) >= (coin.diameter + other.diameter) / 2f)
                }
            }
        }
    }

    @Test
    fun onlyExposedHeadsAndTopFaceCanBePicked() {
        val layout = WalletOpenLayout.calculate(390f, 844f)
        val groups = pocketBillGroups(mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5), layout.pocket)
        groups.forEachIndexed { index, group ->
            val unit = CashUnit(CashUnitId("hit-$index"), group.denomination, CashUnitState.IN_WALLET,
                Instant.parse("2026-11-05T09:00:00Z"), CashUnitSource.ALLOWANCE)
            val hit = CashHit(unit, false, pocketBillHitBounds(groups, index), group.face)
            assertTrue(hit.contains(hit.hit.center))
            assertFalse(hit.contains(Offset(group.openingX - 1f, hit.hit.center.y)))
            assertFalse(hit.contains(Offset(group.right + 1f, hit.hit.center.y)))
            if (index < groups.lastIndex) assertFalse(hit.contains(Offset(group.openingX + 1f, group.face.center.y)))
        }
    }

    @Test
    fun floorDropsExcludeArtEdgesAndOtherCoins() {
        val layout = WalletOpenLayout.calculate(390f, 844f)
        val floor = WalletRect(0f, 56f, 390f, 788f)
        val left = Offset(40f, 300f)
        val target = assertNotNull(floorCoinTarget(left, 34f, floor, layout.wallet, layout.tray, emptyList()))
        assertEquals(left, target.center)
        assertNull(floorCoinTarget(layout.wallet.center, 34f, floor, layout.wallet, layout.tray, emptyList()))
        assertNull(floorCoinTarget(layout.tray.center, 34f, floor, layout.wallet, layout.tray, emptyList()))
        assertNull(floorCoinTarget(Offset(2f, 600f), 34f, floor, layout.wallet, layout.tray, emptyList()))
        assertNull(floorCoinTarget(left + Offset(30f, 0f), 34f, floor, layout.wallet, layout.tray, listOf(target)))
    }

    @Test
    fun v3ScreenshotsAndCoinLeftPositionSurviveRecompositionAndTraySave() = runTest {
        val driver = DriverFactory().createDriver()
        val clock = object : Clock { override fun now() = Instant.parse("2026-11-05T09:00:00Z") }
        try {
            val data = RealCashData(driver, clock, SequentialIdGenerator(), kotlin.coroutines.EmptyCoroutineContext)
            val counts = mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5, C500 to 2)
            data.wallet.receiveAllowance(counts, LocalDate(2026, 11, 1))
            val model = WalletOpenModel(backgroundScope, AppEnvironment(data, Haptics.None, clock = clock), seed = 7)
            val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
                RealCashTheme(darkTheme = false) {
                    CompositionLocalProvider(LocalMoneyArt provides ResourceMoneyArt) {
                        Surface { WalletOpenScreen(model, {}, {}) }
                    }
                }
            }
            var time = 0L
            fun frame() { Snapshot.sendApplyNotifications(); scene.render(time); time += 16_000_000L }
            fun settle() { repeat(100) { frame(); runCurrent() } }
            fun pointer(type: PointerEventType, point: Offset) = scene.sendPointerEvent(type, point, timeMillis = testScheduler.currentTime)
            fun screenshot(name: String) {
                val dir = File(System.getProperty("realcash.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
                File(dir, name).writeBytes(checkNotNull(scene.render(time).encodeToData(EncodedImageFormat.PNG)).bytes)
            }
            fun pick(point: Offset) {
                pointer(PointerEventType.Press, point)
                advanceTimeBy(400); runCurrent(); frame()
                assertNotNull(model.physics.picked)
            }
            fun drop(point: Offset) {
                pointer(PointerEventType.Move, point)
                pointer(PointerEventType.Release, point)
                runCurrent(); frame()
            }
            try {
                settle()
                screenshot("v3b-open.png")
                val layout = WalletOpenLayout.calculate(390f, 844f)
                val groups = pocketBillGroups(counts, layout.pocket)
                val front = model.ui.value.inWallet.first { it.denomination == W10000 }
                // Even the far right back edge picks the left front unit from the whole group hit band.
                val billPoint = Offset(groups[1].openingX + 2f, groups[1].top + 3f)
                pick(billPoint)
                val picked = assertNotNull(model.physics.picked)
                assertEquals(front.id, picked.unit.id)
                assertEquals(groups[1].left, picked.source.left)
                assertEquals(3, model.ui.value.inWallet.count { it.denomination == W10000 }, "holding preserves the original fan slots")
                pointer(PointerEventType.Move, Offset(190f, 300f))
                settle()
                screenshot("v3b-held.png")
                drop(layout.wallet.center)
                assertEquals(picked.source, model.physics.flight?.target, "return to the left front slot")
                settle()
                val coinPosition = CoinScatter.place(7, listOf(34f, 34f), layout.coins).first()
                val coinSource = Offset(coinPosition.x, coinPosition.y)
                pick(coinSource)
                val coinId = assertNotNull(model.physics.picked).unit.id
                val left = Offset(40f, 300f)
                drop(left)
                assertEquals(left, model.physics.flight?.target?.center)
                settle()
                assertFalse(model.ui.value.changed, "moving on the floor does not change ownership")
                screenshot("v3-coin-left.png")

                // Saving another bill reflows/recomposes the UI without resetting the floor coin.
                model.setOnTray(model.ui.value.inWallet.first { it.denomination == W5000 }.id, true)
                advanceTimeBy(300); runCurrent(); settle()
                assertEquals(1, data.wallet.state.value.inHand.size)
                pick(left)
                assertEquals(coinId, model.physics.picked?.unit?.id)
                drop(layout.wallet.center)
                assertEquals(left, model.physics.flight?.target?.center, "wallet art springs the coin back to its left floor source")
                settle()
                pick(left)
                assertEquals(coinId, model.physics.picked?.unit?.id)
                drop(layout.trayInner.center)
                advanceTimeBy(300); runCurrent(); settle()
                assertTrue(data.wallet.state.value.inHand.any { it.id == coinId })
                // Tray coins may also return directly to the left floor, using the normal debounce.
                val trayPoint = traySlots(model.ui.value.inHand, layout.trayInner, mapOf(coinId to layout.trayInner.center)).getValue(coinId).center
                pick(trayPoint)
                assertEquals(coinId, model.physics.picked?.unit?.id)
                drop(left)
                advanceTimeBy(300); runCurrent(); settle()
                assertTrue(data.wallet.state.value.inWallet.any { it.id == coinId })
                pick(left)
                assertEquals(coinId, model.physics.picked?.unit?.id)
                pointer(PointerEventType.Release, left)
            } finally { scene.close() }
        } finally { driver.close() }
    }
}
