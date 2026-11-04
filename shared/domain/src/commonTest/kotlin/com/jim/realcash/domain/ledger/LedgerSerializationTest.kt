package com.jim.realcash.domain.ledger

import com.jim.realcash.domain.calc.Settler
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.Denomination.W1000
import com.jim.realcash.domain.model.Denomination.W10000
import com.jim.realcash.domain.model.Denomination.W5000
import com.jim.realcash.domain.model.Denomination.W50000
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.testing.Fixture
import com.jim.realcash.domain.testing.T0
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Events are persisted by the data layer, so they must round-trip through kotlinx-serialization. */
class LedgerSerializationTest {
    private val fx = Fixture()

    @Test
    fun everyEventTypeRoundTrips() {
        val units = fx.units(W50000 to 1, W10000 to 2, W1000 to 5)
        val hand = fx.hand(units.filter { it.denomination == W10000 }.take(1)).copy(memo = "점심", expectedAmount = Won(8_000))
        val payment = fx.payment(6_500, merchant = "스타벅스")
        val refund = fx.payment(6_500, isRefund = true)
        val bogus = fx.payment(1_000, merchant = null)

        val events = mutableListOf<LedgerEvent>(fx.allowance(units), fx.moved(hand), fx.detected(payment))
        val settlement = Settler.settle(events.fold(), payment, PullPolicy.LARGEST_FIRST, T0, fx.ids)
        events += Settled(fx.eventId(), T0, settlement)
        events += fx.detected(refund)
        events += Refunded(fx.eventId(), T0, refund.id, Settler.refundUnits(refund, T0, fx.ids))
        val fiftyK = units.first { it.denomination == W50000 }
        events += Exchanged(fx.eventId(), T0, listOf(fiftyK.id), fx.units(W10000 to 5, source = CashUnitSource.EXCHANGE))
        val hand2 = fx.hand(units.filter { it.denomination == W1000 }.take(2))
        events += fx.moved(hand2)
        events += HandReturned(fx.eventId(), T0, hand2.id, ReturnReason.EXPIRED)
        events += fx.detected(bogus)
        events += PaymentRejected(fx.eventId(), T0, bogus.id)
        events += Adjusted(fx.eventId(), T0, listOf(units.last().id), fx.units(W5000 to 1, source = CashUnitSource.ADJUSTMENT), "found")

        val serializer = ListSerializer(LedgerEvent.serializer())
        val encoded = Json.encodeToString(serializer, events)
        val decoded = Json.decodeFromString(serializer, encoded)
        assertEquals(events, decoded)
        assertEquals(events.fold(), decoded.fold())
        assertTrue("\"type\":\"AllowanceReceived\"" in encoded)
        assertTrue("\"2026-11-04T09:00:00Z\"" in encoded, "instants are ISO-8601 strings")
        assertTrue("\"periodStart\":\"2026-11-02\"" in encoded)
        assertTrue(listOf("MovedToHand", "HandReturned", "PaymentDetected", "PaymentRejected", "Settled", "Refunded", "Exchanged", "Adjusted").all { "\"type\":\"$it\"" in encoded })
    }
}
