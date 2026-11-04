package com.jim.realcash.ui.wallet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.components.ClosedWallet
import com.jim.realcash.ui.format.formatted

/** Home v2: a period, the remaining money, and a closed wallet. */
@Composable
fun WalletHomeScreen(
    model: WalletHomeModel,
    onReceiveAllowance: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenWallet: () -> Unit,
    modifier: Modifier = Modifier,
    opening: Float = 0f,
    enabled: Boolean = true,
    menu: @Composable () -> Unit = {},
) {
    val ui by model.ui.collectAsState()
    WalletHomeContent(ui, onReceiveAllowance, onOpenPending, onOpenWallet, modifier, opening, enabled, menu)
}

/** Stateless home body, with the overflow menu supplied by the app shell. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WalletHomeContent(
    ui: WalletHomeUi,
    onReceiveAllowance: () -> Unit,
    onOpenPending: () -> Unit,
    onOpenWallet: () -> Unit,
    modifier: Modifier = Modifier,
    opening: Float = 0f,
    enabled: Boolean = true,
    menu: @Composable () -> Unit = {},
) {
    Box(modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 80.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(ui.periodLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            // Flow to a second line at large accessibility font sizes instead of truncating the amount.
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    ui.balance.formatted(),
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                )
                Text(
                    Strings.REMAINING,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically).padding(start = 8.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "${ui.periodRange} · ${Strings.daysLeft(ui.daysLeft)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(88.dp))
            FlowRow(
                Modifier.fillMaxWidth(0.86f).heightIn(min = 48.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
            ) {
                if (ui.pendingCount > 0) {
                    Surface(
                        onClick = onOpenPending, enabled = enabled,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) { Text(Strings.pendingBadge(ui.pendingCount), Modifier.padding(horizontal = 14.dp, vertical = 12.dp), style = MaterialTheme.typography.labelLarge) }
                }
                if (ui.handTotal.value > 0) {
                    Text(
                        Strings.handBadge(ui.handTotal.formatted()),
                        Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            ClosedWallet(ui.sideLayers, opening, ui.loaded && enabled, onOpenWallet, Modifier.fillMaxWidth(0.86f))
            if (ui.loaded && ui.walletEmpty) {
                Spacer(Modifier.height(30.dp))
                Button(onClick = onReceiveAllowance, enabled = enabled, modifier = Modifier.fillMaxWidth(0.72f).height(56.dp)) {
                    Text(Strings.ADD_ALLOWANCE, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp)) { if (enabled) menu() }
    }
}
