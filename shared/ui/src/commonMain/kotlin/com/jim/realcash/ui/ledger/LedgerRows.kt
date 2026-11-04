package com.jim.realcash.ui.ledger

import com.jim.realcash.data.PaymentLogEntry
import com.jim.realcash.domain.ledger.Adjusted
import com.jim.realcash.domain.ledger.AllowanceReceived
import com.jim.realcash.domain.ledger.Exchanged
import com.jim.realcash.domain.ledger.HandReturned
import com.jim.realcash.domain.ledger.LedgerEvent
import com.jim.realcash.domain.ledger.MovedToHand
import com.jim.realcash.domain.ledger.PaymentDetected
import com.jim.realcash.domain.ledger.Refunded
import com.jim.realcash.domain.ledger.ReturnReason
import com.jim.realcash.domain.ledger.Settled
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.Hand
import com.jim.realcash.domain.model.HandId
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.countsByDenomination
import com.jim.realcash.domain.model.total
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One entry of the 가계부 list. */
sealed interface LedgerRow {
    /** Id of the ledger event the row comes from (stable list key). */
    val eventId: String

    /** When it happened. */
    val at: Instant

    /**
     * A settled payment.
     *
     * @property amount payment amount from the detection (or the payment log), if known.
     * @property merchant merchant, if known.
     * @property sourceApp package / identifier of the notification, if known.
     * @property paid face value of the units handed over.
     * @property change face value of the change that came back.
     */
    data class Payment(
        override val eventId: String,
        override val at: Instant,
        val paymentId: PaymentId,
        val amount: Won?,
        val merchant: String?,
        val sourceApp: String?,
        val paid: Won,
        val change: Won,
        val shortfall: Won,
        val flags: Set<SettlementFlag>,
    ) : LedgerRow {
        /** Cash that actually left: paid minus change. */
        val cashOut: Won get() = paid - change
    }

    /** A refund whose units went back into the wallet. */
    data class Refund(
        override val eventId: String,
        override val at: Instant,
        val amount: Won,
        val merchant: String?,
    ) : LedgerRow

    /** An allowance. */
    data class Allowance(
        override val eventId: String,
        override val at: Instant,
        val total: Won,
        /** Received counts, largest first. */
        val split: Map<Denomination, Int>,
    ) : LedgerRow

    /** An exchange. */
    data class Exchange(
        override val eventId: String,
        override val at: Instant,
        /** Given counts, largest first. */
        val from: Map<Denomination, Int>,
        /** Received counts, largest first. */
        val to: Map<Denomination, Int>,
    ) : LedgerRow

    /** A hand that expired unused and went back into the wallet. */
    data class HandExpired(
        override val eventId: String,
        override val at: Instant,
        val amount: Won,
    ) : LedgerRow
}

/** Rows of one local day, newest first. */
data class LedgerDay(
    /** The day. */
    val date: LocalDate,
    /** Its rows, newest first. */
    val rows: List<LedgerRow>,
)

/**
 * Review numbers of the current budget period (design doc §3.3 G).
 *
 * @property spent cash that left in settlements of the period (paid minus change).
 * @property received allowances recorded for the period (by their `periodStart`).
 * @property remaining what is left now (wallet + hand).
 * @property directCount settlements of the period without [SettlementFlag.NOT_HAND_PICKED].
 * @property paymentCount settlements of the period.
 * @property brokenLargeBills 50,000원 / 10,000원 units handed over in settlements of the period.
 * @property planned sum of the hands' expected amounts, over settlements whose hand had one and whose amount is
 *   known; `null` when there is no such settlement.
 * @property actual sum of those settlements' payment amounts; `null` together with [planned].
 */
data class LedgerSummary(
    val period: ClosedRange<LocalDate>,
    val spent: Won,
    val received: Won,
    val remaining: Won,
    val directCount: Int,
    val paymentCount: Int,
    val brokenLargeBills: Int,
    val planned: Won?,
    val actual: Won?,
)

/** Everything the 가계부 tab renders. */
data class LedgerUi(
    /** `이번 주 · 11.2 (월) – 11.8 (일)`. */
    val periodLabel: String,
    /** Header numbers. */
    val summary: LedgerSummary,
    /** Days with rows, newest first. */
    val days: List<LedgerDay>,
    /** Today (for the "오늘" day header). */
    val today: LocalDate,
)

/** Pure mapping from the ledger history to the 가계부 tab. */
object LedgerRows {

    /** Large bills whose use counts as "깨진 큰 지폐". */
    private val LARGE_BILLS = setOf(Denomination.W50000, Denomination.W10000)

