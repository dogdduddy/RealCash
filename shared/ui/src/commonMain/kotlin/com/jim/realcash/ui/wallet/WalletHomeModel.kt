package com.jim.realcash.ui.wallet

import com.jim.realcash.data.AppSettings
import com.jim.realcash.domain.calc.BudgetPeriods
import com.jim.realcash.domain.ledger.PaymentDetected
import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.launchCatching
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime

/** Count of one denomination, shared with the take-out and exchange screens. */
data class DenominationCount(val denomination: Denomination, val count: Int)

/** Only the information visible on the closed-wallet home. */
data class WalletHomeUi(
    val loaded: Boolean,
    val periodLabel: String,
    val periodRange: String,
    val daysLeft: Int,
    /** Domain balance: wallet plus hand. */
    val balance: Won,
    val handTotal: Won,
    val walletEmpty: Boolean,
    val pendingCount: Int,
) {
    companion object {
        /** Uses the domain's period and balance without duplicating budget or money rules. */
        fun from(state: WalletState, settings: AppSettings, now: Instant, timeZone: TimeZone, loaded: Boolean): WalletHomeUi {
            val today = now.toLocalDateTime(timeZone).date
            val policy = settings.budgetPeriodPolicy
            val period = BudgetPeriods.currentPeriod(policy, today)
            return WalletHomeUi(
                loaded = loaded,
                periodLabel = if (policy.cycle == BudgetPeriodPolicy.Cycle.MONTHLY) Strings.THIS_MONTH else Strings.THIS_WEEK,
                periodRange = "${period.start.month.number}.${period.start.day} – ${period.endInclusive.month.number}.${period.endInclusive.day}",
                daysLeft = today.daysUntil(period.endInclusive),
                balance = state.balance,
                handTotal = state.inHand.total(),
                walletEmpty = state.inWallet.isEmpty(),
                pendingCount = state.pendingPayments.size,
            )
        }
    }
}

/** Observes the closed wallet and posts newly completed settlements as a single snackbar line. */
class WalletHomeModel(scope: CoroutineScope, private val env: AppEnvironment) {
    private val data = env.data
    private val loaded = MutableStateFlow(false)
    private val ticker = flow {
        while (true) {
            emit(env.clock.now())
            delay(1.minutes)
        }
    }

    val ui: StateFlow<WalletHomeUi> = combine(data.wallet.state, data.settings.settings, ticker, loaded) { state, settings, now, ready ->
        WalletHomeUi.from(state, settings, now, env.timeZone, ready)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        WalletHomeUi.from(data.wallet.state.value, data.settings.settings.value, env.clock.now(), env.timeZone, false),
    )

    init {
        scope.launchCatching(env.messages) {
            data.settings.load()
            data.wallet.load()
            loaded.value = true
            // The loaded settlement is history. Only a new settlement produces a message.
            data.wallet.state.map { it.settlements.lastOrNull() }.distinctUntilChanged().drop(1).collect { settlement ->
                if (settlement != null) {
                    val payment = data.wallet.history.value.filterIsInstance<PaymentDetected>()
                        .lastOrNull { it.payment.id == settlement.paymentId }?.payment
                    env.messages.show(Strings.settlementSnackbar(payment?.amount?.formatted()))
                }
            }
        }
    }
}
