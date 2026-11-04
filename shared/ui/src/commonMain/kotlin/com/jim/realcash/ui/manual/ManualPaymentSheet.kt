package com.jim.realcash.ui.manual

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.FlagBadges
import com.jim.realcash.ui.format.ThousandsSeparatorTransformation
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.UnitChip
import com.jim.realcash.ui.theme.LocalRealCashColors

/** Manual fallback available independently of notification access or parser support. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualPaymentSheet(model: ManualPaymentModel, onDismiss: () -> Unit, snackbarHostState: SnackbarHostState) {
    val draft by model.draft.collectAsState()
    val submitting by model.submitting.collectAsState()
    val recorded by model.recorded.collectAsState()
    val wallet by model.wallet.collectAsState()
    val focus = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !model.submitting.value },
    )
    ModalBottomSheet(onDismissRequest = { if (!model.submitting.value) onDismiss() }, sheetState = sheetState) {
        // The root snackbar is behind the modal scrim; mirror its host inside the sheet for visible failures.
        SnackbarHost(snackbarHostState, Modifier.fillMaxWidth().padding(horizontal = 20.dp))
        ManualPaymentContent(
            draft = draft,
            handTotal = wallet.inHand.total(),
            submitting = submitting,
            recorded = recorded,
            onAmountChange = model::onAmountChange,
            onMerchantChange = model::onMerchantChange,
            onSubmit = { focus.clearFocus(); model.submit() },
            onDismiss = onDismiss,
        )
    }
}

/** Pure rendering of the draft or the actual settlement result. */
@Composable
fun ManualPaymentContent(
    draft: ManualPaymentDraft,
    handTotal: Won,
    submitting: Boolean,
    recorded: Boolean,
    onAmountChange: (String) -> Unit,
    onMerchantChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(Strings.MANUAL_TITLE, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        val result = draft.result
        if (result == null) {
            Text(
                if (handTotal > Won.ZERO) Strings.manualHandHint(handTotal.formatted()) else Strings.MANUAL_WALLET_HINT,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = draft.digits,
                onValueChange = onAmountChange,
                enabled = !submitting && !recorded,
                label = { Text(Strings.MANUAL_AMOUNT) },
                prefix = { Text("₩") },
                singleLine = true,
                visualTransformation = ThousandsSeparatorTransformation,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = draft.amountNotCash,
                supportingText = if (draft.amountNotCash) ({ Text(Strings.AMOUNT_NOT_CASH) }) else null,
                textStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.merchant,
                onValueChange = onMerchantChange,
                enabled = !submitting && !recorded,
                label = { Text(Strings.MANUAL_MERCHANT) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (recorded && !submitting) Text(Strings.MANUAL_RETRY_HINT, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onSubmit, enabled = draft.isValid && !submitting, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (submitting) Strings.MANUAL_SUBMITTING else Strings.MANUAL_SETTLE)
            }
        } else {
            ManualResultCard(draft, result)
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(Strings.DISMISS) }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManualResultCard(draft: ManualPaymentDraft, result: ManualPaymentResult) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                Strings.settledCard(draft.merchantOrNull, draft.amount?.formatted(), null),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(Strings.ledgerPaid(result.paid.formatted()), style = MaterialTheme.typography.bodyMedium)
            Text(Strings.changeLine(result.change.formatted()), color = LocalRealCashColors.current.positive)
            val art = LocalMoneyArt.current
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                result.changeCounts.entries.sortedByDescending { it.key.won }.forEach { (denomination, count) ->
                    UnitChip(denomination, count, painter = art.painter(denomination))
                }
            }
            if (result.settlement.shortfall > Won.ZERO) {
                Text(Strings.shortfallLine(result.settlement.shortfall.formatted()), color = MaterialTheme.colorScheme.error)
            }
            FlagBadges(result.settlement.flags)
        }
    }
}
