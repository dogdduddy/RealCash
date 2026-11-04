package com.jim.realcash.domain.calc

import com.jim.realcash.domain.model.Hand
import kotlin.time.Instant

/** Decides when a hand should go back into the wallet. */
object HandExpiry {
    /** True once [now] has reached the hand's `expiresAt`. */
    fun isExpired(hand: Hand, now: Instant): Boolean = now >= hand.expiresAt
}
