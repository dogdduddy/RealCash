package com.jim.realcash.data

import com.jim.realcash.domain.ledger.HandReturned
import com.jim.realcash.domain.ledger.PaymentDetected
import com.jim.realcash.domain.ledger.ReturnReason
import com.jim.realcash.domain.ledger.Settled
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.parser.DefaultRules
import com.jim.realcash.domain.parser.ParserRule
import com.jim.realcash.domain.parser.RawNotification
import com.jim.realcash.domain.parser.RuleBasedParser
import com.jim.realcash.domain.model.PaymentId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.jim.realcash.domain.util.SequentialIdGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.builtins.ListSerializer

class PaymentIntakeTest {
    private val periodStart = LocalDate(2026, 11, 2)

    private fun samsung(text: String, at: Instant) =
        RawNotification(DefaultRules.SAMSUNG_WALLET, "결제 완료", text, null, at)

    @Test
    fun fullScenario() = runTest {
        val driver = inMemoryDriver()
        val clock = TestClock()
        val data = dataOf(driver, clock)
        val wallet = data.wallet
        val intake = data.intake

        // Allowance {10000x2, 5000, 1000x2}; take 10000 + 5000 out.
        wallet.receiveAllowance(mapOf(W10000 to 2, W5000 to 1, W1000 to 2), periodStart)
        val inWallet = wallet.state.value.inWallet
        val hand = listOf(inWallet.first { it.denomination == W10000 }, inWallet.first { it.denomination == W5000 })
        wallet.moveToHand(hand.map { it.id })

        // 12,000원 payment -> auto settled, 3,000원 change as 1000x3, hand released, no flags.
        clock.advance(1.minutes)
        val first = intake.onNotification(samsung("스타벅스 강남점에서 12,000원 결제", clock.now))
        assertIs<IntakeResult.AutoSettled>(first)
        assertEquals(Won(12_000), first.amount)
        assertEquals("스타벅스 강남점", first.merchant)
        assertEquals(Won(3_000), first.changeTotal)
        assertEquals(emptySet(), first.flags)
        assertNull(first.expiredHand)
        assertEquals(List(3) { W1000 }, first.settlement.changeUnits.map { it.denomination })
        var state = wallet.state.value
        assertNull(state.activeHand)
        assertTrue(first.settlement.changeUnits.all { state.units.getValue(it.id).state == CashUnitState.IN_WALLET })
        assertTrue(first.settlement.changeUnits.all { it.source == CashUnitSource.CHANGE })
        assertTrue(hand.all { state.units.getValue(it.id).state == CashUnitState.SPENT })
        assertEquals(mapOf(W10000 to 1, W1000 to 5), state.countsByDenomination(CashUnitState.IN_WALLET))

        // The same notification again 30s later -> duplicate, nothing changes.
        clock.advance(30.seconds)
        val ledgerRows = driver.rowCount("ledger_event")
        val dup = intake.onNotification(samsung("스타벅스 강남점에서 12,000원 결제", clock.now))
        assertIs<IntakeResult.Duplicate>(dup)
        assertEquals(state, wallet.state.value)
        assertEquals(ledgerRows, driver.rowCount("ledger_event"))

        // A low-confidence rule -> NEEDS_CONFIRM, then settled manually.
        val lowRule = ParserRule(
            id = "test-low",
            sourceApps = listOf("test.app"),
            amountRegex = DefaultRules.AMOUNT_PATTERN,
            baseConfidence = 0.5f,
        )
        val report = data.parserRules.applyOverride(
            DataJson.encodeToString(ListSerializer(ParserRule.serializer()), DefaultRules.rules + lowRule),
        )
        assertTrue(report.accepted)
        data.settings.update { it.copy(enabledSourceApps = it.enabledSourceApps + "test.app") }
        clock.advance(5.minutes)
        val low = intake.onNotification(RawNotification("test.app", null, "3,000원 결제", null, clock.now))
        assertIs<IntakeResult.NeedsConfirm>(low)
        assertEquals(0.4f, low.payment.confidence, 1e-4f)
        assertTrue(low.payment.id in wallet.state.value.pendingPayments)
        val manual = wallet.settle(low.payment.id, PullPolicy.LARGEST_FIRST)
        assertEquals(setOf(SettlementFlag.NOT_HAND_PICKED, SettlementFlag.MANUAL), manual.settlement.flags)
        state = wallet.state.value
        assertEquals(mapOf(W5000 to 1, W1000 to 7), state.countsByDenomination(CashUnitState.IN_WALLET))
        assertEquals(Won(12_000), state.balance)

        // Refund of the first payment -> 12,000원 back as minimal change.
        clock.advance(10.minutes)
        val refund = intake.onNotification(samsung("스타벅스 강남점에서 12,000원 결제 취소", clock.now))
        assertIs<IntakeResult.Refunded>(refund)
        assertEquals(Won(12_000), refund.refundTotal)
        assertTrue(refund.units.all { it.source == CashUnitSource.REFUND })
        state = wallet.state.value
        assertEquals(Won(24_000), state.balance)
        assertEquals(mapOf(W10000 to 1, W5000 to 1, W1000 to 9), state.countsByDenomination(CashUnitState.IN_WALLET))
        assertTrue(state.pendingPayments.isEmpty())
        assertEquals(emptyList(), state.invariantViolations())

        // Every parsed notification is in the log.
        assertEquals(
            listOf(PaymentOutcome.REFUND, PaymentOutcome.NEEDS_CONFIRM, PaymentOutcome.DUPLICATE, PaymentOutcome.AUTO_SETTLED),
            data.paymentLog.recent().map { it.outcome },
        )
        assertEquals(state, dataOf(driver, clock).wallet.load(), "restart equivalence")
        driver.close()
    }

