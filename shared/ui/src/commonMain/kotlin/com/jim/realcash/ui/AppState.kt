package com.jim.realcash.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jim.realcash.data.RealCashData
import com.jim.realcash.ui.allowance.ReceiveAllowanceModel
import com.jim.realcash.ui.confirm.ConfirmPaymentsModel
import com.jim.realcash.ui.exchange.ExchangeModel
import com.jim.realcash.ui.ledger.LedgerModel
import com.jim.realcash.ui.manual.ManualPaymentModel
import com.jim.realcash.ui.settings.SettingsModel
import com.jim.realcash.ui.simulator.NotificationSimulatorModel
import com.jim.realcash.ui.takeout.TakeOutModel
import com.jim.realcash.ui.wallet.WalletHomeModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** The three bottom-bar tabs. */
enum class Tab(
    /** Label under the icon. */
    val label: String,
) {
    /** Wallet home. */
    WALLET(Strings.TAB_WALLET),

    /** Ledger / period review. */
    LEDGER(Strings.TAB_LEDGER),

    /** Settings with the debug section. */
    SETTINGS(Strings.TAB_SETTINGS),
}

/** Destinations of the hand-rolled navigation. Tab roots sit at the bottom of the back stack. */
sealed interface Screen {
    /** Root of [Tab.WALLET]. */
    data object WalletHome : Screen

    /** Root of [Tab.LEDGER]. */
    data object Ledger : Screen

    /** Root of [Tab.SETTINGS]. */
    data object Settings : Screen

    /** Receive an allowance (screen 2). */
    data object ReceiveAllowance : Screen

    /** Debug notification simulator (screen 4). */
    data object NotificationSimulator : Screen

    /**
     * Take cash out into the hand (꺼내기).
     *
     * @property refill "더 꺼내기": start from the current hand's units (see [com.jim.realcash.ui.takeout.TakeOutModel]).
     */
    data class TakeOut(val refill: Boolean = false) : Screen

    /** Exchange wallet units (환전). */
    data object Exchange : Screen
}

/** Root screen of [tab]. */
val Tab.root: Screen
    get() = when (this) {
        Tab.WALLET -> Screen.WalletHome
        Tab.LEDGER -> Screen.Ledger
        Tab.SETTINGS -> Screen.Settings
    }

/** One-shot user-visible messages (shown as snackbars by the root). */
class UiMessages {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Messages in the order they were posted. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** Posts [text]; never suspends. */
    fun show(text: String) {
        _messages.tryEmit(text)
    }
}

/**
 * What every model needs besides its scope: the data graph, time, haptics and the message sink.
 *
 * @property clock time source (injected so screenshots and tests are deterministic).
 * @property timeZone zone used for "today", period boundaries and time labels.
 */
class AppEnvironment(
    val data: RealCashData,
    val haptics: Haptics,
    val messages: UiMessages = UiMessages(),
    val clock: Clock = Clock.System,
    val timeZone: TimeZone = TimeZone.currentSystemDefault(),
) {
    /** Today's date in [timeZone]. */
    fun today(): LocalDate = clock.now().toLocalDateTime(timeZone).date
}

/**
 * Runs [block] in this scope and turns the data layer's expected failures ([IllegalStateException],
 * [IllegalArgumentException]) into a snackbar message instead of crashing.
 */
fun CoroutineScope.launchCatching(messages: UiMessages, block: suspend CoroutineScope.() -> Unit): Job = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: IllegalStateException) {
        messages.show(Strings.failed(e.message))
    } catch (e: IllegalArgumentException) {
        messages.show(Strings.failed(e.message))
    }
}

/**
 * Root state holder: owns the environment, the scope, the back stack and the lazily created screen models.
 *
 * Create it once per composition root ([RealCashApp] does) and call [start] once.
 *
 * @param scope lives as long as the UI; every repository call is launched here.
 */
