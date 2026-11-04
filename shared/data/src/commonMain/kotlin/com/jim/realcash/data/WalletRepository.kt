package com.jim.realcash.data

import com.jim.realcash.domain.calc.CashUnitFactory
import com.jim.realcash.domain.calc.HandExpiry
import com.jim.realcash.domain.calc.Settler
import com.jim.realcash.domain.ledger.Adjusted
import com.jim.realcash.domain.ledger.AllowanceReceived
import com.jim.realcash.domain.ledger.Exchanged
import com.jim.realcash.domain.ledger.HandReturned
import com.jim.realcash.domain.ledger.InvalidLedgerEventException
import com.jim.realcash.domain.ledger.LedgerEvent
import com.jim.realcash.domain.ledger.MovedToHand
import com.jim.realcash.domain.ledger.PaymentDetected
import com.jim.realcash.domain.ledger.PaymentRejected
import com.jim.realcash.domain.ledger.Refunded
import com.jim.realcash.domain.ledger.ReturnReason
import com.jim.realcash.domain.ledger.Settled
import com.jim.realcash.domain.ledger.WalletReducer
import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.ledger.fold
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.Hand
import com.jim.realcash.domain.model.HandId
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.PullPolicy
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.util.IdGenerator
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

/**
 * The single entry point for reading and changing the wallet. Used by the UI and by platform services.
 *
 * The ledger in the database is the source of truth; [state] is the fold of every stored event, kept in memory.
 * Every mutation:
 * 1. builds the event(s) from the current state,
 * 2. validates them with [WalletReducer.reduce] (an invalid event throws and never reaches the database),
 * 3. appends them atomically to the store,
 * 4. publishes the new state on [state].
 *
 * Mutations are serialised with a [Mutex], so concurrent callers (UI + notification service) cannot interleave.
 *
 * Errors: [IllegalArgumentException] for malformed input (negative counts, non-positive TTL, ...);
 * [IllegalStateException] — including the domain's [InvalidLedgerEventException] — when the request does not
 * fit the current wallet (unknown unit, a second hand, payment not pending, ...).
 */
