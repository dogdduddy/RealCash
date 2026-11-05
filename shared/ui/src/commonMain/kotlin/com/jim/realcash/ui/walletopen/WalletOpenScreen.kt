package com.jim.realcash.ui.walletopen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.ui.Haptics
import com.jim.realcash.ui.components.RcIcon
import com.jim.realcash.ui.components.RcIconGlyph
import com.jim.realcash.ui.format.faceLabel
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.money.BillFace
import com.jim.realcash.ui.money.CoinFace
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.relativeSize
import com.jim.realcash.ui.resources.Res
import com.jim.realcash.ui.resources.tray
import com.jim.realcash.ui.resources.wallet_open
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.compose.resources.painterResource

@Composable
fun WalletOpenScreen(model: WalletOpenModel, onBack: () -> Unit, onManualPayment: () -> Unit, modifier: Modifier = Modifier) {
    val ui by model.ui.collectAsState()
    val submitting by model.submitting.collectAsState()
    val paymentInfo by model.paymentInfoOpen.collectAsState()
    WalletOpenContent(ui, model.seed, submitting, model::toggle, onBack, model::showPaymentInfo,
        modifier, model.physics, model.haptics, model::setOnTray)
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

@Composable
fun WalletOpenContent(
    ui: WalletOpenUi,
    seed: Int,
    submitting: Boolean,
    onToggle: (CashUnitId) -> Unit,
    onBack: () -> Unit,
    onCalculate: () -> Unit,
    modifier: Modifier = Modifier,
    physics: WalletOpenPhysics = remember { WalletOpenPhysics() },
    haptics: Haptics = Haptics.None,
    onDrop: (CashUnitId, Boolean) -> Unit = { id, _ -> onToggle(id) },
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current.density
    BoxWithConstraints(modifier.fillMaxSize()) {
        val layout = remember(maxWidth, maxHeight) { WalletOpenLayout.calculate(maxWidth.value, maxHeight.value) }
        SideEffect { physics.tray = layout.trayInner }
        val wallet = layout.wallet
        val controlsRight = if (maxWidth > maxHeight) layout.tray.right else maxWidth.value
        val art = LocalMoneyArt.current
        val bills = ui.inWallet.filter { it.denomination.kind == Denomination.Kind.BILL }.groupBy { it.denomination }
        val groups = pocketBillGroups(bills.mapValues { it.value.size }, layout.pocket)
        val walletPainter = painterResource(Res.drawable.wallet_open)
        val coins = ui.inWallet.filter { it.denomination.kind == Denomination.Kind.COIN }
        val allCoins = remember(seed, (ui.inWallet + ui.inHand).map { it.id }.toSet()) {
            (ui.inWallet + ui.inHand).filter { it.denomination.kind == Denomination.Kind.COIN }.sortedBy { it.id.value }
        }
        val scatter = remember(seed, layout, allCoins) {
            CoinScatter.place(seed, allCoins.map { 34f * it.denomination.relativeSize }, layout.coins)
        }
        val floorCoins = remember(seed, layout) { mutableStateMapOf<CashUnitId, WalletRect>() }
        val initialCoinSlots = scatter.associate { coin ->
            allCoins[coin.index].id to WalletRect(coin.x - coin.diameter / 2f, coin.y - coin.diameter / 2f, coin.diameter, coin.diameter)
        }
        val coinSlots = initialCoinSlots + floorCoins
        // Only positions, never ownership. Discarded on the next screen visit.
        val trayCoins = remember(seed) { mutableStateMapOf<CashUnitId, Offset>() }
        val traySlots = traySlots(ui.inHand, layout.trayInner, trayCoins)
        val picked = physics.picked
        val pickedId = picked?.unit?.id // start/end only, not per-pointer coordinates
        val overTray = physics.overTray // edge transitions only
        val hits = buildList {
            groups.forEachIndexed { index, group ->
                add(CashHit(bills.getValue(group.denomination).first(), false, pocketBillHitBounds(groups, index),
                    WalletRect(group.frontLeft, group.top, group.billWidth, group.billHeight), wallet))
            }
            coins.forEach { coin -> coinSlots[coin.id]?.let { add(CashHit(coin, false, it, it)) } }
            ui.inHand.forEach { unit -> traySlots[unit.id]?.let { add(CashHit(unit, true, it, it)) } }
        }
        val sway = groups.flatMap { group ->
            (0 until group.visibleCount).map { i ->
                val animation = key(group.denomination, i) { remember { BillSway(scope) } }
                group.exposedBillBounds(i) to animation
            }
        }
        val currentEnabled = rememberUpdatedState(!submitting)
        val currentHits = rememberUpdatedState(hits)
        val currentPocket = rememberUpdatedState(layout.pocket)
        val currentSway = rememberUpdatedState(sway)
        val release by rememberUpdatedState<(Boolean) -> Unit>({ cancelled ->
            val pick = physics.picked
            if (pick != null && !physics.settling) {
                val floorTarget = if (pick.isBill || cancelled) null else floorCoinTarget(
                    physics.position, pick.faceWidth, WalletRect(0f, 56f, maxWidth.value, maxHeight.value - 56f),
                    wallet, layout.tray, coinSlots.filterKeys { id -> id != pick.unit.id && coins.any { it.id == id } }.values.toList(),
                )
                val destination = when {
                    cancelled -> DropDestination.SOURCE
                    !pick.isBill && layout.trayInner.contains(physics.position) -> DropDestination.TRAY
                    !pick.isBill && floorTarget != null -> DropDestination.FLOOR
                    !pick.isBill -> DropDestination.SOURCE
                    else -> pick.destination(physics.position, layout.trayInner, wallet)
                }
                val target = when (destination) {
                    DropDestination.SOURCE -> pick.source
                    DropDestination.FLOOR -> {
                        trayCoins.remove(pick.unit.id)
                        floorCoins[pick.unit.id] = checkNotNull(floorTarget)
                        floorTarget
                    }
                    DropDestination.TRAY -> {
                        if (!pick.isBill) trayCoins[pick.unit.id] = physics.position
                        val units = if (pick.fromTray) ui.inHand else ui.inHand + pick.unit
                        traySlots(units, layout.trayInner, trayCoins).getValue(pick.unit.id)
                    }
                    DropDestination.WALLET -> {
                        trayCoins.remove(pick.unit.id)
                        if (pick.isBill) {
                            val counts = (ui.inWallet + pick.unit).filter { it.denomination.kind == Denomination.Kind.BILL }.groupingBy { it.denomination }.eachCount()
                            val group = pocketBillGroups(counts, layout.pocket).first { it.denomination == pick.unit.denomination }
                            WalletRect(group.frontLeft, group.top, group.billWidth, group.billHeight)
                        } else coinSlots[pick.unit.id] ?: pick.source
                    }
                }
                if (destination == DropDestination.SOURCE) haptics.tap() else {
                    haptics.success()
                    if (destination != DropDestination.FLOOR || pick.fromTray)
                        onDrop(pick.unit.id, destination == DropDestination.TRAY)
                }
                physics.settle(pick.flight(destination, target))
            }
        })
        // Full-screen gesture surface has no scrolling. Controls remain ordinary buttons above it.
        Box(Modifier.fillMaxSize().walletGestures(currentEnabled, density, currentHits, currentPocket, currentSway, physics, haptics) { release(it) }) {
            Image(walletPainter, "열린 지갑", Modifier.rect(wallet), contentScale = ContentScale.Fit)
            Image(painterResource(Res.drawable.tray), "꺼낸 돈을 두는 접시",
                Modifier.rect(layout.tray).graphicsLayer { alpha = if (overTray) 1f else 0.94f }, contentScale = ContentScale.FillBounds)
            ui.inHand.forEach { unit ->
                val rect = traySlots.getValue(unit.id)
                key(unit.id) {
                    Money(unit, Modifier.rect(rect).graphicsLayer { alpha = if (unit.id == pickedId) 0f else 1f }
                        .cashAction("${unit.denomination.faceLabel}원 지갑에 돌려놓기", !submitting) { onToggle(unit.id) })
                }
            }
            if (ui.inHand.isEmpty()) Text(
                if (overTray) "여기에 놓으세요" else "돈을 길게 눌러 접시에 놓으세요",
                style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f), textAlign = TextAlign.Center,
                modifier = Modifier.rect(layout.trayInner).padding(top = (layout.trayInner.height / 2f - 8f).dp))
            // Clip paper to the reviewed lens; the wallet artwork itself is always drawn whole.
            Box(Modifier.fillMaxSize().drawWithContent {
                val l = wallet.left.dp.toPx()
                val t = wallet.top.dp.toPx()
                val w = wallet.width.dp.toPx()
                val h = wallet.height.dp.toPx()
                val midX = (WalletOpening.LEFT + WalletOpening.RIGHT) / 2f
                val lens = Path().apply {
                    moveTo(l + w * midX, t + h * WalletOpening.TOP)
                    quadraticTo(l + w * (2f * WalletOpening.RIGHT - midX), t + h * (WalletOpening.TOP + WalletOpening.BOTTOM) / 2f,
                        l + w * midX, t + h * WalletOpening.BOTTOM)
                    quadraticTo(l + w * (2f * WalletOpening.LEFT - midX), t + h * (WalletOpening.TOP + WalletOpening.BOTTOM) / 2f,
                        l + w * midX, t + h * WalletOpening.TOP)
                    close()
                }
                clipPath(lens) { this@drawWithContent.drawContent() }
            }) {
                var index = 0
                groups.forEach { group ->
                    val painter = art.painter(group.denomination)
                    val frontPicked = bills.getValue(group.denomination).first().id == pickedId
                    for (i in group.drawOrder(frontPicked)) {
                        val animation = sway[index + i].second
                        key(group.denomination, i) {
                            BillFace(group.denomination,
                                Modifier.offset((group.left + i * group.fanStep + group.exposedWidth).dp, group.top.dp)
                                    .size(group.billHeight.dp, group.billWidth.dp)
                                    .graphicsLayer {
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        rotationZ = 90f // Fixed portrait orientation; swipe never rotates around Z.
                                        cameraDistance = 10_000.dp.toPx()
                                        translationX = animation.displacementDp.dp.toPx()
                                    }.graphicsLayer {
                                        transformOrigin = TransformOrigin(0f, 0f)
                                        rotationX = SwayState.rotationX(animation.displacementDp)
                                        cameraDistance = 10_000.dp.toPx()
                                    }.drawWithContent {
                                        clipRect(bottom = minOf(size.height * WalletOpening.FACE_FRACTION, group.exposedWidth.dp.toPx() * 2f)) {
                                            this@drawWithContent.drawContent()
                                            drawRect(Color.Black.copy(alpha = (i * 0.035f).coerceAtMost(0.28f)))
                                        }
                                    }, painter)
                        }
                    }
                    index += group.visibleCount
                }
            }
            groups.forEachIndexed { i, group ->
                Box(Modifier.rect(pocketBillHitBounds(groups, i)).cashAction("${group.denomination.faceLabel}원 손에 들기", !submitting) {
                    onToggle(bills.getValue(group.denomination).first().id)
                })
            }
            allCoins.forEachIndexed { index, unit ->
                val rect = coinSlots[unit.id] ?: return@forEachIndexed
                val coin = scatter.firstOrNull { it.index == index }
                    ?: ScatteredCoin(index, rect.center.x, rect.center.y, rect.width, 0f)
                key(unit.id) {
                    FallingCoin(unit, coin.copy(x = rect.center.x, y = rect.center.y),
                        unit !in coins || unit.id == pickedId, !submitting && unit in coins) { onToggle(unit.id) }
                }
            }
        }
        IconButton(onClick = onBack, enabled = !submitting && pickedId == null,
            modifier = Modifier.offset(4.dp, 8.dp).semantics { contentDescription = "뒤로" }) { RcIconGlyph(RcIcon.BACK) }
        Text("손에 ${ui.handTotal.formatted()} · 지갑 ${ui.walletTotal.formatted()}", style = MaterialTheme.typography.labelLarge,
            maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(start = 52.dp, end = 12.dp, top = 24.dp))
        Text(if (submitting) "저장 중" else if (ui.changed) "접시 저장 대기 중" else "접시 자동 저장",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.offset(12.dp, (layout.tray.bottom + 14f).dp))
        FilledTonalButton(onClick = onCalculate, enabled = pickedId == null,
            modifier = Modifier.offset((controlsRight - 84f).dp, (layout.tray.bottom + 2f).dp).height(40.dp)) { Text("계산") }
        if (wallet.top > layout.tray.bottom + 100f) Text("길게 눌러 집고 · 밀어서 흔들어 보세요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().offset(y = (layout.tray.bottom + 58f).dp))
        val overflow = coins.filter { it.id !in coinSlots }
        var overflowOpen by remember { mutableStateOf(false) }
        if (overflow.isNotEmpty()) TextButton(onClick = { overflowOpen = true },
            modifier = Modifier.offset((layout.coins.left - 12f).dp, layout.coins.bottom.dp)) { Text("+${overflow.size}") }
        if (overflowOpen) AlertDialog(onDismissRequest = { overflowOpen = false }, title = { Text("남은 동전") }, text = {
            Column { overflow.groupBy { it.denomination }.forEach { (d, units) ->
                TextButton(onClick = { onToggle(units.first().id) }, enabled = !submitting) { Text("${d.faceLabel}원 ×${units.size} · 손에 들기") }
            } }
        }, confirmButton = { TextButton(onClick = { overflowOpen = false }) { Text("닫기") } })
        if (picked != null) key(picked.unit.id) { PickGhost(physics, picked) }
    }
}

private fun Modifier.rect(rect: WalletRect) = offset(rect.left.dp, rect.top.dp).size(rect.width.dp, rect.height.dp)
private fun Modifier.cashAction(label: String, enabled: Boolean, action: () -> Unit) = semantics {
    contentDescription = label
    if (enabled) onClick(label) { action(); true }
}

@Composable
private fun Money(unit: CashUnit, modifier: Modifier) {
    val painter = LocalMoneyArt.current.painter(unit.denomination)
    if (unit.denomination.kind == Denomination.Kind.BILL) BillFace(unit.denomination, modifier, painter)
    else CoinFace(unit.denomination, modifier, painter)
}

/** Keep every bill inside the tray, compressing the overlap pitch when it fills up. */
internal fun traySlots(units: List<CashUnit>, area: WalletRect, placedCoins: Map<CashUnitId, Offset>): Map<CashUnitId, WalletRect> {
    val bills = units.filter { it.denomination.kind == Denomination.Kind.BILL }
    val bounds = trayBillBounds(bills.map { it.denomination }, area)
    val largest = bounds.maxOfOrNull { it.width } ?: 0f
    val pitch = if (bills.size <= 1) 0f else minOf(24f, ((area.width - largest) / (bills.size - 1)).coerceAtLeast(0f))
    return buildMap {
        bills.forEachIndexed { i, unit -> put(unit.id, bounds[i].copy(left = area.left + i * pitch)) }
        val coins = units.filter { it.denomination.kind == Denomination.Kind.COIN }
        coins.forEachIndexed { i, unit ->
            val size = 34f * unit.denomination.relativeSize
            val point = placedCoins[unit.id] ?: Offset(area.right - size / 2f - (i * 22f) % (area.width - size).coerceAtLeast(1f), area.bottom - size / 2f)
            put(unit.id, WalletRect(point.x - size / 2f, point.y - size / 2f, size, size))
        }
    }
}

@Composable
private fun FallingCoin(unit: CashUnit, placement: ScatteredCoin, hidden: Boolean, enabled: Boolean, action: () -> Unit) {
    val fall = remember { Animatable(-140f) }
    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (withFrameNanos { it } - start < placement.index * 30_000_000L) { /* frame-clock stagger */ }
        fall.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Money(unit, Modifier.offset((placement.x - placement.diameter / 2f).dp, (placement.y - placement.diameter / 2f).dp)
        .size(placement.diameter.dp).graphicsLayer {
            translationY = fall.value.dp.toPx(); rotationZ = placement.rotation; alpha = if (hidden) 0f else 1f
        }.then(if (hidden) Modifier else Modifier.cashAction("${unit.denomination.faceLabel}원 손에 들기", enabled, action)))
}

@Composable
private fun PickGhost(physics: WalletOpenPhysics, pick: PickState) {
    val lift = remember { Animatable(1f) }
    val progress = remember { Animatable(0f) }
    val settling = physics.settling
    LaunchedEffect(Unit) { lift.animateTo(PickState.LIFT_SCALE, spring(0.65f, 400f)) }
    LaunchedEffect(settling) {
        if (settling) {
            if (physics.flight?.returning == true) {
                withTimeoutOrNull(PickState.RETURN_MILLIS) { progress.animateTo(1f, spring(0.75f, 600f)) }
                progress.snapTo(1f)
            } else progress.animateTo(1f, spring(0.55f, 180f))
            physics.clear()
        }
    }
    Money(pick.unit, Modifier.requiredSize(pick.faceWidth.dp, pick.faceHeight.dp).graphicsLayer {
        val p = progress.value
        val flight = physics.flight
        val heldCenter = pick.centerAt(physics.position)
        val target = flight?.target
        val center = if (target == null) heldCenter else heldCenter + (target.center - heldCenter) * p
        translationX = (center.x - pick.faceWidth / 2f).dp.toPx()
        translationY = (center.y - pick.faceHeight / 2f).dp.toPx()
        rotationZ = pick.rotation + ((flight?.rotation ?: pick.rotation) - pick.rotation) * p
        val targetW = if (flight?.rotation == 90f) target?.height else target?.width
        val targetH = if (flight?.rotation == 90f) target?.width else target?.height
        scaleX = lift.value + ((targetW ?: pick.faceWidth) / pick.faceWidth - lift.value) * p
        scaleY = lift.value + ((targetH ?: pick.faceHeight) / pick.faceHeight - lift.value) * p
        shadowElevation = (18f * (1f - p.coerceIn(0f, 1f)) + 2f).dp.toPx()
    }.semantics { contentDescription = "${pick.unit.denomination.faceLabel}원 집어서 이동 중" })
}
