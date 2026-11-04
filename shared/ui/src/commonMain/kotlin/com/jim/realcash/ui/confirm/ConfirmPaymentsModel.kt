package com.jim.realcash.ui.confirm

import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.format.formatConfidence
import com.jim.realcash.ui.format.formatTime
import com.jim.realcash.ui.format.sourceAppLabel
import com.jim.realcash.ui.launchCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/** One pending payment row. */
data class PendingPaymentUi(
    /** Payment id. */
    val id: PaymentId,
    /** Amount. */
    val amount: Won,
    /** Merchant or [Strings.UNKNOWN_MERCHANT]. */
    val merchant: String,
    /** "삼성월렛", "토스" or the package name. */
    val sourceLabel: String,
    /** `14:05` or `11.3 14:05`. */
    val timeLabel: String,
    /** `85%`. */
    val confidenceLabel: String,
    /** True for a refund detection. */
    val isRefund: Boolean,
)

/** Outcome of a "맞아요" in the current sheet session. */
data class ConfirmedResultUi(
    /** Payment id. */
    val id: PaymentId,
    /** Amount. */
    val amount: Won,
    /** Merchant or [Strings.UNKNOWN_MERCHANT]. */
    val merchant: String,
    /** Change that went into the wallet (refund: the refunded amount). */
    val change: Won,
    /** Uncovered part of the payment. */
    val shortfall: Won,
    /** Settlement flags (empty for refunds). */
    val flags: Set<SettlementFlag>,
    /** True when this was a refund put back into the wallet. */
    val isRefund: Boolean,
    /** Actual change / refund units returned by the repository, grouped for artwork chips. */
    val changeCounts: Map<Denomination, Int> = emptyMap(),
)

/** State of the confirmation sheet. */
data class ConfirmPaymentsUi(
    /** Pending payments, newest first. */
    val pending: List<PendingPaymentUi>,
    /** Payments confirmed in this sheet session, newest first. */
    val confirmed: List<ConfirmedResultUi>,
    /** Payments with a request in flight (buttons disabled). */
    val busy: Set<PaymentId>,
) {
    /** Builders. */
    companion object {
        /** Pending rows of [state], newest first. */
        fun pendingOf(state: WalletState, timeZone: TimeZone, today: LocalDate): List<PendingPaymentUi> =
            state.pendingPayments.values.sortedByDescending { it.detectedAt }.map { p ->
                PendingPaymentUi(
                    id = p.id,
                    amount = p.amount,
                    merchant = p.merchant ?: Strings.UNKNOWN_MERCHANT,
                    sourceLabel = sourceAppLabel(p.sourceApp),
                    timeLabel = formatTime(p.detectedAt, timeZone, today),
                    confidenceLabel = formatConfidence(p.confidence),
                    isRefund = p.isRefund,
                )
            }
    }
}

/**
 * State holder of the payment confirmation sheet (screen 3): "맞아요" settles with the user's pull policy (or applies a
 * pending refund), "아니에요" rejects.
 */
class ConfirmPaymentsModel(
    private val scope: CoroutineScope,
    private val env: AppEnvironment,
) {
    private val data = env.data
    private val confirmed = MutableStateFlow<List<ConfirmedResultUi>>(emptyList())
    private val busy = MutableStateFlow<Set<PaymentId>>(emptySet())

    /** The sheet state. */
    val ui: StateFlow<ConfirmPaymentsUi> = combine(data.wallet.state, confirmed, busy) { state, done, inFlight ->
        ConfirmPaymentsUi(ConfirmPaymentsUi.pendingOf(state, env.timeZone, env.today()), done, inFlight)
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        ConfirmPaymentsUi(ConfirmPaymentsUi.pendingOf(data.wallet.state.value, env.timeZone, env.today()), emptyList(), emptySet()),
    )

    /** Clears the results of the previous sheet session. */
    fun beginSession() {
        confirmed.value = emptyList()
    }

    /**
     * "맞아요" for payment [id].
     *
     * NOTE: a pending *refund* detection cannot be settled; confirming it applies `WalletRepository.refund`
     * (units go back into the wallet) instead of `settle`, and the result card shows the refunded amount.
     */
    fun confirm(id: PaymentId) {
        val payment = data.wallet.state.value.pendingPayments[id] ?: return
        runFor(id) {
            val result = if (payment.isRefund) {
                val refunded = data.wallet.refund(payment)
                resultOf(payment, changeUnits = refunded.units, shortfall = Won.ZERO, flags = emptySet())
            } else {
                val policy = data.settings.load().pullPolicy
                val s = data.wallet.settle(id, policy).settlement
                resultOf(payment, changeUnits = s.changeUnits, shortfall = s.shortfall, flags = s.flags)
            }
            confirmed.update { listOf(result) + it }
            env.haptics.success()
        }
    }

    /** "아니에요" for payment [id]. */
    fun reject(id: PaymentId) {
        runFor(id) {
            data.wallet.rejectPayment(id)
            env.haptics.tap()
            env.messages.show(Strings.REJECTED)
        }
    }

    private fun runFor(id: PaymentId, block: suspend () -> Unit) {
        if (id in busy.value) return
        busy.update { it + id }
        scope.launchCatching(env.messages) {
            try {
                block()
            } finally {
                busy.update { it - id }
            }
        }
    }

    private fun resultOf(p: DetectedPayment, changeUnits: List<CashUnit>, shortfall: Won, flags: Set<SettlementFlag>) = ConfirmedResultUi(
        id = p.id,
        amount = p.amount,
        merchant = p.merchant ?: Strings.UNKNOWN_MERCHANT,
        change = changeUnits.total(),
        shortfall = shortfall,
        flags = flags,
        isRefund = p.isRefund,
        changeCounts = changeUnits.groupingBy { it.denomination }.eachCount(),
    )
}
