package com.jim.realcash.domain.testing

import com.jim.realcash.domain.calc.CashUnitFactory
import com.jim.realcash.domain.ledger.AllowanceReceived
import com.jim.realcash.domain.ledger.LedgerEvent
import com.jim.realcash.domain.ledger.MovedToHand
import com.jim.realcash.domain.ledger.PaymentDetected
import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.ledger.fold
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.Hand
import com.jim.realcash.domain.model.HandId
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.SequentialIdGenerator
import kotlin.test.assertEquals
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

/** Fixed reference instant for deterministic tests. */
val T0: Instant = Instant.parse("2026-11-04T09:00:00Z")

/** Deterministic builders for units, payments and events. */
class Fixture {
    /** Id source shared by every builder of this fixture. */
    val ids = SequentialIdGenerator("u-")
    private var eventSeq = 0

    /** New IN_WALLET units with the given counts. */
    fun units(
        vararg counts: Pair<Denomination, Int>,
        at: Instant = T0,
        source: CashUnitSource = CashUnitSource.ALLOWANCE,
    ): List<CashUnit> = CashUnitFactory.create(counts.toMap(), at, source, ids)

    /** A fresh event id. */
    fun eventId(): String = "e-${++eventSeq}"

    /** A detected payment. */
    fun payment(
        amount: Int,
        merchant: String? = "스타벅스",
        at: Instant = T0,
        isRefund: Boolean = false,
    ): DetectedPayment = DetectedPayment(
        id = PaymentId("p-${ids.next()}"),
        amount = Won(amount),
        merchant = merchant,
        sourceApp = "test.app",
        rawText = "raw",
        detectedAt = at,
        confidence = 1f,
        isRefund = isRefund,
    )

    /** A hand holding [units]. */
    fun hand(units: List<CashUnit>, at: Instant = T0): Hand =
        Hand(id = HandId("h-${ids.next()}"), unitIds = units.map { it.id }, createdAt = at, expiresAt = Hand.defaultExpiry(at))

    /** Allowance event. */
    fun allowance(units: List<CashUnit>): AllowanceReceived =
        AllowanceReceived(eventId(), T0, units, LocalDate(2026, 11, 2))

    /** Move-to-hand event. */
    fun moved(hand: Hand): MovedToHand = MovedToHand(eventId(), T0, hand)

    /** Payment-detected event. */
    fun detected(payment: DetectedPayment): PaymentDetected = PaymentDetected(eventId(), T0, payment)

    /** Wallet holding [walletUnits], optionally with [handUnits] taken out into a hand, and pending [payments]. */
    fun state(
        walletUnits: List<CashUnit>,
        handUnits: List<CashUnit> = emptyList(),
        payments: List<DetectedPayment> = emptyList(),
    ): WalletState {
        val events = mutableListOf<LedgerEvent>(allowance(walletUnits + handUnits))
        if (handUnits.isNotEmpty()) events += moved(hand(handUnits))
        payments.forEach { events += detected(it) }
        return events.fold()
    }
}

/** Asserts that the wallet invariants hold. */
fun assertConsistent(state: WalletState) {
    assertEquals(emptyList(), state.invariantViolations(), "wallet invariants")
}
