package com.jim.realcash.data

import com.jim.realcash.domain.ledger.InvalidLedgerEventException
import com.jim.realcash.domain.ledger.ReturnReason
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.Denomination.C500
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.SequentialIdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

class WalletRepositoryTest {
    private val periodStart = LocalDate(2026, 11, 2)

    private fun payment(id: String, amount: Int, at: kotlin.time.Instant, refund: Boolean = false) = DetectedPayment(
        id = PaymentId(id),
        amount = Won(amount),
        merchant = "가게",
        sourceApp = "test.app",
        rawText = "raw",
        detectedAt = at,
        confidence = 1f,
        isRefund = refund,
    )

    @Test
    fun invalidMutationsThrowAndNeverReachTheDatabase() = runTest {
        val driver = inMemoryDriver()
        val wallet = dataOf(driver).wallet
        wallet.receiveAllowance(mapOf(W10000 to 2, W1000 to 2), periodStart)
        val units = wallet.state.value.inWallet
        wallet.moveToHand(listOf(units.first { it.denomination == W10000 }.id))
        val rows = driver.rowCount("ledger_event")
        val state = wallet.state.value

        // A unit that is not in the wallet.
        assertFailsWith<InvalidLedgerEventException> { wallet.moveToHand(listOf(CashUnitId("nope"))) }
        // A second hand while one is active.
        val other = units.first { it.denomination == W1000 }
        assertFailsWith<InvalidLedgerEventException> { wallet.moveToHand(listOf(other.id)) }
        // Exchange that does not balance.
        assertFailsWith<InvalidLedgerEventException> { wallet.exchange(listOf(other.id), mapOf(C500 to 1)) }
        // Settling a payment that was never detected.
        assertFailsWith<IllegalStateException> { wallet.settle(PaymentId("ghost"), PullPolicy.LARGEST_FIRST) }
        // Rejecting a payment that is not pending.
        assertFailsWith<InvalidLedgerEventException> { wallet.rejectPayment(PaymentId("ghost")) }
        // Empty allowance.
        assertFailsWith<InvalidLedgerEventException> { wallet.receiveAllowance(emptyMap(), periodStart) }
        // Malformed input.
        assertFailsWith<IllegalArgumentException> { wallet.receiveAllowance(mapOf(W1000 to -1), periodStart) }
        assertFailsWith<IllegalArgumentException> { wallet.moveToHand(listOf(other.id), ttl = (-1).hours) }

        assertEquals(rows, driver.rowCount("ledger_event"), "no invalid event was persisted")
        assertEquals(state, wallet.state.value, "in-memory state unchanged")
        assertEquals(state, dataOf(driver).wallet.load(), "stored ledger unchanged")

        wallet.returnHand()
        assertFailsWith<IllegalStateException> { wallet.returnHand() }
        driver.close()
    }

    @Test
    fun foldOfStoredLedgerEqualsInMemoryStateAfterEveryMutation() = runTest {
        val driver = inMemoryDriver()
        val clock = TestClock()
        val ids = SequentialIdGenerator("id-")
        val data = dataOf(driver, clock, ids)
        val wallet = data.wallet

        suspend fun assertRestartEquivalent() {
            assertEquals(wallet.state.value, dataOf(driver, clock, ids).wallet.load())
            assertEquals(emptyList(), wallet.state.value.invariantViolations())
        }

        wallet.receiveAllowance(mapOf(W50000 to 1, W10000 to 2, W5000 to 1, W1000 to 2), periodStart)
        assertRestartEquivalent()

        val inWallet = wallet.state.value.inWallet
        wallet.moveToHand(listOf(inWallet.first { it.denomination == W5000 }.id), ttl = 2.hours)
        assertRestartEquivalent()
        wallet.returnHand(ReturnReason.USER)
        assertRestartEquivalent()

        val fiftyK = inWallet.first { it.denomination == W50000 }
        wallet.exchange(listOf(fiftyK.id), mapOf(W10000 to 5))
        assertRestartEquivalent()

        clock.advance(10.minutes)
        val p1 = payment("p-1", 7_300, clock.now)
        wallet.recordDetectedPayment(p1)
        val settled = wallet.settle(p1.id, PullPolicy.LARGEST_FIRST)
        assertEquals(setOf(SettlementFlag.NOT_HAND_PICKED, SettlementFlag.MANUAL), settled.settlement.flags)
        assertRestartEquivalent()

        val p2 = payment("p-2", 1_000, clock.now)
        wallet.recordDetectedPayment(p2)
        wallet.rejectPayment(p2.id)
        assertRestartEquivalent()

        val refund = wallet.refund(payment("r-1", 7_300, clock.now, refund = true))
        assertEquals(Won(7_300), refund.units.fold(Won.ZERO) { acc, u -> acc + u.amount })
        assertRestartEquivalent()

        val oneK = wallet.state.value.inWallet.first { it.denomination == W1000 }
        wallet.adjust(listOf(oneK.id), mapOf(C500 to 2), "동전으로 바꿔 둠")
        assertRestartEquivalent()

        clock.advance(1.hours)
        wallet.moveToHand(listOf(wallet.state.value.inWallet.first { it.denomination == W10000 }.id), ttl = 30.minutes)
        assertNull(wallet.returnHandIfExpired(clock.now + 29.minutes))
        val expired = wallet.returnHandIfExpired(clock.now + 30.minutes)
        assertEquals(ReturnReason.EXPIRED, expired?.reason)
        assertRestartEquivalent()

        val balance = wallet.state.value.balance
        // 77,000 allowance - 7,300 paid + 7,300 refunded; adjust swapped 1,000 for 2x500.
        assertEquals(Won(77_000), balance)
        assertTrue(wallet.state.value.units.values.none { it.state == CashUnitState.IN_HAND })
        driver.close()
    }
}
