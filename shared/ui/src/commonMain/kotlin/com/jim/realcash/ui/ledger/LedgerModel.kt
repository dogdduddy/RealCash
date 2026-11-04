package com.jim.realcash.ui.ledger

import com.jim.realcash.data.AppSettings
import com.jim.realcash.data.PaymentLogEntry
import com.jim.realcash.domain.calc.BudgetPeriods
import com.jim.realcash.domain.ledger.LedgerEvent
import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.model.BudgetPeriodPolicy
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.format.shortLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

/** State holder of the 가계부 tab: the ledger history, the wallet balance, the settings and the payment log. */
class LedgerModel(
    scope: CoroutineScope,
    private val env: AppEnvironment,
) {
    private val data = env.data

    /** The screen state. */
    val ui: StateFlow<LedgerUi> = combine(
        data.wallet.history,
        data.wallet.state,
        data.settings.settings,
        data.paymentLog.observeRecent(LOG_LOOKBACK).onStart { emit(emptyList()) },
    ) { history, state, settings, log -> build(history, state, settings, log) }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            build(data.wallet.history.value, data.wallet.state.value, data.settings.settings.value, emptyList()),
        )

    private fun build(history: List<LedgerEvent>, state: WalletState, settings: AppSettings, log: List<PaymentLogEntry>): LedgerUi {
        val today = env.today()
        val policy = settings.budgetPeriodPolicy
        val period = BudgetPeriods.currentPeriod(policy, today)
        val name = if (policy.cycle == BudgetPeriodPolicy.Cycle.WEEKLY) Strings.THIS_WEEK else Strings.THIS_MONTH
        val label = "$name · ${period.start.shortLabel()} – ${period.endInclusive.shortLabel()}"
        return LedgerRows.from(history, log, period, label, state.balance, env.timeZone, today)
    }

    private companion object {
        /** Payment log rows used as the merchant/amount fallback. */
        const val LOG_LOOKBACK = 200
    }
}
