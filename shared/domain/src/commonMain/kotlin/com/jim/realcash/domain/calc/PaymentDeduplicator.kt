package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.DetectedPayment
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Detects the same payment reported twice (e.g. by Samsung Wallet and by the card issuer's app). */
object PaymentDeduplicator {

    /** Default time window within which two notifications may describe the same payment. */
    val DEFAULT_WINDOW: Duration = 120.seconds

    /**
     * True if some payment in [recent] has the same amount, was detected within [window] of [candidate]
     * (inclusive), and either merchant is `null` or both normalise to the same string.
     *
     * NOTE: a refund and a payment are never duplicates of each other, even with the same amount.
     */
    fun isDuplicate(
        candidate: DetectedPayment,
        recent: List<DetectedPayment>,
        window: Duration = DEFAULT_WINDOW,
    ): Boolean = recent.any { other ->
        other.amount == candidate.amount &&
            other.isRefund == candidate.isRefund &&
            (other.detectedAt - candidate.detectedAt).absoluteValue <= window &&
            sameMerchant(other.merchant, candidate.merchant)
    }

    private fun sameMerchant(a: String?, b: String?): Boolean =
        a == null || b == null || normalizeMerchant(a) == normalizeMerchant(b)

    /**
     * Normalises a merchant name for comparison: drops corporate markers ("(주)", "㈜", "주식회사"),
     * whitespace and punctuation, and lower-cases the rest. "스타벅스 강남점" and "(주)스타벅스강남점" match.
     */
    fun normalizeMerchant(name: String): String =
        name.replace("(주)", "")
            .replace("㈜", "")
            .replace("주식회사", "")
            .filter { it.isLetterOrDigit() }
            .lowercase()
}