class AppState(
    val env: AppEnvironment,
    val scope: CoroutineScope,
) {
    /** The snackbar host fed from [UiMessages]. */
    val snackbarHostState = SnackbarHostState()

    private val backStack = mutableStateListOf<Screen>(Screen.WalletHome)

    /** The selected bottom-bar tab. */
    var tab: Tab by mutableStateOf(Tab.WALLET)
        private set

    /** The screen on top of the back stack. */
    val current: Screen get() = backStack.last()

    /** True when a back action would pop a screen (the bottom bar is hidden then). */
    val canGoBack: Boolean get() = backStack.size > 1

    /** Whether the payment confirmation sheet (screen 3) is open. */
    var confirmSheetOpen: Boolean by mutableStateOf(false)

    /** Whether the manual payment fallback is open. */
    var manualSheetOpen: Boolean by mutableStateOf(false)
        private set

    val manualPayment: ManualPaymentModel by lazy { ManualPaymentModel(scope, env) }

    /** Wallet home model (created on first use). */
    val walletHome: WalletHomeModel by lazy { WalletHomeModel(scope, env) }

    /** Payment confirmation model (created on first use). */
    val confirmPayments: ConfirmPaymentsModel by lazy { ConfirmPaymentsModel(scope, env) }

    /** Notification simulator model; kept across visits so the last result stays visible. */
    val simulator: NotificationSimulatorModel by lazy { NotificationSimulatorModel(scope, env) }

    /** Ledger tab model (created on first use). */
    val ledger: LedgerModel by lazy { LedgerModel(scope, env) }

    /** Settings tab model (created on first use). */
    val settings: SettingsModel by lazy { SettingsModel(scope, env) }

    private var allowanceModel: ReceiveAllowanceModel? = null
    private var takeOutModel: TakeOutModel? = null
    private var exchangeModel: ExchangeModel? = null

    /** Allowance model; a fresh draft every time the screen is opened. */
    val receiveAllowance: ReceiveAllowanceModel
        get() = allowanceModel ?: ReceiveAllowanceModel(scope, env, onDone = ::back).also { allowanceModel = it }

    /** 꺼내기 model; a fresh draft every time the screen is opened (refill mode from the [Screen.TakeOut] on top). */
    val takeOut: TakeOutModel
        get() = takeOutModel ?: TakeOutModel(
            scope,
            env,
            refill = (backStack.lastOrNull { it is Screen.TakeOut } as? Screen.TakeOut)?.refill ?: false,
            onDone = ::back,
        ).also { takeOutModel = it }

    /** 환전 model; a fresh draft every time the screen is opened. */
    val exchange: ExchangeModel
        get() = exchangeModel ?: ExchangeModel(scope, env, onDone = ::back).also { exchangeModel = it }

    private var started = false

    /** Loads settings and the wallet ledger (the repository state is EMPTY until then). Idempotent. */
    fun start() {
        if (started) return
        started = true
        scope.launchCatching(env.messages) {
            env.data.settings.load()
            env.data.wallet.load()
        }
    }

    /** Switches to [tab], resetting the back stack to its root. */
    fun selectTab(tab: Tab) {
        this.tab = tab
        backStack.clear()
        backStack.add(tab.root)
    }

    /** Pushes [screen]. */
    fun navigate(screen: Screen) {
        resetModelOf(screen)
        backStack.add(screen)
    }

    /** Pops the top screen; returns false when already at a tab root. */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        val popped = backStack.removeAt(backStack.lastIndex)
        resetModelOf(popped)
        return true
    }

    /** Drops the per-visit model of [screen] so that the next visit starts from a fresh draft. */
    private fun resetModelOf(screen: Screen) {
        when (screen) {
            Screen.ReceiveAllowance -> allowanceModel = null
            is Screen.TakeOut -> takeOutModel = null
            Screen.Exchange -> exchangeModel = null
            else -> Unit
        }
    }

    /** Opens the payment confirmation sheet. */
    fun openConfirmSheet() {
        confirmPayments.beginSession()
        confirmSheetOpen = true
    }

    /** Closes the payment confirmation sheet. */
    fun closeConfirmSheet() {
        confirmSheetOpen = false
    }

    fun openManualSheet() {
        manualPayment.beginSession()
        manualSheetOpen = true
    }

    fun closeManualSheet() {
        if (!manualPayment.submitting.value) manualSheetOpen = false
    }
}
