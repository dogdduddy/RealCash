package com.jim.realcash.ui.ledger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.FlagBadges
import com.jim.realcash.ui.components.ScreenHeader
import com.jim.realcash.ui.format.exchangeSummary
import com.jim.realcash.ui.format.formatTime
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.format.shortLabel
import com.jim.realcash.ui.format.sourceAppLabel
import com.jim.realcash.ui.format.splitSummary
import com.jim.realcash.ui.money.colors
import com.jim.realcash.ui.theme.LocalRealCashColors
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** 가계부 tab (screen 6): the period review header and the ledger grouped by day, newest first. */
@Composable
fun LedgerScreen(model: LedgerModel, timeZone: TimeZone, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    val ui by model.ui.collectAsState()
    LedgerContent(ui, timeZone, modifier, onBack)
}

/** Stateless body of [LedgerScreen]. */
@Composable
fun LedgerContent(ui: LedgerUi, timeZone: TimeZone, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(Strings.LEDGER_TITLE, onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") { SummaryCard(ui) }
            if (ui.days.isEmpty()) {
                item(key = "empty") {
                    Text(
                        Strings.LEDGER_EMPTY,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
            ui.days.forEach { day ->
                item(key = "day-${day.date}") {
                    val label = day.date.shortLabel()
                    Text(
                        if (day.date == ui.today) Strings.ledgerToday(label) else label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
                    )
                }
                items(day.rows, key = { it.eventId }) { row -> LedgerRowItem(row, timeZone, ui.today) }
            }
        }
    }
}

@Composable
private fun SummaryCard(ui: LedgerUi) {
    val s = ui.summary
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(ui.periodLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                Figure(Strings.LEDGER_SPENT, s.spent.formatted(), Modifier.weight(1f))
                Figure(Strings.LEDGER_RECEIVED, s.received.formatted(), Modifier.weight(1f))
                Figure(Strings.LEDGER_REMAINING, s.remaining.formatted(), Modifier.weight(1f), emphasize = true)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row {
                Figure(Strings.LEDGER_DIRECT, Strings.ledgerDirect(s.directCount, s.paymentCount), Modifier.weight(1f), small = true)
                Figure(Strings.LEDGER_BROKEN, Strings.ledgerBroken(s.brokenLargeBills), Modifier.weight(1f), small = true)
            }
            val plan = if (s.planned != null && s.actual != null) {
                Strings.ledgerPlanVsActual(s.planned.formatted(), s.actual.formatted())
            } else {
                Strings.LEDGER_NO_DATA
            }
            Figure(Strings.LEDGER_PLAN_VS_ACTUAL, plan, small = true)
        }
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier, emphasize: Boolean = false, small: Boolean = false) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = (if (small) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium)
                .copy(fontWeight = FontWeight.Bold),
            color = if (emphasize) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LedgerRowItem(row: LedgerRow, timeZone: TimeZone, today: LocalDate) {
    val extra = LocalRealCashColors.current
    val scheme = MaterialTheme.colorScheme
    val time = formatTime(row.at, timeZone, today)
    when (row) {
        is LedgerRow.Payment -> RowShell(
            dot = row.paidDotColor(),
            title = row.merchant ?: row.sourceApp?.let(::sourceAppLabel) ?: Strings.LEDGER_PAYMENT_UNKNOWN,
            detail = buildString {
                append(Strings.ledgerPaid(row.paid.formatted()))
                if (row.change.value > 0) append(" · ").append(Strings.ledgerChange(row.change.formatted()))
                if (row.shortfall.value > 0) append(" · ").append(Strings.shortfallLine(row.shortfall.formatted()))
            },
            amount = "-${(row.amount ?: row.cashOut).formatted()}",
            amountColor = scheme.onSurface,
            time = time,
        ) { FlagBadges(row.flags, Modifier.padding(top = 4.dp)) }
        is LedgerRow.Refund -> RowShell(
            dot = extra.positive,
            title = listOfNotNull(Strings.LEDGER_REFUND, row.merchant).joinToString(" · "),
            detail = null,
            amount = "+${row.amount.formatted()}",
            amountColor = extra.positive,
            time = time,
        )
        is LedgerRow.Allowance -> RowShell(
            dot = scheme.primary,
            title = Strings.ledgerAllowance(row.total.formatted()),
            detail = splitSummary(row.split),
            amount = "+${row.total.formatted()}",
            amountColor = extra.positive,
            time = time,
        )
        is LedgerRow.Exchange -> RowShell(
            dot = scheme.secondary,
            title = Strings.LEDGER_EXCHANGE,
            detail = exchangeSummary(row.from, row.to),
            amount = null,
            amountColor = scheme.onSurface,
            time = time,
        )
        is LedgerRow.HandExpired -> RowShell(
            dot = scheme.outline,
            title = Strings.ledgerHandExpired(row.amount.formatted()),
            detail = null,
            amount = null,
            amountColor = scheme.onSurface,
            time = time,
        )
    }
}

/** Dot color of a payment: warning for "직접 꺼내지 않은 결제", the accent green otherwise. */
@Composable
private fun LedgerRow.Payment.paidDotColor(): Color =
    if (SettlementFlag.NOT_HAND_PICKED in flags) LocalRealCashColors.current.warning else Denomination.W10000.colors().dark

@Composable
private fun RowShell(
    dot: Color,
    title: String,
    detail: String?,
    amount: String?,
    amountColor: Color,
    time: String,
    extra: @Composable () -> Unit = {},
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 6.dp).size(10.dp).background(dot, CircleShape))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!detail.isNullOrEmpty()) {
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                extra()
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (amount != null) {
                    Text(amount, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold), color = amountColor)
                }
                Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