    @Test
    fun expiredHandIsReturnedBeforeSettling() = runTest {
        val driver = inMemoryDriver()
        val clock = TestClock()
        val data = dataOf(driver, clock)
        val wallet = data.wallet
        wallet.receiveAllowance(mapOf(W10000 to 2, W5000 to 1), periodStart)
        val tenK = wallet.state.value.inWallet.first { it.denomination == W10000 }
        wallet.moveToHand(listOf(tenK.id), ttl = 6.hours)

        clock.advance(7.hours)
        val result = data.intake.onNotification(samsung("김밥천국에서 12,000원 결제", clock.now))
        assertIs<IntakeResult.AutoSettled>(result)
        val expired = assertNotNull(result.expiredHand)
        assertEquals(ReturnReason.EXPIRED, expired.reason)
        assertTrue(SettlementFlag.NOT_HAND_PICKED in result.flags)
        assertNull(result.settlement.handId)

        val tail = data.ledgerStore.loadAll().takeLast(3)
        assertIs<HandReturned>(tail[0])
        assertIs<PaymentDetected>(tail[1])
        assertIs<Settled>(tail[2])
        assertEquals(emptyList(), wallet.state.value.invariantViolations())
        driver.close()
    }

    @Test
    fun handStillValidAtPostingTimeIsUsedEvenIfProcessedLate() = runTest {
        val driver = inMemoryDriver()
        val clock = TestClock()
        val data = dataOf(driver, clock)
        val wallet = data.wallet
        wallet.receiveAllowance(mapOf(W10000 to 2), periodStart)
        val tenK = wallet.state.value.inWallet.first()
        wallet.moveToHand(listOf(tenK.id), ttl = 6.hours)

        val postedAt = clock.now + 5.hours
        clock.advance(7.hours)
        val result = data.intake.onNotification(samsung("김밥천국에서 8,000원 결제", postedAt))
        assertIs<IntakeResult.AutoSettled>(result)
        assertNull(result.expiredHand)
        assertEquals(emptySet(), result.flags)
        driver.close()
    }

    @Test
    fun ignoredNotificationsChangeNothing() = runTest {
        val driver = inMemoryDriver()
        val data = dataOf(driver)
        data.wallet.receiveAllowance(mapOf(W10000 to 1), periodStart)
        val rows = driver.rowCount("ledger_event")

        val disabled = data.intake.onNotification(RawNotification("com.shcard.smartpay", "신한카드", "승인 6,500원", null, T0))
        assertEquals(IntakeResult.Ignored(IgnoreReason.SOURCE_DISABLED), disabled)
        val topUp = data.intake.onNotification(samsung("삼성페이 머니 10,000원 충전", T0))
        assertEquals(IntakeResult.Ignored(IgnoreReason.NOT_A_PAYMENT), topUp)

        assertEquals(rows, driver.rowCount("ledger_event"))
        assertEquals(0L, data.paymentLog.count())
        driver.close()
    }

    @Test
    fun insufficientFundsStillSettlesWithShortfall() = runTest {
        val driver = inMemoryDriver()
        val data = dataOf(driver)
        data.wallet.receiveAllowance(mapOf(W5000 to 1), periodStart)
        val result = data.intake.onNotification(samsung("마트에서 8,000원 결제", T0))
        assertIs<IntakeResult.AutoSettled>(result)
        assertEquals(Won(3_000), result.settlement.shortfall)
        assertTrue(SettlementFlag.INSUFFICIENT_FUNDS in result.flags)
        assertEquals(Won.ZERO, data.wallet.state.value.balance)
        driver.close()
    }

