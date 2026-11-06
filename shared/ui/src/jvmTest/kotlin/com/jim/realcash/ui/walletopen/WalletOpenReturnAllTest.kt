package com.jim.realcash.ui.walletopen

import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.graphics.asComposeCanvas
import androidx.compose.ui.platform.PlatformContext
import androidx.compose.ui.scene.CanvasLayersComposeScene
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsOwner
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getAllSemanticsNodes
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.Denomination.*
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.ResourceMoneyArt
import com.jim.realcash.ui.theme.RealCashTheme
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Instant
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Surface as SkiaSurface

@OptIn(ExperimentalComposeUiApi::class, InternalComposeUiApi::class, ExperimentalCoroutinesApi::class)
class WalletOpenReturnAllTest {
    @Test
    fun returnButtonSemanticsAnimationAndReviewScreenshot() = runTest {
        val cash = mapOf(W50000 to 1, W10000 to 3, W5000 to 2, W1000 to 5, C500 to 2, C100 to 3)
            .flatMap { (denomination, count) -> List(count) { index ->
                CashUnit(CashUnitId("$denomination-$index"), denomination, CashUnitState.IN_WALLET,
                    Instant.parse("2026-11-06T09:00:00Z"), CashUnitSource.ALLOWANCE)
            } }
        val ui = mutableStateOf(WalletOpenUi(cash, emptyList(), false))
        val submitting = mutableStateOf(false)
        val physics = WalletOpenPhysics()
        var returns = 0
        var owner: SemanticsOwner? = null
        val platform = object : PlatformContext by PlatformContext.Empty {
            override val semanticsOwnerListener = object : PlatformContext.SemanticsOwnerListener {
                override fun onSemanticsOwnerAppended(semanticsOwner: SemanticsOwner) { owner = semanticsOwner }
                override fun onSemanticsOwnerRemoved(semanticsOwner: SemanticsOwner) { owner = null }
                override fun onSemanticsChange(semanticsOwner: SemanticsOwner) = Unit
                override fun onLayoutChange(semanticsOwner: SemanticsOwner, semanticsNodeId: Int) = Unit
            }
        }
        val scene = CanvasLayersComposeScene(density = Density(1f), size = IntSize(390, 844),
            coroutineContext = coroutineContext, platformContext = platform)
        val surface = SkiaSurface.makeRasterN32Premul(390, 844)
        scene.setContent {
            RealCashTheme(darkTheme = false) {
                CompositionLocalProvider(LocalMoneyArt provides ResourceMoneyArt) {
                    Surface {
                        WalletOpenContent(ui.value, 7, submitting.value, {}, {}, {}, physics = physics,
                            onReturnAll = {
                                returns++
                                ui.value = WalletOpenUi(ui.value.inWallet + ui.value.inHand, emptyList(), true)
                            })
                    }
                }
            }
        }
        var time = 0L
        fun frame() {
            runCurrent()
            Snapshot.sendApplyNotifications()
            scene.render(surface.canvas.asComposeCanvas(), time)
            time += 16_000_000L
            runCurrent()
        }
        fun textNode(text: String): SemanticsNode? = assertNotNull(owner).getAllSemanticsNodes(true)
            .singleOrNull { node -> node.config.getOrNull(SemanticsProperties.Text)?.any { it.text == text } == true }
        fun returnButton() = assertNotNull(textNode("모두 넣기"))
        try {
            repeat(100) { frame() }
            assertNull(textNode("모두 넣기"), "empty trays have no return-all button in semantics")
            assertNotNull(textNode("돈을 길게 눌러 접시에 놓으세요"))
            val hand = listOf(W10000, W5000, C500).map { d -> cash.first { it.denomination == d } }
            ui.value = WalletOpenUi(cash - hand.toSet(), hand, false)
            repeat(5) { frame() }
            assertFalse(returnButton().config.contains(SemanticsProperties.Disabled))
            assertNull(textNode("돈을 길게 눌러 접시에 놓으세요"))
            surface.makeImageSnapshot().use { image ->
                val png = checkNotNull(image.encodeToData(EncodedImageFormat.PNG)).bytes
                val dir = File(System.getProperty("realcash.screenshotDir") ?: "build/screenshots").apply { mkdirs() }
                File(dir, "t1-tray-return.png").writeBytes(png)
                val decoded = ImageIO.read(png.inputStream())
                assertEquals(390, decoded.width)
                assertEquals(844, decoded.height)
            }
            submitting.value = true
            repeat(3) { frame() }
            assertTrue(returnButton().config.contains(SemanticsProperties.Disabled))
            submitting.value = false
            val rect = traySlots(hand, WalletOpenLayout.calculate(390f, 844f).trayInner, emptyMap()).getValue(hand.first().id)
            physics.start(PickState(hand.first(), true, rect, rect.center))
            repeat(3) { frame() }
            assertTrue(returnButton().config.contains(SemanticsProperties.Disabled))
            physics.clear()
            repeat(3) { frame() }
            assertTrue(returnButton().config[SemanticsActions.OnClick].action!!.invoke())
            repeat(12) { frame() }
            assertEquals(0, returns, "the tray remains until all staggered exits finish")
            assertEquals(3, ui.value.inHand.size)
            assertTrue(returnButton().config.contains(SemanticsProperties.Disabled))
            repeat(12) { frame() }
            assertEquals(1, returns)
            assertTrue(ui.value.inHand.isEmpty())
            assertNull(textNode("모두 넣기"))
            assertNotNull(textNode("돈을 길게 눌러 접시에 놓으세요"))
        } finally {
            scene.close()
            surface.close()
        }
    }
}
