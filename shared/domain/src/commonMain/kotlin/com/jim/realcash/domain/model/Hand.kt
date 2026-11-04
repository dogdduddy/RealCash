package com.jim.realcash.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant
import kotlinx.serialization.Serializable

/** Cash the user has taken out of the wallet before paying ("holding in hand"). At most one is active. */
@Serializable
data class Hand(
    /** Identity of this hand. */
    val id: HandId,
    /** Units held. They are all in state [CashUnitState.IN_HAND] while the hand is active. */
    val unitIds: List<CashUnitId>,
    /** When the cash was taken out. */
    val createdAt: Instant,
    /**
     * When unused cash goes back to the wallet automatically. Normally [defaultExpiry] of [createdAt].
     *
     * NOTE: deliberately not a constructor default. kotlinx-serialization omits properties equal to their
     * default, so a computed default would make stored hands change meaning if [DEFAULT_TTL] ever changed.
     */
    val expiresAt: Instant,
    /** Amount the user expected to pay, if entered. */
    val expectedAmount: Won? = null,
    /** Free-form note. */
    val memo: String? = null,
) {
    /** Defaults for [Hand]. */
    companion object {
        /** Default time-to-live of a hand. */
        val DEFAULT_TTL: Duration = 6.hours

        /** The usual expiry for a hand created at [createdAt]: [createdAt] + [ttl]. */
        fun defaultExpiry(createdAt: Instant, ttl: Duration = DEFAULT_TTL): Instant = createdAt + ttl
    }
}
