package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.Hand
import com.jim.realcash.domain.model.HandId
import com.jim.realcash.domain.testing.T0
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class HandExpiryTest {
    private val hand = Hand(HandId("h"), listOf(CashUnitId("u")), createdAt = T0, expiresAt = Hand.defaultExpiry(T0))

    @Test
    fun defaultTtlIsSixHours() {
        assertEquals(6.hours, Hand.DEFAULT_TTL)
        assertEquals(T0 + 6.hours, hand.expiresAt)
        assertEquals(T0 + 1.hours, Hand.defaultExpiry(T0, 1.hours))
    }

    @Test
    fun expiresExactlyAtExpiresAt() {
        assertFalse(HandExpiry.isExpired(hand, T0))
        assertFalse(HandExpiry.isExpired(hand, T0 + 6.hours - 1.seconds))
        assertTrue(HandExpiry.isExpired(hand, T0 + 6.hours))
        assertTrue(HandExpiry.isExpired(hand.copy(expiresAt = T0 + 30.minutes), T0 + 31.minutes))
    }
}
