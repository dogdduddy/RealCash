package com.jim.realcash.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.FlagBadges
import com.jim.realcash.ui.components.RcIcon
import com.jim.realcash.ui.components.RcIconGlyph
import com.jim.realcash.ui.components.SectionLabel
import com.jim.realcash.ui.format.formatRemaining
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.money.BillStack
import com.jim.realcash.ui.money.CoinStack
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.UnitChip
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

/** How long the "정산 완료" card stays before it hides itself. */
private val SettlementCardDuration = 6.seconds

/**
 * Wallet home (screen 1): period and balance, the settlement card, the pending-payments banner, the hand card and the
 * wallet's bill and coin stacks.
 *
 * @param onReceiveAllowance opens the allowance screen.
 * @param onOpenPending opens the payment confirmation sheet.
 * @param onOpenSimulator opens the notification simulator; the shortcut is shown only when non-null (desktop).
 */
@Composable
fun WalletHomeScreen(
    model: WalletHomeModel,
    onReceiveAllowance: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenSimulator: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ui by model.ui.collectAsState()
    WalletHomeContent(
        ui = ui,
        onReceiveAllowance = onReceiveAllowance,
        onOpenPending = onOpenPending,
        onReturnHand = model::returnHand,
        onDismissSettlement = { model.dismissSettlement(it.paymentId) },
        onOpenSimulator = onOpenSimulator,
        modifier = modifier,
    )
}

/** Stateless body of [WalletHomeScreen]. */
@Composable
fun WalletHomeContent(
    ui: WalletHomeUi,
    onReceiveAllowance: () -> Unit,
    onOpenPending: () -> Unit,
    onReturnHand: () -> Unit,
    onDismissSettlement: (SettlementUi) -> Unit,
    onOpenSimulator: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Spacer(Modifier.height(4.dp))
            BalanceHeader(ui, onOpenSimulator)
            ui.lastSettlement?.let { s ->
                LaunchedEffect(s.paymentId) {
                    delay(SettlementCardDuration)
                    onDismissSettlement(s)
                }
                SettlementCard(s, onDismiss = { onDismissSettlement(s) })
            }
            if (ui.pendingCount > 0) PendingBanner(ui.pendingCount, onOpenPending)
            if (ui.hand != null) HandCard(ui.hand, onReturnHand) else NoHandPlaceholder()
            SectionLabel(Strings.WALLET, trailing = ui.walletTotal.formatted())
            when {
                !ui.loaded -> Spacer(Modifier.height(120.dp))
                ui.walletEmpty -> EmptyWallet(onReceiveAllowance)
                else -> WalletStacks(ui)
            }
            Spacer(Modifier.height(88.dp))
        }
        if (ui.loaded && !ui.walletEmpty) {
            ExtendedFloatingActionButton(
                onClick = onReceiveAllowance,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            ) { Text(Strings.RECEIVE_ALLOWANCE, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun BalanceHeader(ui: WalletHomeUi, onOpenSimulator: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "${ui.periodLabel} · ${Strings.daysLeft(ui.daysLeft)}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                Strings.BALANCE,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (onOpenSimulator != null) {
                TextButton(onClick = onOpenSimulator, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                    Text(Strings.SIMULATOR_SHORTCUT, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Text(
            ui.balance.formatted(),
            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            "${Strings.WALLET} ${ui.walletTotal.formatted()} · ${Strings.HAND} ${ui.handTotal.formatted()}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettlementCard(s: SettlementUi, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.medium,
        shadowElevation = 3.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    Strings.settledCard(s.merchant, s.amount?.formatted(), s.change.formatted()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                )
                FlagBadges(s.flags)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.semantics { contentDescription = Strings.DISMISS }) {
                RcIconGlyph(RcIcon.CLOSE, Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun PendingBanner(count: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            Strings.pendingBanner(count),
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HandCard(hand: HandUi, onReturn: () -> Unit) {
    val art = LocalMoneyArt.current
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    Strings.IN_HAND_TITLE,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    formatRemaining(hand.remaining),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    hand.total.formatted(),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                hand.expectedAmount?.let {
                    Text(
                        "  ${Strings.EXPECTED} ${it.formatted()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hand.units.forEach { UnitChip(it.denomination, it.count, painter = art.painter(it.denomination)) }
            }
            OutlinedButton(onClick = onReturn, modifier = Modifier.fillMaxWidth()) { Text(Strings.RETURN_HAND) }
        }
    }
}

@Composable
private fun NoHandPlaceholder() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            Strings.NO_HAND,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        )
    }
}

@Composable
private fun EmptyWallet(onReceiveAllowance: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(Strings.WALLET_EMPTY_TITLE, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            Text(Strings.WALLET_EMPTY_BODY, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Button(onClick = onReceiveAllowance) { Text(Strings.RECEIVE_ALLOWANCE) }
        }
    }
}

@Composable
private fun WalletStacks(ui: WalletHomeUi) {
    val art = LocalMoneyArt.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ui.bills.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { BillStack(it.denomination, it.count, Modifier.weight(1f), painter = art.painter(it.denomination)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (ui.coins.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            SectionLabel(Strings.COINS)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                ui.coins.forEach { CoinStack(it.denomination, it.count, painter = art.painter(it.denomination)) }
            }
        }
    }
}
