package com.jim.realcash.ui.walletopen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.ui.components.RcIcon
import com.jim.realcash.ui.components.RcIconGlyph
import com.jim.realcash.ui.format.faceLabel
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.money.BillFace
import com.jim.realcash.ui.money.CoinFace
import com.jim.realcash.ui.money.CountBadge
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.relativeSize
import com.jim.realcash.ui.resources.Res
import com.jim.realcash.ui.resources.tray
import com.jim.realcash.ui.resources.wallet_open
import org.jetbrains.compose.resources.painterResource

@Composable
fun WalletOpenScreen(model: WalletOpenModel, onBack: () -> Unit, onManualPayment: () -> Unit, modifier: Modifier = Modifier) {
    val ui by model.ui.collectAsState()
    val submitting by model.submitting.collectAsState()
    val paymentInfo by model.paymentInfoOpen.collectAsState()
    WalletOpenContent(ui, model.seed, submitting, model::toggle, { model.confirm() }, onBack, model::showPaymentInfo, modifier)
    if (paymentInfo) {
        AlertDialog(
            onDismissRequest = model::closePaymentInfo,
            title = { Text("결제는 평소처럼") },
            text = { Text("삼성페이·토스·카드로 결제하면 알림을 읽어 자동으로 가계부에 들어가요. 안 들어오면 설정 → 알림 접근을 켜 주세요. 직접 기록도 할 수 있어요.") },
            confirmButton = {
                TextButton(onClick = { model.confirm { model.closePaymentInfo(); onManualPayment() } }, enabled = !submitting) { Text("직접 기록") }
            },
            dismissButton = { TextButton(onClick = model::closePaymentInfo) { Text("닫기") } },
        )
    }
}

/** Static wallet composition. Tap is the only cash gesture in this version. */
@Composable
fun WalletOpenContent(
    ui: WalletOpenUi,
    seed: Int,
    submitting: Boolean,
    onToggle: (CashUnitId) -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
    onCalculate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = remember(maxWidth, maxHeight) { WalletOpenLayout.calculate(maxWidth.value, maxHeight.value) }
        val wallet = layout.wallet
        val controlsRight = if (maxWidth > maxHeight) layout.tray.right else maxWidth.value
        val art = LocalMoneyArt.current
        val bills = ui.inWallet.filter { it.denomination.kind == Denomination.Kind.BILL }.groupBy { it.denomination }
        val groups = pocketBillGroups(bills.mapValues { it.value.size }, layout.pocket)
        val walletPainter = painterResource(Res.drawable.wallet_open)
        val coins = ui.inWallet.filter { it.denomination.kind == Denomination.Kind.COIN }
        // Include hand coins in the initial scatter so other coins keep their places after a tap.
        val allCoins = remember(seed, (ui.inWallet + ui.inHand).map { it.id }.toSet()) {
            (ui.inWallet + ui.inHand).filter { it.denomination.kind == Denomination.Kind.COIN }.sortedBy { it.id.value }
        }
        val scatter = remember(seed, layout, allCoins) {
            CoinScatter.place(seed, allCoins.map { 34f * it.denomination.relativeSize }, layout.coins)
        }
        val walletIds = coins.map { it.id }.toSet()
        val visibleCoins = scatter.filter { allCoins[it.index].id in walletIds }
        val overflow = coins.filter { coin -> visibleCoins.none { allCoins[it.index].id == coin.id } }
        var overflowOpen by remember { mutableStateOf(false) }

        Image(walletPainter, "열린 지갑", Modifier.rect(wallet), contentScale = ContentScale.FillBounds)

        IconButton(onClick = onBack, enabled = !submitting, modifier = Modifier.offset(4.dp, 8.dp).semantics { contentDescription = "뒤로" }) {
            RcIconGlyph(RcIcon.BACK)
        }
        Text(
            "손에 ${ui.handTotal.formatted()} · 지갑 ${ui.walletTotal.formatted()}",
            style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 12.dp, top = 24.dp),
        )
        Text("손에 ${ui.handTotal.formatted()}", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.TopEnd).padding(end = (maxWidth * 0.04f), top = 58.dp))
        Image(painterResource(Res.drawable.tray), "꺼낸 돈을 두는 접시", Modifier.rect(layout.tray), contentScale = ContentScale.FillBounds)
        TrayMoney(ui.inHand, layout.trayInner, !submitting, onToggle)
        if (ui.inHand.isEmpty()) {
            Text("돈을 탭해 접시에 놓으세요", style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                modifier = Modifier.rect(layout.trayInner).padding(top = (layout.trayInner.height / 2f - 8f).dp))
        }
        TextButton(onClick = onConfirm, enabled = ui.changed && !submitting,
            modifier = Modifier.offset(x = (controlsRight - 158f).dp, y = (layout.tray.bottom + 2f).dp)) {
            Text(if (submitting) "저장 중" else "확정")
        }
        FilledTonalButton(onClick = onCalculate, modifier = Modifier.offset((controlsRight - 84f).dp, (layout.tray.bottom + 2f).dp).height(40.dp)) { Text("계산") }
        if (wallet.top > layout.tray.bottom + 100f) {
            Text("지폐·동전을 탭해 집거나 돌려놓으세요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().offset(y = (layout.tray.bottom + 58f).dp))
        }

        // Full bill layer. The identical foreground crop below hides everything under the pocket lip.
        groups.forEach { group ->
            val painter = art.painter(group.denomination)
            for (i in 0 until group.visibleCount) {
                Box(Modifier.offset((group.left + i * group.fanStep).dp, group.top.dp).size(group.billWidth.dp, group.billHeight.dp), contentAlignment = Alignment.Center) {
                    BillFace(group.denomination,
                        Modifier.requiredSize(group.billHeight.dp, group.billWidth.dp)
                            .graphicsLayer { rotationZ = 90f }
                            .drawWithContent { drawContent(); drawRect(Color.Black.copy(alpha = ((group.visibleCount - 1 - i) * 0.035f).coerceAtMost(0.28f))) }, painter)
                }
            }
        }
        Image(walletPainter, null,
            Modifier.rect(wallet).drawWithContent { clipRect(top = size.height * 0.405f) { this@drawWithContent.drawContent() } },
            contentScale = ContentScale.FillBounds)
        // Hit targets cover the exposed paper only, so tapping leather never picks an invisible bill.
        groups.forEachIndexed { index, group ->
            val units = bills.getValue(group.denomination)
            Box(Modifier.rect(pocketBillHitBounds(groups, index)).clipToBounds()
                .semantics { contentDescription = "${group.denomination.faceLabel}원 손에 들기" }
                .clickable(enabled = !submitting) { onToggle(units.last().id) })
            if (group.count > group.visibleCount) {
                CountBadge(group.count, Modifier.offset(group.left.dp, (group.top - 22f).dp)
                    .clickable(enabled = !submitting) { onToggle(units.last().id) })
            }
        }
        visibleCoins.forEach { coin ->
            val unit = allCoins[coin.index]
            key(unit.id) { FallingCoin(unit, coin, !submitting, onToggle) }
        }
        if (overflow.isNotEmpty()) {
            TextButton(onClick = { overflowOpen = true }, modifier = Modifier.offset((layout.coins.left - 12f).dp, layout.coins.bottom.dp)) {
                Text("+${overflow.size}", style = MaterialTheme.typography.labelSmall)
            }
        }
        if (overflowOpen) {
            AlertDialog(onDismissRequest = { overflowOpen = false }, title = { Text("남은 동전") }, text = {
                Column {
                    overflow.groupBy { it.denomination }.entries.sortedByDescending { it.key.won }.forEach { (denomination, units) ->
                        TextButton(onClick = { onToggle(units.first().id) }, enabled = !submitting) { Text("${denomination.faceLabel}원 ×${units.size} · 손에 들기") }
                    }
                }
            }, confirmButton = { TextButton(onClick = { overflowOpen = false }) { Text("닫기") } })
        }
    }
}