class WalletRepository internal constructor(
    private val store: LedgerStore,
    private val clock: Clock,
    private val ids: IdGenerator,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(WalletState.EMPTY)
    private val _history = MutableStateFlow<List<LedgerEvent>>(emptyList())
    private var loaded = false

    /** Ledger status of every payment id ever detected. Guarded by [mutex]; derived from the same events as [state]. */
    private val paymentStatus = mutableMapOf<PaymentId, PaymentLedgerStatus>()

    /**
     * Current wallet state. Holds [WalletState.EMPTY] until the ledger has been loaded, which happens on the
     * first call to [load] or to any mutation; call [load] at app start to avoid showing an empty wallet.
     */
    val state: StateFlow<WalletState> = _state.asStateFlow()

    // NOTE(T5b): the ledger tab needs the events themselves (timestamps, allowances, exchanges, expired hands), not
    // only their fold. The list is filled on [load] and appended after every successful mutation under the same
    // [mutex], so it always equals `store.loadAll()` in append order. Kept in memory like [state]; when snapshots
    // arrive (see ensureLoaded), this should become a paged query instead.
    /**
     * Every stored ledger event in append order (oldest first). Empty until the ledger has been loaded, exactly
     * like [state]; [state] is always the fold of this list.
     */
    val history: StateFlow<List<LedgerEvent>> = _history.asStateFlow()

    /** Loads and folds the ledger if that has not happened yet; returns the current state. Idempotent. */
    suspend fun load(): WalletState = mutex.withLock { ensureLoaded() }

    /**
     * True if the ledger already knows payment [id] in any form: pending, settled, rejected or refunded.
     * Loads the ledger first if necessary.
     */
    suspend fun hasPayment(id: PaymentId): Boolean = paymentStatusOf(id) != null

    /** Ledger status of payment [id], or `null` if it was never detected. */
    internal suspend fun paymentStatusOf(id: PaymentId): PaymentLedgerStatus? = mutex.withLock {
        ensureLoaded()
        paymentStatus[id]
    }

    /** Adds a new allowance to the wallet: one unit per counted bill/coin in [split], source ALLOWANCE. */
    suspend fun receiveAllowance(split: Map<Denomination, Int>, periodStart: LocalDate): AllowanceReceived =
        mutate { _, now ->
            val units = CashUnitFactory.create(split, now, CashUnitSource.ALLOWANCE, ids)
            single(AllowanceReceived(ids.next(), now, units, periodStart))
        }

    /**
     * Takes [unitIds] (wallet units) out into a new hand that expires after [ttl].
     *
     * Callers normally pass `AppSettings.handTtl`; the default is the domain default ([Hand.DEFAULT_TTL], 6h).
     */
    suspend fun moveToHand(
        unitIds: List<CashUnitId>,
        expectedAmount: Won? = null,
        memo: String? = null,
        ttl: Duration = Hand.DEFAULT_TTL,
    ): MovedToHand {
        require(ttl.isPositive()) { "hand ttl must be positive, was $ttl" }
        return mutate { _, now ->
            val hand = Hand(
                id = HandId(ids.next()),
                unitIds = unitIds,
                createdAt = now,
                expiresAt = Hand.defaultExpiry(now, ttl),
                expectedAmount = expectedAmount,
                memo = memo,
            )
            single(MovedToHand(ids.next(), now, hand))
        }
    }

    /** Puts the active hand back into the wallet. Throws [IllegalStateException] if no hand is active. */
    suspend fun returnHand(reason: ReturnReason = ReturnReason.USER): HandReturned = mutate { state, now ->
        val hand = checkNotNull(state.activeHand) { "no active hand to return" }
        single(HandReturned(ids.next(), now, hand.id, reason))
    }

    /**
     * Returns the active hand with reason EXPIRED if it has expired at [now]; otherwise does nothing.
     * The event itself is stamped with the clock time. Returns the appended event, or `null`.
     */
    suspend fun returnHandIfExpired(now: Instant = clock.now()): HandReturned? = mutate { state, eventTime ->
        val hand = state.activeHand
        if (hand != null && HandExpiry.isExpired(hand, now)) {
            single(HandReturned(ids.next(), eventTime, hand.id, ReturnReason.EXPIRED))
        } else {
            Mutation(emptyList(), null)
        }
    }

    /** Records a detected (or manually entered) payment or refund as pending. */
    suspend fun recordDetectedPayment(payment: DetectedPayment): PaymentDetected = mutate { _, now ->
        single(PaymentDetected(ids.next(), now, payment))
    }

    /** The user said a pending detection was not a payment ("아니에요"). */
    suspend fun rejectPayment(id: PaymentId): PaymentRejected = mutate { _, now ->
        single(PaymentRejected(ids.next(), now, id))
    }

    /**
     * Settles the pending payment [paymentId] against the active hand and the wallet using [Settler.settle].
     *
     * NOTE: [manual] (default `true`) adds [SettlementFlag.MANUAL]. This public method is what the UI calls after
     * the user confirmed a payment; automatic settlements from [PaymentIntake] go through an internal path without
     * the flag.
     */
    suspend fun settle(paymentId: PaymentId, policy: PullPolicy, manual: Boolean = true): Settled =
        mutate { state, now ->
            val payment = checkNotNull(state.pendingPayments[paymentId]) { "payment ${paymentId.value} is not pending" }
            single(settledEvent(state, payment, policy, manual, now))
        }

    /**
     * Applies [refund] (a detection with `isRefund = true`): records it as pending unless it already is, then
     * returns its amount to the wallet as minimal-change units ([Settler.refundUnits], source REFUND).
     * Both events are validated and stored together.
     */
    suspend fun refund(refund: DetectedPayment): Refunded = mutate { state, now ->
        require(refund.isRefund) { "payment ${refund.id.value} is not a refund" }
        val events = mutableListOf<LedgerEvent>()
        if (refund.id !in state.pendingPayments) events += PaymentDetected(ids.next(), now, refund)
        val refunded = Refunded(ids.next(), now, refund.id, Settler.refundUnits(refund, now, ids))
        events += refunded
        Mutation(events, refunded)
    }

    /** Exchanges the wallet units [fromUnitIds] for new units counted in [toSplit] (same total, source EXCHANGE). */
    suspend fun exchange(fromUnitIds: List<CashUnitId>, toSplit: Map<Denomination, Int>): Exchanged =
        mutate { _, now ->
            val toUnits = CashUnitFactory.create(toSplit, now, CashUnitSource.EXCHANGE, ids)
            single(Exchanged(ids.next(), now, fromUnitIds, toUnits))
        }

    /** Manual correction: removes the wallet units [removed] and adds units counted in [addedSplit] (source ADJUSTMENT). */
    suspend fun adjust(removed: List<CashUnitId>, addedSplit: Map<Denomination, Int>, reason: String): Adjusted =
        mutate { _, now ->
            val added = CashUnitFactory.create(addedSplit, now, CashUnitSource.ADJUSTMENT, ids)
            single(Adjusted(ids.next(), now, removed, added, reason))
        }

    /**
     * Records [payment] and settles it in one validated, atomic step (no other mutation can slip in between).
     * Used by [PaymentIntake] for automatic settlement; no MANUAL flag.
     */
    internal suspend fun recordAndSettle(payment: DetectedPayment, policy: PullPolicy): Settled = mutate { state, now ->
        val detected = PaymentDetected(ids.next(), now, payment)
        val afterDetect = WalletReducer.reduce(state, detected)
        val settled = settledEvent(afterDetect, payment, policy, manual = false, now = now)
        Mutation(listOf(detected, settled), settled)
    }

    private fun settledEvent(
        state: WalletState,
        payment: DetectedPayment,
        policy: PullPolicy,
        manual: Boolean,
        now: Instant,
    ): Settled {
        val settlement = Settler.settle(state, payment, policy, now, ids)
        val flagged = if (manual) settlement.copy(flags = settlement.flags + SettlementFlag.MANUAL) else settlement
        return Settled(ids.next(), now, flagged)
    }

    // ---------------------------------------------------------------------------------------------

    private class Mutation<out R>(val events: List<LedgerEvent>, val result: R)

    private fun <E : LedgerEvent> single(event: E): Mutation<E> = Mutation(listOf(event), event)

    /** Must be called with [mutex] held. */
    private suspend fun ensureLoaded(): WalletState {
        if (!loaded) {
            // NOTE(T-later): load the latest state snapshot here and fold only the events stored after it.
            val events = store.loadAll()
            _state.value = events.fold()
            _history.value = events
            paymentStatus.clear()
            events.forEach(::track)
            loaded = true
        }
        return _state.value
    }

    private suspend fun <R> mutate(build: (state: WalletState, now: Instant) -> Mutation<R>): R = mutex.withLock {
        val current = ensureLoaded()
        val mutation = build(current, clock.now())
        var next = current
        // Validate everything before anything is persisted.
        for (event in mutation.events) next = WalletReducer.reduce(next, event)
        store.append(mutation.events)
        _state.value = next
        if (mutation.events.isNotEmpty()) _history.value = _history.value + mutation.events
        mutation.events.forEach(::track)
        mutation.result
    }

    /** Updates [paymentStatus] for an event that has already been validated. */
    private fun track(event: LedgerEvent) {
        when (event) {
            is PaymentDetected -> paymentStatus[event.payment.id] = PaymentLedgerStatus.PENDING
            is PaymentRejected -> paymentStatus[event.paymentId] = PaymentLedgerStatus.REJECTED
            is Settled -> paymentStatus[event.settlement.paymentId] = PaymentLedgerStatus.SETTLED
            // Refunding a settled payment directly keeps it SETTLED; only a pending refund detection becomes REFUNDED.
            is Refunded -> if (paymentStatus[event.paymentId] == PaymentLedgerStatus.PENDING) {
                paymentStatus[event.paymentId] = PaymentLedgerStatus.REFUNDED
            }
            else -> Unit
        }
    }
}

/** Where a detected payment stands in the ledger. */
internal enum class PaymentLedgerStatus {
    /** Detected, waiting for settlement or rejection. */
    PENDING,

    /** Settled (automatically or manually). */
    SETTLED,

    /** Rejected by the user. */
    REJECTED,

    /** A refund detection whose units went back into the wallet. */
    REFUNDED,
}
