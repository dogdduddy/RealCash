package com.jim.realcash.data

import app.cash.sqldelight.db.QueryResult
import com.jim.realcash.data.db.DriverFactory
import com.jim.realcash.domain.ledger.fold
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.SequentialIdGenerator
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate

@OptIn(ExperimentalPathApi::class)
class LedgerStoreTest {

    @Test
    fun eventsSurviveReopeningTheDatabaseFile() = runTest {
        val dir = createTempDirectory("realcash-test")
        try {
            val path = dir.resolve("realcash.db").toString()
            val clock = TestClock()
            val ids = SequentialIdGenerator("id-")

            val driver1 = DriverFactory.forFile(path).createDriver()
            val first = dataOf(driver1, clock, ids)
            val wallet = first.wallet
            wallet.receiveAllowance(mapOf(W50000 to 1, W10000 to 2, W1000 to 3), LocalDate(2026, 11, 2))
            val tenK = wallet.state.value.inWallet.first { it.denomination == W10000 }
            wallet.moveToHand(listOf(tenK.id), expectedAmount = Won(8_000), memo = "점심")
            clock.advance(30.minutes)
            val payment = DetectedPayment(
                id = PaymentId("pay-1"),
                amount = Won(6_500),
                merchant = "스타벅스",
                sourceApp = "test.app",
                rawText = "raw",
                detectedAt = clock.now,
                confidence = 1f,
                isRefund = false,
            )
            wallet.recordDetectedPayment(payment)
            wallet.settle(payment.id, PullPolicy.LARGEST_FIRST)
            val fiftyK = wallet.state.value.inWallet.first { it.denomination == W50000 }
            wallet.exchange(listOf(fiftyK.id), mapOf(W10000 to 4, W5000 to 2))
            val appended = first.ledgerStore.loadAll()
            val stateBefore = wallet.state.value
            driver1.close()

            val driver2 = DriverFactory.forFile(path).createDriver()
            try {
                val second = dataOf(driver2, clock, ids)
                val reloaded = second.ledgerStore.loadAll()
                assertEquals(5, reloaded.size)
                assertEquals(appended, reloaded)
                assertEquals(stateBefore, reloaded.fold())
                assertEquals(stateBefore, second.wallet.load(), "restart equivalence")
                assertEquals(
                    listOf("AllowanceReceived", "MovedToHand", "PaymentDetected", "Settled", "Exchanged"),
                    driver2.ledgerTypes(),
                )
            } finally {
                driver2.close()
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun payloadUsesTheTestedJsonFormat() = runTest {
        val driver = inMemoryDriver()
        val data = dataOf(driver)
        data.wallet.receiveAllowance(mapOf(W10000 to 1), LocalDate(2026, 11, 2))
        val payload = driver.executeQuery(
            null,
            "SELECT payload FROM ledger_event",
            { c -> c.next(); QueryResult.Value(c.getString(0)!!) },
            0,
        ).value
        assertTrue("\"type\":\"AllowanceReceived\"" in payload, payload)
        assertTrue("\"periodStart\":\"2026-11-02\"" in payload, payload)
        assertTrue("\"at\":\"2026-11-04T09:00:00Z\"" in payload, payload)
        assertEquals(listOf("AllowanceReceived"), driver.ledgerTypes())
        driver.close()
    }
}