private fun Modifier.rect(rect: WalletRect) = offset(rect.left.dp, rect.top.dp).size(rect.width.dp, rect.height.dp)

@Composable
private fun FallingCoin(unit: CashUnit, placement: ScatteredCoin, enabled: Boolean, onToggle: (CashUnitId) -> Unit) {
    val fall = remember { Animatable(-140f) }
    LaunchedEffect(Unit) {
        // Use the animation frame clock for the stagger as well as the spring.
        val start = withFrameNanos { it }
        while (withFrameNanos { it } - start < placement.index * 30_000_000L) { /* next frame */ }
        fall.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    CoinFace(unit.denomination,
        Modifier.offset((placement.x - placement.diameter / 2f).dp, (placement.y - placement.diameter / 2f).dp)
            .size(placement.diameter.dp).graphicsLayer { translationY = fall.value.dp.toPx(); rotationZ = placement.rotation }
            .clickable(enabled = enabled, onClickLabel = "손에 들기") { onToggle(unit.id) },
        LocalMoneyArt.current.painter(unit.denomination))
}

@Composable
private fun TrayMoney(units: List<CashUnit>, area: WalletRect, enabled: Boolean, onToggle: (CashUnitId) -> Unit) {
    val bills = units.filter { it.denomination.kind == Denomination.Kind.BILL }
    val coins = units.filter { it.denomination.kind == Denomination.Kind.COIN }
    val billBounds = trayBillBounds(bills.map { it.denomination }, area)
    val billsRight = billBounds.maxOfOrNull { it.right - area.left } ?: 0f
    val coinsStart = if (bills.isEmpty()) 0f else billsRight + 12f
    val contentWidth = maxOf(area.width, coinsStart + coins.sumOf { (34f * it.denomination.relativeSize + 6f).toDouble() }.toFloat())
    Box(Modifier.rect(area).clipToBounds().horizontalScroll(rememberScrollState())) {
        Box(Modifier.width(contentWidth.dp).height(area.height.dp)) {
            bills.forEachIndexed { i, unit ->
                val rect = billBounds[i]
                BillFace(unit.denomination,
                    Modifier.offset((rect.left - area.left).dp, (rect.top - area.top).dp)
                        .size(rect.width.dp, rect.height.dp)
                        .clickable(enabled = enabled, onClickLabel = "지갑에 돌려놓기") { onToggle(unit.id) },
                    LocalMoneyArt.current.painter(unit.denomination))
            }
            var x = coinsStart
            coins.forEach { unit ->
                val diameter = 34f * unit.denomination.relativeSize
                CoinFace(unit.denomination,
                    Modifier.offset(x.dp, ((area.height - diameter) / 2f).dp).size(diameter.dp)
                        .clickable(enabled = enabled, onClickLabel = "지갑에 돌려놓기") { onToggle(unit.id) },
                    LocalMoneyArt.current.painter(unit.denomination))
                x += diameter + 6f
            }
        }
    }
}
