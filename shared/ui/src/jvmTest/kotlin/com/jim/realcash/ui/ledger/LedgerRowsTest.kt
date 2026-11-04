package com.jim.realcash.ui.ledger

import com.jim.realcash.data.PaymentLogEntry
import com.jim.realcash.data.PaymentOutcome
import com.jim.realcash.domain.ledger.AllowanceReceived
import com.jim.realcash.domain.ledger.Exchanged
import com.jim.realcash.domain.ledger.HandReturned
import com.jim.realcash.domain.ledger.LedgerEvent
import com.jim.realcash.domain.ledger.MovedToHand
import com.jim.realcash.domain.ledger.PaymentDetected
import com.jim.realcash.domain.ledger.Refunded
import com.jim.realcash.domain.ledger.ReturnReason
import com.jim.realcash.domain.ledger.Settled
import com.jim.realcash.domain.ledger.fold
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Denomination.C100
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.Hand
import com.jim.realcash.domain.model.HandId
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.parser.DefaultRules
import com.jim.realcash.ui.format.exchangeSummary
import com.jim.realcash.ui.format.splitSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

class LedgerRowsTest {
    private val seoul = TimeZone.of("Asia/Seoul")
    private val period = LocalDate(2026, 11, 2)..LocalDate(2026, 11, 8)
    private val today = LocalDate(2026, 11, 4)

    /** `2026-11-dd hh:00` in Seoul. */
    private fun kst(day: Int, hour: Int): Instant = Instant.parse("2026-11-${day.toString().padStart(2, '0')}T00:00:00Z") + (hour - 9).hours

    private var seq = 0
    private fun unit(d: Denomination, at: Instant, source: CashUnitSource = CashUnitSource.ALLOWANCE) =
        CashUnit(CashUnitId("u-${++seq}"), d, CashUnitState.IN_WALLET, at, source)

    private fun payment(id: String, amount: Int, merchant: String?, at: Instant, refund: Boolean = false) =
        DetectedPayment(PaymentId(id), Won(amount), merchant, DefaultRules.SAMSUNG_WALLET, "raw", at, 1f, refund)

    /** A realistic history; returns it with a few units of interest. */
    private fun history(): List<LedgerEvent> {
        val events = mutableListOf<LedgerEvent>()
        // Last period's allowance (not counted as received this period).
        val old = kst(1, 9) - (24 * 5).hours
        events += AllowanceReceived("e-old", old, listOf(unit(W10000, old)), LocalDate(2026, 10, 26))
        val t1 = kst(2, 10)
        val fifty = unit(W50000, t1)
        val tens = List(2) { unit(W10000, t1) }
        val five = unit(W5000, t1)
        val ones = List(2) { unit(W1000, t1) }
        events += AllowanceReceived("e-1", t1, listOf(fifty) + tens + five + ones, period.start)
        // Hand with an expected amount, settled with change.
        val t2 = kst(3, 12)
        val h1 = Hand(HandId("h-1"), listOf(five.id, ones[0].id), t2, t2 + 6.hours, expectedAmount = Won(6_000))
        events += MovedToHand("e-2", t2, h1)
        events += PaymentDetected("e-3", t2 + 1.hours, payment("p-1", 5_800, "김밥천국", t2))
        events += Settled(
            "e-4",
            t2 + 1.hours,
            Settlement(PaymentId("p-1"), h1.id, h1.unitIds, List(2) { unit(C100, t2, CashUnitSource.CHANGE) }, Won.ZERO, emptySet()),
        )
        // No hand: a 50,000 bill is broken. The detection has no merchant; the payment log knows it.
        val t3 = kst(3, 18)
        events += PaymentDetected("e-5", t3, payment("p-2", 12_300, null, t3))
        val change = listOf(unit(W10000, t3, CashUnitSource.CHANGE), unit(W10000, t3, CashUnitSource.CHANGE), unit(W10000, t3, CashUnitSource.CHANGE)) +
            unit(W5000, t3, CashUnitSource.CHANGE) + List(2) { unit(W1000, t3, CashUnitSource.CHANGE) } +
            unit(C500, t3, CashUnitSource.CHANGE) + List(2) { unit(C100, t3, CashUnitSource.CHANGE) }
        events += Settled("e-6", t3, Settlement(PaymentId("p-2"), null, listOf(fifty.id), change, Won.ZERO, setOf(SettlementFlag.NOT_HAND_PICKED)))
        // Exchange 10,000 -> 5,000×2.
        val t4 = kst(4, 9)
        events += Exchanged("e-7", t4, listOf(tens[0].id), List(2) { unit(W5000, t4, CashUnitSource.EXCHANGE) })
        // A hand that expires unused.
        val h2 = Hand(HandId("h-2"), listOf(tens[1].id), t4, t4 + 1.hours)
        events += MovedToHand("e-8", t4 + 1.hours, h2)
        events += HandReturned("e-9", kst(4, 14), h2.id, ReturnReason.EXPIRED)
        // A refund.
        val t5 = kst(4, 15)
        events += PaymentDetected("e-10", t5, payment("r-1", 3_000, "환불가게", t5, refund = true))
        events += Refunded("e-11", t5, PaymentId("r-1"), List(3) { unit(W1000, t5, CashUnitSource.REFUND) })
        return events
    }

