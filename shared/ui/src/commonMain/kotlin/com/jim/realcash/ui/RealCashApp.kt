package com.jim.realcash.ui

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.jim.realcash.data.RealCashData
import com.jim.realcash.ui.allowance.ReceiveAllowanceScreen
import com.jim.realcash.ui.components.RcIcon
import com.jim.realcash.ui.components.RcIconGlyph
import com.jim.realcash.ui.confirm.ConfirmPaymentsSheet
import com.jim.realcash.ui.exchange.ExchangeScreen
import com.jim.realcash.ui.ledger.LedgerScreen
import com.jim.realcash.ui.manual.ManualPaymentSheet
import com.jim.realcash.ui.money.LocalMoneyArt
import com.jim.realcash.ui.money.ResourceMoneyArt
import com.jim.realcash.ui.settings.SettingsScreen
import com.jim.realcash.ui.simulator.NotificationSimulatorScreen
import com.jim.realcash.ui.takeout.TakeOutScreen
import com.jim.realcash.ui.theme.RealCashTheme
import com.jim.realcash.ui.wallet.WalletHomeScreen
import com.jim.realcash.ui.walletopen.WalletOpenScreen
import kotlin.time.Clock
import kotlinx.datetime.TimeZone

/**
 * The RealCash app: theme, wallet menu, hand-rolled navigation and the payment confirmation sheet.
 *
 * Creates the [AppState] (and with it the screen models) in a composition-scoped coroutine scope and loads the
 * settings and the wallet ledger once.
 *
 * @param data the process-wide data graph.
 * @param haptics platform haptics.
 * @param showDebugShortcut adds a notification-simulator item to the wallet menu (desktop builds).
 * @param clock time source; override for previews and screenshots.
 * @param timeZone zone for dates and times; override for previews and screenshots.
 * @param darkTheme dark palette; follows the system by default.
 * @param platformDebugEntries platform-only screens listed in 설정 → 디버그 (Android: notification access and the
 *   notification sample collector). Desktop and iOS pass nothing.
 */
