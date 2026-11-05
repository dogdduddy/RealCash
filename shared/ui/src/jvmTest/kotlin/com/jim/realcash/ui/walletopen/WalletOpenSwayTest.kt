package com.jim.realcash.ui.walletopen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.unit.Density
import com.jim.realcash.domain.model.*
import com.jim.realcash.domain.model.Denomination.*
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.MoneyArt
import com.jim.realcash.ui.theme.RealCashTheme
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.EncodedImageFormat

@OptIn(ExperimentalCoroutinesApi::class)
class WalletOpenSwayTest {
    @Test fun contactMovesOnlyOnePaperAndReleasePulsesAcrossGroupsThenSpringsToRest() = runTest {
        val counts = mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5)
        val groups = pocketBillGroups(counts, WalletOpenLayout.calculate(390f, 844f).pocket)
        val strips = groups.flatMap { group -> (0 until group.visibleCount).map { group.exposedBillBounds(it) } }
        lateinit var papers: List<Pair<WalletRect, BillSway>>
        val scene = ImageComposeScene(390, 844, Density(1f), coroutineContext = coroutineContext) {
            val scope = rememberCoroutineScope()
            papers = remember { strips.map { it to BillSway(scope) } }
            papers.forEach { (_, animation) ->
                Box(Modifier.size(10.dp).graphicsLayer { translationX = animation.displacementDp })
            }
        }
        var time = 0L
        fun frame() { Snapshot.sendApplyNotifications(); scene.render(time); time += 16_000_000L }
        var ticks = 0
        val gesture = BillSwayGesture { ticks++ }
        try {
            frame(); runCurrent()
            // Last 10,000-won back -> first 5,000-won front crosses a denomination boundary.
            val index = 3
            val strip = strips[index]
            val y = strip.bottom - 1f // Below the shorter next denomination: it receives a free pulse.
            val limit = SwayState.limit(strips[index + 1].right - strip.right)
            val held = Offset(strip.right + limit * 0.75f, y)
            gesture.move(Offset(strip.left + 0.1f, y), held, papers)
            assertEquals(limit * 0.75f, papers[index].second.displacementDp, 0.0001f)
            assertTrue(papers.filterIndexed { i, _ -> i != index }.all { it.second.displacementDp == 0f })
            gesture.move(held, Offset(strip.right + limit + 0.1f, y), papers)
            runCurrent(); frame(); runCurrent()
            val peak = limit
            assertEquals(peak, papers[index].second.displacementDp, 0.0001f)
            advanceTimeBy(19); runCurrent()
            assertEquals(0f, papers[index + 1].second.displacementDp)
            advanceTimeBy(1); runCurrent()
            assertEquals(peak / 2f, papers[index + 1].second.displacementDp, 0.0001f)
            assertEquals(0f, papers[index + 2].second.displacementDp)
            advanceTimeBy(20); runCurrent()
            assertEquals(peak / 4f, papers[index + 2].second.displacementDp, 0.0001f)
            assertTrue(papers.drop(index + 3).all { it.second.displacementDp == 0f })
            var overshot = false
            repeat(100) {
                frame(); runCurrent()
                if (papers[index].second.displacementDp < 0f) overshot = true
            }
            assertTrue(overshot, "the released paper should bounce slightly past rest")
            assertTrue(papers.all { kotlin.math.abs(it.second.displacementDp) < 0.01f })
            assertEquals(1, ticks)
        } finally { scene.close() }
    }

    @Test fun liftingOrCancellingReturnsHeldPaperAndCancelsDelayedPulses() = runTest {
        lateinit var papers: List<Pair<WalletRect, BillSway>>
        val scene = ImageComposeScene(100, 100, Density(1f), coroutineContext = coroutineContext) {
            val scope = rememberCoroutineScope()
            papers = remember { List(4) { WalletRect(it * 20f, 0f, 10f, 80f) to BillSway(scope) } }
            papers.forEach { (_, animation) -> Box(Modifier.size(10.dp).graphicsLayer { translationX = animation.displacementDp }) }
        }
        var time = 0L
        fun frame() { Snapshot.sendApplyNotifications(); scene.render(time); time += 16_000_000L }
        val gesture = BillSwayGesture {}
        try {
            frame(); runCurrent()
            gesture.move(Offset(5f, 30f), Offset(15f, 30f), papers)
            assertEquals(5f, papers[0].second.displacementDp)
            papers[2].second.pulse(SwayPulse(2, SwayState(2f), 20))
            gesture.release(papers)
            runCurrent(); advanceTimeBy(40); runCurrent()
            assertEquals(0f, papers[2].second.displacementDp)
            repeat(100) { frame(); runCurrent() }
            assertTrue(papers.all { kotlin.math.abs(it.second.displacementDp) < 0.01f })
        } finally { scene.close() }
    }

    @Test fun swipingThirtyBillsTicksWithoutRecomposingCashOrPickingAnything() = runBlocking {
        val counts = mapOf(W10000 to 30)
        val units = counts.flatMap { (d, count) -> List(count) { i ->
            CashUnit(CashUnitId("$d-$i"), d, CashUnitState.IN_WALLET,
                Instant.parse("2026-11-04T09:00:00Z"), CashUnitSource.ALLOWANCE)
        } }
        var compositions = 0
        var ticks = 0
        var pickups = 0
        val physics = WalletOpenPhysics()
        val art = object : MoneyArt {
            @Composable override fun painter(denomination: Denomination): Painter? {
                SideEffect { compositions++ }
                return null
            }
        }
        val haptics = object : Haptics {
            override fun tap() { ticks++ }
            override fun pickUp() { pickups++ }
            override fun success() = Unit
        }
        val scene = ImageComposeScene(780, 1688, Density(2f), coroutineContext = coroutineContext) {
            RealCashTheme { CompositionLocalProvider(LocalMoneyArt provides art) {
                WalletOpenContent(WalletOpenUi(units, emptyList(), false), 1, false,
                    { fail("a swipe must not change ownership") }, {}, {}, physics = physics, haptics = haptics)
            } }
        }
        var time = 0L
        fun frame() { scene.render(time); time += 16_000_000L }
        try {
            repeat(10) { frame() }
            val baseline = compositions
            val before = checkNotNull(scene.render(time).encodeToData(EncodedImageFormat.PNG)).bytes
            val layout = WalletOpenLayout.calculate(390f, 844f)
            val groups = pocketBillGroups(counts, layout.pocket)
            val left = groups.first().left + 1f
            val right = groups.last().right - 1f
            val y = groups.first().top + groups.first().exposedHeight / 2f
            scene.sendPointerEvent(PointerEventType.Press, Offset(left, y) * 2f, timeMillis = 1_000L)
            for (step in 1..20) {
                delay(16)
                scene.sendPointerEvent(PointerEventType.Move, Offset(left + (right - left) * step / 20f, y) * 2f, timeMillis = 1_000L + step * 16L)
                frame()
            }
            val during = checkNotNull(scene.render(time).encodeToData(EncodedImageFormat.PNG)).bytes
            assertFalse(before.contentEquals(during), "the springs must visibly sway the paper")
            assertEquals(0, pickups)
            assertNull(physics.picked)
            assertEquals(groups.sumOf { it.visibleCount }, ticks, "one selection tick per visible bill crossed")
            assertEquals(baseline, compositions, "spring frames must not recompose the cash artwork")
            scene.sendPointerEvent(PointerEventType.Release, Offset(right, y) * 2f, timeMillis = 1_336L)
            repeat(90) { frame() }
            assertEquals(baseline, compositions, "return-to-rest springs also use deferred layer reads")
        } finally { scene.close() }
    }
}
