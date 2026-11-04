package com.jim.realcash.ui.exchange

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.DenominationStepperRow
import com.jim.realcash.ui.components.ScreenHeader
import com.jim.realcash.ui.components.SectionLabel
import com.jim.realcash.ui.format.faceLabel
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.money.BillStack
import com.jim.realcash.ui.money.CoinStack
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.UnitChip
import kotlin.math.abs

/**
 * 환전 (screen 5), deliberately a little ceremonious, like a bank counter (design doc §3.3 F): first put what to
 * give on the counter, then choose how to receive it, and only a balanced, non-trivial split can be exchanged.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ExchangeScreen(model: ExchangeModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val draft by model.draft.collectAsState()
    val submitting by model.submitting.collectAsState()
    LaunchedEffect(model) { model.syncWithWallet() }
    val receiving = draft.step == ExchangeDraft.Step.RECEIVE
    // In step 2 the platform back goes to step 1 first (composed after the root handler, so it takes priority).
    BackHandler(enabled = receiving) { model.backToGive() }
    ExchangeContent(
        draft = draft,
        submitting = submitting,
        onBack = if (receiving) model::backToGive else onBack,
        onAddToTray = model::addToTray,
        onRemoveFromTray = model::removeFromTray,
        onToReceive = model::toReceive,
        onBackToGive = model::backToGive,
        onSuggestion = model::onSuggestion,
        onIncrement = model::increment,
        onDecrement = model::decrement,
        onSubmit = model::submit,
        modifier = modifier,
    )
}

/** Stateless body of [ExchangeScreen]. */
@Composable
fun ExchangeContent(
    draft: ExchangeDraft,
    submitting: Boolean,
    onBack: () -> Unit,
    onAddToTray: (Denomination) -> Unit,
    onRemoveFromTray: (Denomination) -> Unit,
    onToReceive: () -> Unit,
    onBackToGive: () -> Unit,
    onSuggestion: (ExchangeSuggestion) -> Unit,
    onIncrement: (Denomination) -> Unit,
    onDecrement: (Denomination) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val receiving = draft.step == ExchangeDraft.Step.RECEIVE
    Column(modifier.fillMaxSize()) {
        ScreenHeader(Strings.EXCHANGE_TITLE, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepIndicator(draft.step)
            Text(
                if (receiving) Strings.EXCHANGE_RECEIVE_NOTE else Strings.EXCHANGE_GIVE_NOTE,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            CounterTray(draft, editable = !receiving, onRemoveFromTray)
            if (receiving) {
                ReceiveStep(draft, onBackToGive, onSuggestion, onIncrement, onDecrement)
            } else {
                GiveStep(draft, onAddToTray)
            }
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = if (receiving) onSubmit else onToReceive,
            enabled = !submitting && if (receiving) draft.isValid else draft.trayUnits.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
        ) {
            Text(if (receiving) Strings.EXCHANGE_DO else Strings.EXCHANGE_TO_COUNTER, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun StepIndicator(step: ExchangeDraft.Step) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        StepDot(1, Strings.EXCHANGE_STEP_GIVE, active = step == ExchangeDraft.Step.GIVE, done = step == ExchangeDraft.Step.RECEIVE)
        HorizontalDivider(Modifier.weight(1f).padding(horizontal = 10.dp), color = MaterialTheme.colorScheme.outlineVariant)
        StepDot(2, Strings.EXCHANGE_STEP_RECEIVE, active = step == ExchangeDraft.Step.RECEIVE, done = false)
    }
}

@Composable
private fun StepDot(number: Int, label: String, active: Boolean, done: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val on = active || done
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(26.dp).background(if (on) scheme.primary else scheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (done) "✓" else number.toString(),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
            color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
        )
    }
}

/** The counter: a framed tray showing what is being handed over. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CounterTray(draft: ExchangeDraft, editable: Boolean, onRemove: (Denomination) -> Unit) {
    val art = LocalMoneyArt.current
    val scheme = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.large
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .background(scheme.secondaryContainer.copy(alpha = 0.55f), shape)
            .border(1.5.dp, scheme.secondary.copy(alpha = 0.35f), shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Strings.EXCHANGE_TRAY,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = scheme.onSecondaryContainer,
                modifier = Modifier.weight(1f),
            )
            Text(
                draft.trayTotal.formatted(),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = scheme.onSecondaryContainer,
            )
        }
        if (draft.trayCounts.isEmpty()) {
            Text(Strings.EXCHANGE_TRAY_EMPTY, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant)
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.trayCounts.forEach { (d, n) ->
                    val chipModifier = if (editable) {
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onRemove(d) }
                            .semantics { contentDescription = "${d.faceLabel}원 ${n}장, 눌러서 지갑에 돌려놓기" }
                    } else {
                        Modifier
                    }
                    UnitChip(d, n, chipModifier, painter = art.painter(d))
                }
            }
        }
    }
}

@Composable
private fun GiveStep(draft: ExchangeDraft, onAdd: (Denomination) -> Unit) {
    val art = LocalMoneyArt.current
    SectionLabel(Strings.WALLET, trailing = (draft.available.total() - draft.trayTotal).formatted())
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        draft.remainingOf(Denomination.Kind.BILL).chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { c ->
                    val enabled = c.count > 0
                    BillStack(
                        c.denomination,
                        c.count,
                        Modifier.weight(1f).alpha(if (enabled) 1f else 0.32f),
                        painter = art.painter(c.denomination),
                        onClick = if (enabled) ({ onAdd(c.denomination) }) else null,
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        val coins = draft.remainingOf(Denomination.Kind.COIN)
        if (coins.isNotEmpty()) {
            SectionLabel(Strings.COINS)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                coins.forEach { c ->
                    val enabled = c.count > 0
                    CoinStack(
                        c.denomination,
                        c.count,
                        Modifier.alpha(if (enabled) 1f else 0.32f),
                        painter = art.painter(c.denomination),
                        onClick = if (enabled) ({ onAdd(c.denomination) }) else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceiveStep(
    draft: ExchangeDraft,
    onBackToGive: () -> Unit,
    onSuggestion: (ExchangeSuggestion) -> Unit,
    onIncrement: (Denomination) -> Unit,
    onDecrement: (Denomination) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SectionLabel(Strings.EXCHANGE_STEP_RECEIVE, Modifier.weight(1f))
        TextButton(onClick = onBackToGive) { Text(Strings.EXCHANGE_BACK_TO_GIVE) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = draft.suggestion == ExchangeSuggestion.MINIMAL,
            onClick = { onSuggestion(ExchangeSuggestion.MINIMAL) },
            label = { Text(Strings.EXCHANGE_SUGGEST_MINIMAL) },
        )
        FilterChip(
            selected = draft.suggestion == ExchangeSuggestion.SMALLER,
            onClick = { onSuggestion(ExchangeSuggestion.SMALLER) },
            enabled = draft.smallerAvailable,
            label = { Text(Strings.EXCHANGE_SUGGEST_SMALLER) },
        )
    }
    Column {
        Denomination.descending.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            DenominationStepperRow(d, draft.toCount(d), onIncrement, onDecrement)
        }
    }
    SumLine(draft)
}

@Composable
private fun SumLine(draft: ExchangeDraft) {
    val error = !draft.sumMatches || draft.isSameAsGiven
    val color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column {
        Text(
            Strings.splitSum(draft.toTotal.formatted(), draft.trayTotal.formatted()),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        val detail = when {
            !draft.sumMatches -> {
                val diff = draft.toTotal - draft.trayTotal
                Strings.splitDiff(Won(abs(diff.value)).formatted(), over = diff.value > 0)
            }
            draft.isSameAsGiven -> Strings.EXCHANGE_SAME
            else -> null
        }
        if (detail != null) {
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Start)
        }
    }
}