    @Test
    fun dedupeWindowBoundsAndLowConfidenceFloor() = runTest {
        val driver = inMemoryDriver()
        val clock = TestClock()
        val data = dataOf(driver, clock)
        data.wallet.receiveAllowance(mapOf(W10000 to 3), periodStart)

        val first = data.intake.onNotification(samsung("GS25에서 2,000원 결제", T0))
        assertIs<IntakeResult.AutoSettled>(first)
        // Exactly at the window edge (120s, inclusive) -> duplicate; one second later -> a new payment.
        assertIs<IntakeResult.Duplicate>(data.intake.onNotification(samsung("GS25에서 2,000원 결제", T0 + 120.seconds)))
        assertIs<IntakeResult.AutoSettled>(data.intake.onNotification(samsung("GS25에서 2,000원 결제", T0 + 121.seconds)))
        // A notification posted earlier than a logged one is compared too (window is symmetric).
        assertIs<IntakeResult.Duplicate>(data.intake.onNotification(samsung("GS25에서 2,000원 결제", T0 - 60.seconds)))
        // Different merchant -> not a duplicate.
        assertIs<IntakeResult.AutoSettled>(data.intake.onNotification(samsung("CU에서 2,000원 결제", T0 + 10.seconds)))

        // A minimum-confidence floor: "₩12,000" without merchant parses with confidence 0.8.
        val strict = PaymentIntake(
            data.wallet, data.settings, data.parserRules, data.paymentLog, SequentialIdGenerator("strict-"),
            minConfidence = 0.9f,
        )
        val rows = driver.rowCount("ledger_event")
        val ignored = strict.onNotification(samsung("₩12,000 결제되었습니다", T0 + 1.hours))
        assertIs<IntakeResult.Ignored>(ignored)
        assertEquals(IgnoreReason.LOW_CONFIDENCE, ignored.reason)
        assertEquals(rows, driver.rowCount("ledger_event"))
        val latest = data.paymentLog.observeRecent(limit = 1).first().single()
        assertEquals(PaymentOutcome.IGNORED_LOW_CONFIDENCE, latest.outcome)
        assertEquals(Won(12_000), latest.amount)
        driver.close()
    }

    @Test
    fun exactRedeliveryIsADuplicateAndLoggedOnce() = runTest {
        val driver = inMemoryDriver()
        val data = dataOf(driver)
        data.wallet.receiveAllowance(mapOf(W10000 to 2), periodStart)
        val raw = samsung("스타벅스 강남점에서 6,500원 결제", T0)

        val first = data.intake.onNotification(raw)
        assertIs<IntakeResult.AutoSettled>(first)
        assertEquals(PaymentId(NotificationKey.of(raw)), first.payment.id)
        val rows = driver.rowCount("ledger_event")
        val state = data.wallet.state.value

        // Same notification delivered again, much later (outside the heuristic window).
        val again = dataOf(driver, TestClock(T0 + 1.hours)).intake.onNotification(raw)
        assertIs<IntakeResult.Duplicate>(again)
        assertEquals(first.payment.id, again.payment.id)
        assertEquals(rows, driver.rowCount("ledger_event"))
        assertEquals(state, data.wallet.state.value)
        assertEquals(listOf(PaymentOutcome.AUTO_SETTLED), data.paymentLog.recent().map { it.outcome })
        assertTrue(data.wallet.hasPayment(first.payment.id))
        driver.close()
    }

    @Test
    fun crashBetweenLedgerAndLogIsBackfilled() = runTest {
        val driver = inMemoryDriver()
        val data = dataOf(driver)
        data.wallet.receiveAllowance(mapOf(W10000 to 2), periodStart)
        val raw = samsung("스타벅스 강남점에서 6,500원 결제", T0)

        // Ledger written, log write lost.
        val parsed = RuleBasedParser(DefaultRules.rules, SequentialIdGenerator("x-")).parse(raw)!!
        val payment = parsed.copy(id = PaymentId(NotificationKey.of(raw)))
        data.wallet.recordAndSettle(payment, PullPolicy.LARGEST_FIRST)
        assertEquals(0L, data.paymentLog.count())
        val rows = driver.rowCount("ledger_event")

        // After restart the platform re-delivers the notification.
        val restarted = dataOf(driver)
        val result = restarted.intake.onNotification(raw)
        assertIs<IntakeResult.Duplicate>(result)
        assertEquals(rows, driver.rowCount("ledger_event"))
        val log = restarted.paymentLog.recent()
        assertEquals(1, log.size)
        assertEquals(payment.id, log.single().paymentId)
        assertEquals(PaymentOutcome.AUTO_SETTLED, log.single().outcome)
        driver.close()
    }

    @Test
    fun heuristicDedupeStillAppliesToDifferentNotifications() = runTest {
        val driver = inMemoryDriver()
        val data = dataOf(driver)
        data.wallet.receiveAllowance(mapOf(W10000 to 3), periodStart)

        assertIs<IntakeResult.AutoSettled>(data.intake.onNotification(samsung("스타벅스 강남점에서 12,000원 결제", T0)))
        // Different text (no merchant), same amount, within the window -> duplicate by the heuristic.
        val other = data.intake.onNotification(samsung("₩12,000 결제되었습니다", T0 + 20.seconds))
        assertIs<IntakeResult.Duplicate>(other)
        // Same text, posted outside the window -> a new payment.
        val later = data.intake.onNotification(samsung("스타벅스 강남점에서 12,000원 결제", T0 + 5.minutes))
        assertIs<IntakeResult.AutoSettled>(later)
        assertEquals(
            listOf(PaymentOutcome.AUTO_SETTLED, PaymentOutcome.DUPLICATE, PaymentOutcome.AUTO_SETTLED),
            data.paymentLog.recent().map { it.outcome },
        )
        driver.close()
    }
}
