package com.jim.realcash.data

import com.jim.realcash.domain.ledger.AllowanceReceived
import com.jim.realcash.domain.ledger.Exchanged
import com.jim.realcash.domain.ledger.HandReturned
import com.jim.realcash.domain.ledger.MovedToHand
import com.jim.realcash.domain.ledger.PaymentDetected
import com.jim.realcash.domain.ledger.Settled
import com.jim.realcash.domain.ledger.fold
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.SequentialIdGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

/** NOTE(T5b): [WalletRepository.history] mirrors the stored ledger. */
class WalletHistoryTest {
    private val periodStart = LocalDate(2026, 11, 2)

    @Test
    fun historyEqualsStoredLedgerAndSurvivesRestart() = runTest {
        val driver = inMemoryDriver()
        val clock = TestClock()
        val ids = SequentialIdGenerator("id-")
        val data = dataOf(driver, clock, ids)
        val wallet = data.wallet
        assertEquals(emptyList(), wallet.history.value, "empty before load")
        wallet.load()
        assertEquals(emptyList(), wallet.history.value)

        wallet.receiveAllowance(mapOf(W50000 to 1, W10000 to 2, W5000 to 1, W1000 to 3), periodStart)
        val fifty = wallet.state.value.inWallet.first { it.denomination == W50000 }
        wallet.exchange(listOf(fifty.id), mapOf(W10000 to 5))
        wallet.moveToHand(listOf(wallet.state.value.inWallet.first { it.denomination == W5000 }.id))
        clock.advance(5.minutes)
        val payment = DetectedPayment(PaymentId("p-1"), Won(4_500), "가게", "test.app", "raw", clock.now, 1f, false)
        wallet.recordDetectedPayment(payment)
        wallet.settle(payment.id, PullPolicy.LARGEST_FIRST)

        // A rejected mutation leaves the history untouched.
        val before = wallet.history.value
        assertFailsWith<IllegalStateException> { wallet.moveToHand(listOf(CashUnitId("nope"))) }
        assertEquals(before, wallet.history.value)

        val stored = data.ledgerStore.loadAll()
        assertEquals(stored, wallet.history.value, "in-memory history equals the stored ledger")
        assertEquals(
            listOf(AllowanceReceived::class, Exchanged::class, MovedToHand::class, PaymentDetected::class, Settled::class),
            wallet.history.value.map { it::class },
        )
        assertEquals(wallet.state.value, wallet.history.value.fold(), "state is the fold of the history")

        // After a restart the history is rebuilt from the database.
        val restarted = dataOf(driver, clock, ids)
        restarted.wallet.load()
        assertEquals(stored, restarted.wallet.history.value)
        assertEquals(restarted.ledgerStore.loadAll(), restarted.wallet.history.value)

        // ...and keeps appending there.
        wallet.returnHandIfExpired() // no active hand any more: nothing appended
        assertEquals(stored.size, wallet.history.value.size)
        restarted.wallet.moveToHand(listOf(restarted.wallet.state.value.inWallet.first().id))
        restarted.wallet.returnHand()
        assertTrue(restarted.wallet.history.value.last() is HandReturned)
        assertEquals(restarted.ledgerStore.loadAll(), restarted.wallet.history.value)
        driver.close()
    }
}
