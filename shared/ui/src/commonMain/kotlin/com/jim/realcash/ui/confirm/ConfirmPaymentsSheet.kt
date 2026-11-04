package com.jim.realcash.ui.confirm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Won
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.FlagBadges
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.theme.LocalRealCashColors
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.UnitChip

/**
 * Payment confirmation sheet (screen 3) as a Material3 modal bottom sheet.
 *
 * @param onDismiss closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmPaymentsSheet(
    model: ConfirmPaymentsModel,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
) {
    val ui by model.ui.collectAsState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ConfirmPaymentsContent(ui, onConfirm = model::confirm, onReject = model::reject)
    }
}

/** Stateless body of [ConfirmPaymentsSheet]. */
@Composable
fun ConfirmPaymentsContent(
    ui: ConfirmPaymentsUi,
    onConfirm: (PaymentId) -> Unit,
    onReject: (PaymentId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(Strings.CONFIRM_TITLE, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        if (ui.pending.isEmpty() && ui.confirmed.isEmpty()) {
            Text(
                Strings.CONFIRM_EMPTY,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp),
            )
        }
        ui.pending.forEach { p -> PendingCard(p, busy = p.id in ui.busy, onConfirm, onReject) }
        ui.confirmed.forEach { ConfirmedCard(it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PendingCard(p: PendingPaymentUi, busy: Boolean, onConfirm: (PaymentId) -> Unit, onReject: (PaymentId) -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (p.isRefund) "환불 " else "") + p.amount.formatted(),
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f),
                )
                Text(p.confidenceLabel.let(Strings::confidence), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(p.merchant, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${p.sourceLabel} · ${p.timeLabel}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                OutlinedButton(onClick = { onReject(p.id) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(Strings.NO) }
                Button(onClick = { onConfirm(p.id) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text(Strings.YES) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConfirmedCard(r: ConfirmedResultUi) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                Strings.settledCard(r.merchant, r.amount.formatted(), null),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                if (r.isRefund) Strings.allowanceReceived(r.change.formatted()) else Strings.changeLine(r.change.formatted()),
                style = MaterialTheme.typography.bodyMedium,
                color = LocalRealCashColors.current.positive,
            )
            if (r.changeCounts.isNotEmpty()) {
                val art = LocalMoneyArt.current
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    r.changeCounts.entries.sortedByDescending { it.key.won }.forEach { (denomination, count) ->
                        UnitChip(denomination, count, painter = art.painter(denomination))
                    }
                }
            }
            if (r.shortfall > Won.ZERO) {
                Text(Strings.shortfallLine(r.shortfall.formatted()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
            FlagBadges(r.flags)
        }
    }
}