    /**
     * Builds the tab from [history] (oldest first, as stored).
     *
     * Payment amounts and merchants come from the `PaymentDetected` events in [history]; [paymentLog] is the
     * fallback for anything missing there.
     *
     * NOTE: settlements are read from the `Settled` events in [history] (same data as `WalletState.settlements`,
     * plus a timestamp), so no separate settlements argument is taken. Pending detections, rejections, hands that
     * were put back by the user or used in a settlement, and manual adjustments are not listed.
     */
    fun from(
        history: List<LedgerEvent>,
        paymentLog: List<PaymentLogEntry>,
        period: ClosedRange<LocalDate>,
        periodLabel: String,
        balance: Won,
        timeZone: TimeZone,
        today: LocalDate,
    ): LedgerUi {
        val units = HashMap<CashUnitId, CashUnit>()
        val hands = HashMap<HandId, Hand>()
        val payments = HashMap<PaymentId, DetectedPayment>()
        history.forEach { e ->
            when (e) {
                is AllowanceReceived -> e.units.forEach { units[it.id] = it }
                is Settled -> e.settlement.changeUnits.forEach { units[it.id] = it }
                is Refunded -> e.units.forEach { units[it.id] = it }
                is Exchanged -> e.toUnits.forEach { units[it.id] = it }
                is Adjusted -> e.addedUnits.forEach { units[it.id] = it }
                is MovedToHand -> hands[e.hand.id] = e.hand
                is PaymentDetected -> payments[e.payment.id] = e.payment
                else -> Unit
            }
        }
        val log = paymentLog.associateBy { it.paymentId }
        fun unitsOf(ids: List<CashUnitId>): List<CashUnit> = ids.mapNotNull { units[it] }
        fun amountOf(id: PaymentId): Won? = payments[id]?.amount ?: log[id]?.amount
        fun merchantOf(id: PaymentId): String? = payments[id]?.merchant ?: log[id]?.merchant

        val rows = history.mapNotNull { e ->
            when (e) {
                is Settled -> {
                    val s = e.settlement
                    LedgerRow.Payment(
                        eventId = e.id,
                        at = e.at,
                        paymentId = s.paymentId,
                        amount = amountOf(s.paymentId),
                        merchant = merchantOf(s.paymentId),
                        sourceApp = payments[s.paymentId]?.sourceApp ?: log[s.paymentId]?.sourceApp,
                        paid = unitsOf(s.paidUnitIds).total(),
                        change = s.changeUnits.total(),
                        shortfall = s.shortfall,
                        flags = s.flags,
                    )
                }
                is Refunded -> LedgerRow.Refund(e.id, e.at, e.units.total(), merchantOf(e.paymentId))
                is AllowanceReceived -> LedgerRow.Allowance(e.id, e.at, e.units.total(), e.units.countsByDenomination())
                is Exchanged -> LedgerRow.Exchange(
                    e.id,
                    e.at,
                    unitsOf(e.fromUnitIds).countsByDenomination(),
                    e.toUnits.countsByDenomination(),
                )
                is HandReturned -> if (e.reason == ReturnReason.EXPIRED) {
                    LedgerRow.HandExpired(e.id, e.at, unitsOf(hands[e.handId]?.unitIds.orEmpty()).total())
                } else {
                    null
                }
                else -> null
            }
        }

        fun Instant.date(): LocalDate = toLocalDateTime(timeZone).date
        val days = rows.asReversed()
            .groupBy { it.at.date() } // LinkedHashMap: keeps the newest-first order of first appearance
            .map { (date, dayRows) -> LedgerDay(date, dayRows) }

        val paymentsInPeriod = history.filterIsInstance<Settled>().filter { it.at.date() in period }
        var spent = Won.ZERO
        var broken = 0
        var planned: Won? = null
        var actual: Won? = null
        paymentsInPeriod.forEach { e ->
            val s = e.settlement
            val paidUnits = unitsOf(s.paidUnitIds)
            spent += paidUnits.total() - s.changeUnits.total()
            broken += paidUnits.count { it.denomination in LARGE_BILLS }
            val expected = s.handId?.let { hands[it] }?.expectedAmount
            val amount = amountOf(s.paymentId)
            if (expected != null && amount != null) {
                planned = (planned ?: Won.ZERO) + expected
                actual = (actual ?: Won.ZERO) + amount
            }
        }
        val received = history.filterIsInstance<AllowanceReceived>()
            .filter { it.periodStart in period }
            .fold(Won.ZERO) { acc, e -> acc + e.units.total() }

        val summary = LedgerSummary(
            period = period,
            spent = spent,
            received = received,
            remaining = balance,
            directCount = paymentsInPeriod.count { SettlementFlag.NOT_HAND_PICKED !in it.settlement.flags },
            paymentCount = paymentsInPeriod.size,
            brokenLargeBills = broken,
            planned = planned,
            actual = actual,
        )
        return LedgerUi(periodLabel, summary, days, today)
    }
}
