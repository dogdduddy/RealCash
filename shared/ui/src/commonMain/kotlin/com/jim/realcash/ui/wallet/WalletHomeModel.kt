package com.jim.realcash.ui.wallet

import com.jim.realcash.data.AppSettings
import com.jim.realcash.data.PaymentLogEntry
import com.jim.realcash.domain.calc.BudgetPeriods
import com.jim.realcash.domain.ledger.ReturnReason
import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.countsByDenomination
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.format.shortLabel
import com.jim.realcash.ui.launchCatching
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime

/** Count of one denomination. */
data class DenominationCount(
    /** The denomination. */
    val denomination: Denomination,
    /** How many units. */
    val count: Int,
)

/** The "손에 든 돈" card. */
data class HandUi(
    /** Units in the hand, largest first. */
    val units: List<DenominationCount>,
    /** Total in the hand. */
    val total: Won,
    /** Amount the user expected to pay, if entered. */
    val expectedAmount: Won?,
    /** Time until the hand goes back to the wallet automatically (may be negative = expired). */
    val remaining: Duration,
)

/** The brief "정산 완료" card for the most recent settlement. */
data class SettlementUi(
    /** Settled payment. */
    val paymentId: PaymentId,
    /** Merchant from the payment log, if known. */
    val merchant: String?,
    /** Payment amount from the payment log, if known. */
    val amount: Won?,
    /** Change that went into the wallet. */
    val change: Won,
    /** Settlement flags. */
    val flags: Set<SettlementFlag>,
)

/** Everything the wallet home renders. Built by [WalletHomeUi.from], a pure function. */
data class WalletHomeUi(
    /** False until the ledger has been loaded (avoids flashing the empty state). */
    val loaded: Boolean,
    /** `이번 주 · 11.2 (월) – 11.8 (일)`. */
    val periodLabel: String,
    /** Days after today until the period ends (0 = ends today). */
    val daysLeft: Int,
    /** First day of the current period (used as the allowance's periodStart). */
    val periodStart: LocalDate,
    /** Wallet + hand. */
    val balance: Won,
    /** Wallet only. */
    val walletTotal: Won,
    /** Hand only. */
    val handTotal: Won,
    /** Bills in the wallet, largest first. */
    val bills: List<DenominationCount>,
    /** Coins in the wallet, largest first. */
    val coins: List<DenominationCount>,
    /** The active hand, if any. */
    val hand: HandUi?,
    /** Number of payments waiting for confirmation. */
    val pendingCount: Int,
    /** Most recent settlement, unless dismissed. */
    val lastSettlement: SettlementUi?,
) {
    /** True when the wallet holds nothing. */
    val walletEmpty: Boolean get() = bills.isEmpty() && coins.isEmpty()

    /** Builders. */
    companion object {
        /** Derives the screen state from the data layer's state at [now]. */
        fun from(
            state: WalletState,
            settings: AppSettings,
            log: List<PaymentLogEntry>,
            now: Instant,
            timeZone: TimeZone,
            dismissed: Set<PaymentId>,
            loaded: Boolean,
        ): WalletHomeUi {
            val today = now.toLocalDateTime(timeZone).date
            val policy = settings.budgetPeriodPolicy
            val period = BudgetPeriods.currentPeriod(policy, today)
            val periodName = if (policy.cycle == BudgetPeriodPolicy.Cycle.WEEKLY) Strings.THIS_WEEK else Strings.THIS_MONTH
            val inWallet = state.inWallet
            val counts = inWallet.countsByDenomination().map { (d, n) -> DenominationCount(d, n) }
            val hand = state.activeHand?.let { h ->
                val units: List<CashUnit> = state.inHand
                HandUi(
                    units = units.countsByDenomination().map { (d, n) -> DenominationCount(d, n) },
                    total = units.total(),
                    expectedAmount = h.expectedAmount,
                    remaining = h.expiresAt - now,
                )
            }
            val last = state.settlements.lastOrNull()?.takeIf { it.paymentId !in dismissed }?.let { s ->
                val entry = log.firstOrNull { it.paymentId == s.paymentId }
                SettlementUi(s.paymentId, entry?.merchant, entry?.amount, s.changeUnits.total(), s.flags)
            }
            return WalletHomeUi(
                loaded = loaded,
                periodLabel = "$periodName · ${period.start.shortLabel()} – ${period.endInclusive.shortLabel()}",
                daysLeft = today.daysUntil(period.endInclusive),
                periodStart = period.start,
                balance = state.balance,
                walletTotal = inWallet.total(),
                handTotal = state.inHand.total(),
                bills = counts.filter { it.denomination.kind == Denomination.Kind.BILL },
                coins = counts.filter { it.denomination.kind == Denomination.Kind.COIN },
                hand = hand,
                pendingCount = state.pendingPayments.size,
                lastSettlement = last,
            )
        }
    }
}

/**
 * State holder of the wallet home.
 *
 * Combines the wallet state, the settings, the recent payment log (for the settlement card's merchant/amount) and a
 * one-minute ticker (hand TTL, "days left").
 */
class WalletHomeModel(
    private val scope: CoroutineScope,
    private val env: AppEnvironment,
) {
    private val data = env.data
    private val loaded = MutableStateFlow(false)
    private val dismissed = MutableStateFlow<Set<PaymentId>>(emptySet())

    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(env.clock.now())
            delay(1.minutes)
        }
    }

    /** The screen state. */
    val ui: StateFlow<WalletHomeUi> = combine(
        combine(data.wallet.state, data.settings.settings, loaded, ::Triple),
        data.paymentLog.observeRecent(LOG_LOOKBACK).onStart { emit(emptyList()) },
        ticker,
        dismissed,
    ) { (state, settings, isLoaded), log, now, gone ->
        WalletHomeUi.from(state, settings, log, now, env.timeZone, gone, isLoaded)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        WalletHomeUi.from(
            data.wallet.state.value,
            data.settings.settings.value,
            emptyList(),
            env.clock.now(),
            env.timeZone,
            emptySet(),
            loaded = false,
        ),
    )

    init {
        scope.launchCatching(env.messages) {
            data.settings.load()
            data.wallet.load()
            loaded.value = true
        }
    }

    /** Puts the active hand back into the wallet ("지갑에 다시 넣기"). */
    fun returnHand() {
        val total = ui.value.handTotal
        scope.launchCatching(env.messages) {
            data.wallet.returnHand(ReturnReason.USER)
            env.haptics.success()
            env.messages.show(Strings.handReturned(total.formatted()))
        }
    }

    /** Hides the settlement card for [id]. */
    fun dismissSettlement(id: PaymentId) {
        dismissed.value = dismissed.value + id
    }

    private companion object {
        /** How many log rows are searched for the last settlement's merchant. */
        const val LOG_LOOKBACK = 20
    }
}