@Composable
fun RealCashApp(
    data: RealCashData,
    haptics: Haptics,
    showDebugShortcut: Boolean = false,
    clock: Clock = Clock.System,
    timeZone: TimeZone = TimeZone.currentSystemDefault(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    platformDebugEntries: List<DebugEntry> = emptyList(),
) {
    val scope = rememberCoroutineScope()
    val appState = remember(data) { AppState(AppEnvironment(data, haptics, UiMessages(), clock, timeZone), scope) }
    LaunchedEffect(appState) { appState.start() }
    RealCashTheme(darkTheme) {
        RealCashRoot(appState, showDebugShortcut, platformDebugEntries = platformDebugEntries)
    }
}

// NOTE(T3): the one extension point for platform code. A platform host (the Android app) contributes debug screens
// that only exist there; 설정 → 디버그 lists them under the simulator and opens them as a pushed [PlatformDebugScreen],
// so they share the back stack and back handling of every other pushed screen.
/**
 * A platform-only debug screen listed in 설정 → 디버그.
 *
 * @property title row title, also used by the screen's own header.
 * @property description one line under the title.
 * @property content the screen; call `onBack` from its back button.
 */
class DebugEntry(
    val title: String,
    val description: String? = null,
    val content: @Composable (onBack: () -> Unit) -> Unit,
)

/** Pushed screen showing `platformDebugEntries[index]` (declared here, next to [DebugEntry]; part of [Screen]). */
data class PlatformDebugScreen(val index: Int) : Screen

/**
 * Everything below the theme, driven by an existing [appState]. Used by [RealCashApp] and by the screenshot tests,
 * which prepare the back stack and the sheet before rendering.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun RealCashRoot(
    appState: AppState,
    showDebugShortcut: Boolean = false,
    modifier: Modifier = Modifier,
    platformDebugEntries: List<DebugEntry> = emptyList(),
) {
    LaunchedEffect(appState) {
        appState.env.messages.messages.collect { appState.snackbarHostState.showSnackbar(it) }
    }
    // The platform back event (Android back button / gesture) pops the back stack; at the home
    // it is not consumed, so the system handles it. No-op where the platform sends no back events (desktop window).
    BackHandler(enabled = appState.canGoBack) { appState.back() }
    CompositionLocalProvider(LocalMoneyArt provides ResourceMoneyArt) {
        Scaffold(
            modifier = modifier,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(appState.snackbarHostState) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (val screen = appState.current) {
                    Screen.WalletHome, Screen.WalletOpen -> WalletScene(appState, showDebugShortcut)
                    Screen.Ledger -> LedgerScreen(appState.ledger, appState.env.timeZone, onBack = { appState.back() })
                    Screen.Settings -> SettingsScreen(
                        model = appState.settings,
                        onBack = { appState.back() },
                        onOpenSimulator = { appState.navigate(Screen.NotificationSimulator) },
                        platformEntries = platformDebugEntries,
                        onOpenPlatformEntry = { index -> appState.navigate(PlatformDebugScreen(index)) },
                    )
                    Screen.ReceiveAllowance -> {
                        val model = remember { appState.receiveAllowance }
                        ReceiveAllowanceScreen(model, onBack = { appState.back() })
                    }
                    Screen.NotificationSimulator -> NotificationSimulatorScreen(appState.simulator, onBack = { appState.back() })
                    is Screen.TakeOut -> {
                        val model = remember(screen) { appState.takeOut }
                        TakeOutScreen(model, onBack = { appState.back() })
                    }
                    Screen.Exchange -> {
                        val model = remember { appState.exchange }
                        ExchangeScreen(model, onBack = { appState.back() })
                    }
                    is PlatformDebugScreen -> platformDebugEntries.getOrNull(screen.index)?.content?.invoke { appState.back() }
                }
            }
        }
        if (appState.confirmSheetOpen) {
            ConfirmPaymentsSheet(appState.confirmPayments, onDismiss = appState::closeConfirmSheet)
        }
        if (appState.manualSheetOpen) {
            ManualPaymentSheet(appState.manualPayment, onDismiss = appState::closeManualSheet, snackbarHostState = appState.snackbarHostState)
        }
    }
}

/** Keeps both wallet faces alive during the open/close transition. */
@Composable
private fun WalletScene(appState: AppState, showDebugShortcut: Boolean) {
    val open = appState.current == Screen.WalletOpen
    val transition = updateTransition(open, label = "wallet")
    val opening by transition.animateFloat(
        transitionSpec = { tween(durationMillis = if (targetState) 360 else 220) },
        label = "wallet-opening",
    ) { if (it) 1f else 0f }
    Box(Modifier.fillMaxSize()) {
        if (opening < 1f) {
            WalletHomeScreen(
                model = appState.walletHome,
                onReceiveAllowance = { appState.navigate(Screen.ReceiveAllowance) },
                onOpenPending = appState::openConfirmSheet,
                onOpenWallet = appState::openWallet,
                opening = opening,
                enabled = !open,
                modifier = Modifier.graphicsLayer { alpha = 1f - opening },
                menu = { WalletMenu(appState, showDebugShortcut) },
            )
        }
        if (open) {
            WalletOpenScreen(
                model = appState.walletOpen,
                onBack = { appState.back() },
                onManualPayment = appState::openManualSheet,
                modifier = Modifier.graphicsLayer { alpha = opening },
            )
        }
    }
}

/** All secondary destinations live in this menu; settings retains the platform debug entries. */
@Composable
private fun WalletMenu(appState: AppState, showDebugShortcut: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    fun choose(action: () -> Unit) {
        expanded = false
        action()
    }
    Box {
        IconButton(onClick = { expanded = true }, modifier = Modifier.semantics { contentDescription = Strings.HOME_MENU }) {
            RcIconGlyph(RcIcon.MORE)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(Strings.RECEIVE_ALLOWANCE) }, onClick = { choose { appState.navigate(Screen.ReceiveAllowance) } })
            DropdownMenuItem(text = { Text(Strings.EXCHANGE) }, onClick = { choose { appState.navigate(Screen.Exchange) } })
            DropdownMenuItem(text = { Text(Strings.MANUAL_TITLE) }, onClick = { choose(appState::openManualSheet) })
            DropdownMenuItem(text = { Text(Strings.TAB_LEDGER) }, onClick = { choose { appState.navigate(Screen.Ledger) } })
            DropdownMenuItem(text = { Text(Strings.TAB_SETTINGS) }, onClick = { choose { appState.navigate(Screen.Settings) } })
            if (showDebugShortcut) {
                DropdownMenuItem(text = { Text(Strings.SIMULATOR_SHORTCUT) }, onClick = { choose { appState.navigate(Screen.NotificationSimulator) } })
            }
            DropdownMenuItem(text = { Text("꺼내기") }, onClick = { choose { appState.navigate(Screen.TakeOut(refill = true)) } })
        }
    }
}