    private val log = listOf(
        PaymentLogEntry(PaymentId("p-2"), DefaultRules.TOSS, Won(12_300), "GS25 역삼점", kst(3, 18), "raw", PaymentOutcome.AUTO_SETTLED),
    )

    private fun build(events: List<LedgerEvent> = history()) =
        LedgerRows.from(events, log, period, "이번 주", events.fold().balance, seoul, today)

    @Test
    fun summaryOfThePeriod() {
        val events = history()
        val s = build(events).summary
        assertEquals(Won(5_800 + 12_300), s.spent, "paid minus change of both settlements")
        assertEquals(Won(77_000), s.received, "only this period's allowance")
        assertEquals(events.fold().balance, s.remaining)
        assertEquals(1, s.directCount)
        assertEquals(2, s.paymentCount)
        assertEquals(1, s.brokenLargeBills, "the 50,000 bill; the hand paid 5,000 + 1,000")
        assertEquals(Won(6_000), s.planned)
        assertEquals(Won(5_800), s.actual)
    }

    @Test
    fun rowsNewestFirstGroupedByDay() {
        val ui = build()
        assertEquals(
            listOf(LocalDate(2026, 11, 4), LocalDate(2026, 11, 3), LocalDate(2026, 11, 2), LocalDate(2026, 10, 27)),
            ui.days.map { it.date },
        )
        val all = ui.days.flatMap { it.rows }
        assertEquals(listOf("e-11", "e-9", "e-7", "e-6", "e-4", "e-1", "e-old"), all.map { it.eventId })
        assertTrue(all.zipWithNext().all { (a, b) -> a.at >= b.at })
    }

    @Test
    fun rowContents() {
        val rows = build().days.flatMap { it.rows }.associateBy { it.eventId }
        val refund = assertIs<LedgerRow.Refund>(rows.getValue("e-11"))
        assertEquals(Won(3_000), refund.amount)
        assertEquals("환불가게", refund.merchant)
        assertEquals(Won(10_000), assertIs<LedgerRow.HandExpired>(rows.getValue("e-9")).amount)
        val exchange = assertIs<LedgerRow.Exchange>(rows.getValue("e-7"))
        assertEquals("10,000×1 → 5,000×2", exchangeSummary(exchange.from, exchange.to))
        val broken = assertIs<LedgerRow.Payment>(rows.getValue("e-6"))
        assertEquals("GS25 역삼점", broken.merchant, "merchant from the payment log fallback")
        assertEquals(Won(12_300), broken.amount)
        assertEquals(Won(50_000), broken.paid)
        assertEquals(Won(37_700), broken.change)
        assertEquals(Won(12_300), broken.cashOut)
        assertEquals(setOf(SettlementFlag.NOT_HAND_PICKED), broken.flags)
        val hand = assertIs<LedgerRow.Payment>(rows.getValue("e-4"))
        assertEquals("김밥천국", hand.merchant)
        assertEquals(Won(6_000), hand.paid)
        assertEquals(Won(200), hand.change)
        val allowance = assertIs<LedgerRow.Allowance>(rows.getValue("e-1"))
        assertEquals(Won(77_000), allowance.total)
        assertEquals("50,000×1 + 10,000×2 + 5,000×1 + 1,000×2", splitSummary(allowance.split))
    }

    @Test
    fun userReturnsPendingAndRejectedAreNotListed() {
        val events = history().filterNot { it.id == "e-9" } +
            HandReturned("e-12", kst(4, 16), HandId("h-2"), ReturnReason.USER)
        val ids = build(events).days.flatMap { it.rows }.map { it.eventId }
        assertTrue("e-12" !in ids)
        assertTrue(ids.none { it in setOf("e-2", "e-3", "e-5", "e-8", "e-10") })
    }

    @Test
    fun emptyHistory() {
        val ui = LedgerRows.from(emptyList(), emptyList(), period, "이번 주", Won.ZERO, seoul, today)
        assertTrue(ui.days.isEmpty())
        assertEquals(Won.ZERO, ui.summary.spent)
        assertEquals(0, ui.summary.paymentCount)
        assertNull(ui.summary.planned)
        assertNull(ui.summary.actual)
    }
}
