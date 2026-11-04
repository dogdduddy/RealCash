package com.jim.realcash.ui.takeout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.ScreenHeader
import com.jim.realcash.ui.components.SectionLabel
import com.jim.realcash.ui.format.ThousandsSeparatorTransformation
import com.jim.realcash.ui.format.faceLabel
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.money.billAspectRatio
import com.jim.realcash.ui.money.relativeSize
import com.jim.realcash.ui.money.BillStack
import com.jim.realcash.ui.money.CoinStack
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.MoneyFace
import com.jim.realcash.ui.money.UnitChip
import com.jim.realcash.ui.theme.LocalRealCashColors
import com.jim.realcash.ui.wallet.DenominationCount
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop

/** Alpha of a stack with nothing left to pick. */
private const val EMPTY_STACK_ALPHA = 0.32f

/** Size of the ghost that follows the finger. */
private val GhostBillWidth = 128.dp
private val GhostCoinSize = 48.dp

/**
 * 꺼내기 (screen 3): optional expected amount with live feedback, the hand zone (drop target) and the wallet stacks
 * that can be dragged into it or tapped, two pick helpers and "손에 들기".
 */
@Composable
fun TakeOutScreen(model: TakeOutModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val draft by model.draft.collectAsState()
    val submitting by model.submitting.collectAsState()
    LaunchedEffect(model) { model.syncWithWallet() }
    TakeOutContent(
        draft = draft,
        drag = model.drag,
        refill = model.refill,
        submitting = submitting,
        onBack = onBack,
        onExpectedChange = model::onExpectedChange,
        onTapAdd = model::tapAdd,
        onDragStart = model::onDragStart,
        onEnterZone = model::onEnterZone,
        onDrop = model::drop,
        onPutBack = model::putBack,
        onFitExact = model::fitExact,
        onFewest = model::fewest,
        onConfirm = model::confirm,
        modifier = modifier,
    )
}

/** Stateless body of [TakeOutScreen]; [drag] is the hoisted drag & drop state. */
@Composable
fun TakeOutContent(
    draft: TakeOutDraft,
    drag: DragDropState,
    refill: Boolean,
    submitting: Boolean,
    onBack: () -> Unit,
    onExpectedChange: (String) -> Unit,
    onTapAdd: (Denomination) -> Unit,
    onDragStart: () -> Unit,
    onEnterZone: () -> Unit,
    onDrop: (Denomination) -> Unit,
    onPutBack: (Denomination) -> Unit,
    onFitExact: () -> Unit,
    onFewest: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enterZone by rememberUpdatedState(onEnterZone)
    // Haptic on the edge only: isOverZone is a derivedStateOf, observed here without recomposing anything.
    LaunchedEffect(drag) {
        snapshotFlow { drag.isOverZone }.distinctUntilChanged().drop(1).collect { if (it) enterZone() }
    }
    // The ghost is translated relative to this full-size box, so its root origin is recorded.
    Box(modifier.fillMaxSize().onGloballyPositioned { drag.hostOrigin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(if (refill) Strings.TAKE_MORE_TITLE else Strings.TAKE_OUT_TITLE, onBack = onBack)
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (refill) {
                    Text(Strings.REFILL_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = draft.expectedDigits,
                    onValueChange = onExpectedChange,
                    label = { Text(Strings.EXPECTED_AMOUNT) },
                    placeholder = { Text(Strings.EXPECTED_AMOUNT_HINT) },
                    prefix = { Text("₩") },
                    singleLine = true,
                    visualTransformation = ThousandsSeparatorTransformation,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.fillMaxWidth(),
                )
                FeedbackLine(draft)
                HandZone(draft, drag, onPutBack)
                Helpers(draft, onFitExact, onFewest)
                SectionLabel(Strings.WALLET, trailing = (draft.availableTotal - draft.pickedTotal).formatted())
                WalletPalette(draft, drag, onTapAdd, onDragStart, onDrop)
                Spacer(Modifier.height(8.dp))
            }
            Button(
                onClick = onConfirm,
                enabled = draft.canConfirm && !submitting,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
            ) {
                val label = if (draft.canConfirm) "${Strings.HOLD_IN_HAND} · ${draft.pickedTotal.formatted()}" else Strings.HOLD_IN_HAND
                Text(label, style = MaterialTheme.typography.titleMedium)
            }
        }
        // Drawn last so it floats above everything; composed only while a drag is in progress.
        drag.dragged?.let { DragGhost(it, drag) }
    }
}

