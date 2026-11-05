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
    @Test fun contactMovesOnlyTouchedPaperThenSpringsToRestAndTicksOnce() = runTest {
        lateinit var papers: List<Pair<WalletRect, BillSway>>
        val scene = ImageComposeScene(200, 100, Density(1f), coroutineContext = coroutineContext) {
            val scope = rememberCoroutineScope()
            papers = remember { List(3) { WalletRect(it * 50f, 0f, 30f, 80f) to BillSway(scope) } }
            papers.forEach { (_, animation) -> Box(Modifier.size(10.dp).graphicsLayer { translationX = animation.displacementDp }) }
        }
        var time = 0L
        fun frame() { Snapshot.sendApplyNotifications(); scene.render(time); time += 16_000_000L }
        var ticks = 0
        val gesture = BillSwayGesture { ticks++ }
        try {
            frame(); runCurrent()
            gesture.move(Offset(55f, 30f), Offset(60f, 30f), papers)
            assertEquals(5f, papers[1].second.displacementDp)
            assertEquals(0f, papers[0].second.displacementDp)
            assertEquals(0f, papers[2].second.displacementDp)
            gesture.move(Offset(60f, 30f), Offset(70f, 30f), papers)
            assertEquals(8f, papers[1].second.displacementDp)
            gesture.move(Offset(70f, 30f), Offset(51f, 30f), papers)
            assertEquals(-2f, papers[1].second.displacementDp)
            assertEquals(1, ticks)
            gesture.release(papers)
            var overshot = false
            repeat(100) { frame(); runCurrent(); if (papers[1].second.displacementDp > 0f) overshot = true }
            assertTrue(overshot)
            assertTrue(papers.all { kotlin.math.abs(it.second.displacementDp) < 0.01f })
        } finally { scene.close() }
    }

    @Test fun liftingOrCancellingReturnsTouchedPaperWithoutMovingUntouchedPapers() = runTest {
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
            assertEquals(8f, papers[0].second.displacementDp)
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
            val left = groups.first().openingX + 1f
            val right = groups.last().right + 1f
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
            assertEquals(30, ticks, "one selection tick per visible bill crossed")
            assertEquals(baseline, compositions, "spring frames must not recompose the cash artwork")
            scene.sendPointerEvent(PointerEventType.Release, Offset(right, y) * 2f, timeMillis = 1_336L)
            repeat(90) { frame() }
            assertEquals(baseline, compositions, "return-to-rest springs also use deferred layer reads")
        } finally { scene.close() }
    }
}
