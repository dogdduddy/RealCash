package com.jim.realcash.domain.calc

import com.jim.realcash.domain.testing.Fixture
import com.jim.realcash.domain.testing.T0
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class PaymentDeduplicatorTest {
    private val fx = Fixture()
    private val first = fx.payment(6_500, merchant = "스타벅스 강남점", at = T0)

    @Test
    fun sameAmountNinetySecondsApartIsDuplicate() {
        val second = fx.payment(6_500, merchant = "스타벅스 강남점", at = T0 + 90.seconds)
        assertTrue(PaymentDeduplicator.isDuplicate(second, listOf(first)))
    }

    @Test
    fun windowIsInclusiveAndSymmetric() {
        assertTrue(PaymentDeduplicator.isDuplicate(fx.payment(6_500, merchant = "스타벅스 강남점", at = T0 + 120.seconds), listOf(first)))
        assertTrue(PaymentDeduplicator.isDuplicate(fx.payment(6_500, merchant = "스타벅스 강남점", at = T0 - 120.seconds), listOf(first)))
    }

    @Test
    fun hundredTwentyOneSecondsApartIsNotDuplicate() {
        val second = fx.payment(6_500, merchant = "스타벅스 강남점", at = T0 + 121.seconds)
        assertFalse(PaymentDeduplicator.isDuplicate(second, listOf(first)))
    }

    @Test
    fun differentMerchantIsNotDuplicate() {
        val second = fx.payment(6_500, merchant = "이디야", at = T0 + 10.seconds)
        assertFalse(PaymentDeduplicator.isDuplicate(second, listOf(first)))
    }

    @Test
    fun missingMerchantOnEitherSideCountsAsSame() {
        assertTrue(PaymentDeduplicator.isDuplicate(fx.payment(6_500, merchant = null, at = T0 + 5.seconds), listOf(first)))
        val noMerchant = fx.payment(6_500, merchant = null, at = T0)
        assertTrue(PaymentDeduplicator.isDuplicate(fx.payment(6_500, merchant = "이디야"), listOf(noMerchant)))
    }

    @Test
    fun merchantNamesAreNormalised() {
        val second = fx.payment(6_500, merchant = "(주)스타벅스강남점", at = T0 + 30.seconds)
        assertTrue(PaymentDeduplicator.isDuplicate(second, listOf(first)))
        assertEquals("starbucks강남점", PaymentDeduplicator.normalizeMerchant("㈜ Starbucks 강남점!"))
    }

    @Test
    fun differentAmountIsNotDuplicate() {
        assertFalse(PaymentDeduplicator.isDuplicate(fx.payment(6_000, merchant = "스타벅스 강남점"), listOf(first)))
    }

    @Test
    fun refundDoesNotDuplicatePayment() {
        val refund = fx.payment(6_500, merchant = "스타벅스 강남점", at = T0 + 10.seconds, isRefund = true)
        assertFalse(PaymentDeduplicator.isDuplicate(refund, listOf(first)))
    }

    @Test
    fun customWindowAndEmptyHistory() {
        assertFalse(PaymentDeduplicator.isDuplicate(first, emptyList()))
        val later = fx.payment(6_500, merchant = "스타벅스 강남점", at = T0 + 200.seconds)
        assertTrue(PaymentDeduplicator.isDuplicate(later, listOf(first), window = 300.seconds))
    }
}