@Composable
private fun FeedbackLine(draft: TakeOutDraft) {
    val expected = draft.expected ?: return
    val extra = LocalRealCashColors.current
    val (text, color) = when (val f = draft.feedback) {
        TakeOutFeedback.None -> return
        TakeOutFeedback.Exact -> Strings.FEEDBACK_EXACT to extra.positive
        is TakeOutFeedback.Short -> {
            val t = if (f.walletTotal != null) {
                Strings.feedbackShortWallet(f.walletTotal.formatted(), f.missing.formatted())
            } else {
                Strings.feedbackShort(f.missing.formatted())
            }
            t to extra.warning
        }
        is TakeOutFeedback.Over -> Strings.feedbackOver(f.over.formatted(), f.changeTotal.formatted()) to
            MaterialTheme.colorScheme.onSurface
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(
            Strings.pickedVsExpected(draft.pickedTotal.formatted(), expected.formatted()),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = color)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandZone(draft: TakeOutDraft, drag: DragDropState, onPutBack: (Denomination) -> Unit) {
    val art = LocalMoneyArt.current
    val scheme = MaterialTheme.colorScheme
    // Both reads change only at drag start/end and on the zone's edge.
    val over = drag.isOverZone
    val dragging = drag.dragged != null
    val shape = MaterialTheme.shapes.large
    val background = if (over) scheme.primaryContainer else scheme.surfaceContainer
    val border = when {
        over -> scheme.primary
        dragging -> scheme.primary.copy(alpha = 0.45f)
        else -> Color.Transparent
    }
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 128.dp)
            .background(background, shape)
            .border(2.dp, border, shape)
            .onGloballyPositioned { drag.zoneBounds = it.boundsInRoot() }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (over) Strings.HAND_ZONE_DROP else Strings.IN_HAND_TITLE,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = if (over) scheme.primary else scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                draft.pickedTotal.formatted(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSurface,
            )
        }
        if (draft.pickedCounts.isEmpty()) {
            Text(Strings.HAND_ZONE_EMPTY, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.pickedCounts.forEach { c ->
                    UnitChip(
                        c.denomination,
                        c.count,
                        painter = art.painter(c.denomination),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onPutBack(c.denomination) }
                            .semantics { contentDescription = "${c.denomination.faceLabel}원 ${c.count}장, 눌러서 지갑에 넣기" },
                    )
                }
            }
            Text(Strings.HAND_ZONE_TAP_HINT, style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Helpers(draft: TakeOutDraft, onFitExact: () -> Unit, onFewest: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onFitExact, enabled = draft.helpersEnabled, modifier = Modifier.weight(1f)) {
                Text(Strings.FIT_EXACT, maxLines = 1)
            }
            OutlinedButton(onClick = onFewest, enabled = draft.helpersEnabled, modifier = Modifier.weight(1f)) {
                Text(Strings.FEWEST, maxLines = 1)
            }
        }
        val note = when (val h = draft.helper) {
            null -> null
            is TakeOutHelperResult.Exact -> Strings.fitExactDone(h.total.formatted())
            is TakeOutHelperResult.NotExact -> Strings.fitNotExact(h.total.formatted())
            is TakeOutHelperResult.Fewest -> Strings.fewestDone(h.count, h.total.formatted())
            is TakeOutHelperResult.Shortfall -> Strings.helperShortfall(h.shortfall.formatted())
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WalletPalette(
    draft: TakeOutDraft,
    drag: DragDropState,
    onTapAdd: (Denomination) -> Unit,
    onDragStart: () -> Unit,
    onDrop: (Denomination) -> Unit,
) {
    val art = LocalMoneyArt.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        draft.remainingBills.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { c ->
                    DraggableUnit(c, drag, onTapAdd, onDragStart, onDrop, Modifier.weight(1f)) { onClick ->
                        BillStack(c.denomination, c.count, painter = art.painter(c.denomination), onClick = onClick)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (draft.remainingCoins.isNotEmpty()) {
            SectionLabel(Strings.COINS)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                draft.remainingCoins.forEach { c ->
                    DraggableUnit(c, drag, onTapAdd, onDragStart, onDrop) { onClick ->
                        CoinStack(c.denomination, c.count, painter = art.painter(c.denomination), onClick = onClick)
                    }
                }
            }
        }
    }
}

/**
 * Wraps one stack with HardPay's drag source: `detectDragGestures` reports the start in root coordinates
 * (stack origin + local offset) and deltas to [drag]; tapping stays available as the fallback. A stack with
 * nothing left is dimmed and neither draggable nor tappable.
 */
@Composable
private fun DraggableUnit(
    count: DenominationCount,
    drag: DragDropState,
    onTapAdd: (Denomination) -> Unit,
    onDragStart: () -> Unit,
    onDrop: (Denomination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (onClick: (() -> Unit)?) -> Unit,
) {
    val d = count.denomination
    val enabled = count.count > 0
    val start by rememberUpdatedState(onDragStart)
    val dropped by rememberUpdatedState(onDrop)
    // Written on every layout pass, read only inside the gesture callback (never during composition).
    var origin by remember { mutableStateOf(Offset.Zero) }
    val gestures = if (enabled) {
        Modifier.pointerInput(d) {
            detectDragGestures(
                onDragStart = { local ->
                    drag.start(d, origin + local)
                    start()
                },
                onDrag = { change, delta ->
                    change.consume()
                    drag.moveBy(delta)
                },
                onDragEnd = { drag.end()?.let(dropped) },
                onDragCancel = { drag.cancel() },
            )
        }
    } else {
        Modifier
    }
    Box(
        modifier
            .alpha(if (enabled) 1f else EMPTY_STACK_ALPHA)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .then(gestures),
    ) {
        content(if (enabled) ({ onTapAdd(d) }) else null)
    }
}

/**
 * The unit under the finger. [DragDropState.position] is read inside `graphicsLayer {}` (deferred read), so pointer
 * moves re-run only this layer's draw phase; the rest of the screen is not recomposed.
 */
@Composable
private fun DragGhost(denomination: Denomination, drag: DragDropState) {
    val art = LocalMoneyArt.current
    val density = LocalDensity.current
    val isBill = denomination.kind == Denomination.Kind.BILL
    val width = if (isBill) GhostBillWidth * denomination.relativeSize else GhostCoinSize * denomination.relativeSize
    val height = if (isBill) width / denomination.billAspectRatio else width
    val halfW = with(density) { width.toPx() / 2f }
    val halfH = with(density) { height.toPx() / 2f }
    MoneyFace(
        denomination,
        Modifier
            .size(width, height)
            .graphicsLayer {
                val p = drag.position
                val o = drag.hostOrigin
                translationX = p.x - o.x - halfW
                translationY = p.y - o.y - halfH
                rotationZ = if (isBill) -3f else 0f
                scaleX = if (isBill) 1.08f else 1f
                scaleY = if (isBill) 1.08f else 1f
            }
            .shadow(6.dp, if (isBill) RoundedCornerShape(2.dp) else CircleShape),
        art.painter(denomination),
    )
}

