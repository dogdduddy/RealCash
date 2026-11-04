package com.jim.realcash.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.jim.realcash.data.RealCashData
import com.jim.realcash.ui.allowance.ReceiveAllowanceScreen
import com.jim.realcash.ui.components.RcIcon
import com.jim.realcash.ui.components.RcIconGlyph
import com.jim.realcash.ui.confirm.ConfirmPaymentsSheet
import com.jim.realcash.ui.settings.LedgerPlaceholderScreen
import com.jim.realcash.ui.settings.SettingsPlaceholderScreen
import com.jim.realcash.ui.simulator.NotificationSimulatorScreen
import com.jim.realcash.ui.theme.RealCashTheme
import com.jim.realcash.ui.wallet.WalletHomeScreen
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

/**
 * The RealCash app: theme, bottom bar (지갑 / 가계부 / 설정), hand-rolled navigation and the payment confirmation sheet.
 *
 * Creates the [AppState] (and with it the screen models) in a composition-scoped coroutine scope and loads the
 * settings and the wallet ledger once.
 *
 * @param data the process-wide data graph.
 * @param haptics platform haptics.
 * @param showDebugShortcut shows a notification-simulator shortcut on the wallet home (desktop builds).
 * @param clock time source; override for previews and screenshots.
 * @param timeZone zone for dates and times; override for previews and screenshots.
 * @param darkTheme dark palette; follows the system by default.
 */
@Composable
fun RealCashApp(
    data: RealCashData,
    haptics: Haptics,
    showDebugShortcut: Boolean = false,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    darkTheme: Boolean = isSystemInDarkTheme(),
) {
    val scope = rememberCoroutineScope()
    val appState = remember(data) { AppState(AppEnvironment(data, haptics, UiMessages(), clock, timeZone), scope) }
    LaunchedEffect(appState) { appState.start() }
    RealCashTheme(darkTheme) {
        RealCashRoot(appState, showDebugShortcut)
    }
}

/**
 * Everything below the theme, driven by an existing [appState]. Used by [RealCashApp] and by the screenshot tests,
 * which prepare the back stack and the sheet before rendering.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealCashRoot(appState: AppState, showDebugShortcut: Boolean = false, modifier: Modifier = Modifier) {
    LaunchedEffect(appState) {
        appState.env.messages.messages.collect { appState.snackbarHostState.showSnackbar(it) }
    }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(appState.snackbarHostState) },
        bottomBar = { if (!appState.canGoBack) BottomBar(appState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (appState.current) {
                Screen.WalletHome -> WalletHomeScreen(
                    model = appState.walletHome,
                    onReceiveAllowance = { appState.navigate(Screen.ReceiveAllowance) },
                    onOpenPending = appState::openConfirmSheet,
                    onOpenSimulator = if (showDebugShortcut) ({ appState.navigate(Screen.NotificationSimulator) }) else null,
                )
                Screen.Ledger -> LedgerPlaceholderScreen()
                Screen.Settings -> SettingsPlaceholderScreen(onOpenSimulator = { appState.navigate(Screen.NotificationSimulator) })
                Screen.ReceiveAllowance -> {
                    val model = remember { appState.receiveAllowance }
                    ReceiveAllowanceScreen(model, onBack = { appState.back() })
                }
                Screen.NotificationSimulator -> NotificationSimulatorScreen(appState.simulator, onBack = { appState.back() })
            }
        }
    }
    if (appState.confirmSheetOpen) {
        ConfirmPaymentsSheet(appState.confirmPayments, onDismiss = appState::closeConfirmSheet)
    }
}

@Composable
private fun BottomBar(appState: AppState) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Tab.entries.forEach { tab ->
            NavigationBarItem(
                selected = appState.tab == tab,
                onClick = { appState.selectTab(tab) },
                icon = {
                    RcIconGlyph(
                        when (tab) {
                            Tab.WALLET -> RcIcon.WALLET
                            Tab.LEDGER -> RcIcon.LEDGER
                            Tab.SETTINGS -> RcIcon.SETTINGS
                        },
                    )
                },
                label = { Text(tab.label) },
            )
        }
    }
}
