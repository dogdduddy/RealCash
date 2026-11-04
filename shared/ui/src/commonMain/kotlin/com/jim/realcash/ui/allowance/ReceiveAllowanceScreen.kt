package com.jim.realcash.ui.allowance

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.calc.SplitStyle
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Won
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.DenominationStepperRow
import com.jim.realcash.ui.components.ScreenHeader
import com.jim.realcash.ui.components.SectionLabel
import com.jim.realcash.ui.format.ThousandsSeparatorTransformation
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.format.shortLabel
import kotlin.math.abs
import kotlinx.datetime.LocalDate

/**
 * Receive allowance (screen 2): amount field, two suggestion chips, a per-denomination stepper list, a live sum
 * line (red while invalid) and "지갑에 넣기".
 */
@Composable
fun ReceiveAllowanceScreen(model: ReceiveAllowanceModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val draft by model.draft.collectAsState()
    val submitting by model.submitting.collectAsState()
    val periodStart by model.periodStart.collectAsState()
    ReceiveAllowanceContent(
        draft = draft,
        periodStart = periodStart,
        submitting = submitting,
        onBack = onBack,
        onAmountChange = model::onAmountChange,
        onStyle = model::onStyle,
        onIncrement = model::increment,
        onDecrement = model::decrement,
        onSubmit = model::submit,
        modifier = modifier,
    )
}

/** Stateless body of [ReceiveAllowanceScreen]. */
@Composable
fun ReceiveAllowanceContent(
    draft: AllowanceDraft,
    periodStart: LocalDate,
    submitting: Boolean,
    onBack: () -> Unit,
    onAmountChange: (String) -> Unit,
    onStyle: (SplitStyle) -> Unit,
    onIncrement: (Denomination) -> Unit,
    onDecrement: (Denomination) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(Strings.ALLOWANCE_TITLE, onBack = onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.digits,
                onValueChange = onAmountChange,
                label = { Text(Strings.ALLOWANCE_AMOUNT) },
                placeholder = { Text(Strings.ALLOWANCE_AMOUNT_HINT) },
                prefix = { Text("₩") },
                singleLine = true,
                isError = draft.amountNotCash,
                supportingText = if (draft.amountNotCash) ({ Text(Strings.AMOUNT_NOT_CASH) }) else null,
                visualTransformation = ThousandsSeparatorTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleChip(Strings.STYLE_ATM, SplitStyle.LARGEST_FIRST, draft, onStyle)
                StyleChip(Strings.STYLE_BALANCED, SplitStyle.BALANCED, draft, onStyle)
            }
            SectionLabel(Strings.SPLIT_TITLE)
            Column {
                Denomination.descending.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    DenominationStepperRow(d, draft.countOf(d), onIncrement, onDecrement)
                }
            }
            SumLine(draft)
            Text(
                Strings.periodStartNote(periodStart.shortLabel()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
        }
        Button(
            onClick = onSubmit,
            enabled = draft.isValid && !submitting,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
        ) { Text(Strings.PUT_IN_WALLET, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun StyleChip(label: String, style: SplitStyle, draft: AllowanceDraft, onStyle: (SplitStyle) -> Unit) {
    FilterChip(
        selected = draft.style == style && !draft.edited && draft.counts.isNotEmpty(),
        onClick = { onStyle(style) },
        label = { Text(label) },
    )
}

@Composable
private fun SumLine(draft: AllowanceDraft) {
    val target = draft.amount ?: Won.ZERO
    val color = if (draft.showSumError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Column {
        Text(
            Strings.splitSum(draft.sum.formatted(), target.formatted()),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = color,
        )
        if (draft.showSumError && !draft.amountNotCash && draft.sum != target) {
            val diff = draft.sum - target
            Text(
                Strings.splitDiff(Won(abs(diff.value)).formatted(), over = diff.value > 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
