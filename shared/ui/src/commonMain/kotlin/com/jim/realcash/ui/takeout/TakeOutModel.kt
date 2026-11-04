package com.jim.realcash.ui.takeout

import com.jim.realcash.domain.ledger.ReturnReason
import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.HandId
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.launchCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State holder of the 꺼내기 screen.
 *
 * @param refill "더 꺼내기": the current hand's units start out picked and are pickable together with the wallet.
 * @param onDone called after the hand was taken out (navigates back).
 */
class TakeOutModel(
    private val scope: CoroutineScope,
    private val env: AppEnvironment,
    val refill: Boolean,
    private val onDone: () -> Unit,
) {
    private val wallet = env.data.wallet

    /** The hand being refilled, if any (captured when the screen opens). */
    private val refillHandId: HandId? = if (refill) wallet.state.value.activeHand?.id else null

    private val _draft = MutableStateFlow(initialDraft(wallet.state.value))
    private val _submitting = MutableStateFlow(false)

    /** The editable draft. */
    val draft: StateFlow<TakeOutDraft> = _draft.asStateFlow()

    /** True while the hand is being written. */
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    /** Drag & drop state of the screen (kept here so it survives recomposition and tests can drive it). */
    val drag = DragDropState()

    private fun initialDraft(state: WalletState): TakeOutDraft {
        val hand = state.activeHand?.takeIf { it.id == refillHandId }
        return TakeOutDraft.of(availableIn(state), picked = hand?.unitIds.orEmpty(), expected = hand?.expectedAmount)
    }

    private fun availableIn(state: WalletState): List<CashUnit> {
        val refilling = refillHandId != null && state.activeHand?.id == refillHandId
        return if (refilling) state.inWallet + state.inHand else state.inWallet
    }

    /** Keeps the draft in sync with the wallet while the screen is shown (run it in a `LaunchedEffect`). */
    suspend fun syncWithWallet() {
        wallet.state.collect { state -> _draft.update { it.rebase(availableIn(state)) } }
    }

    /** The expected amount field changed. */
    fun onExpectedChange(text: String) = _draft.update { it.withExpectedText(text) }

    /** Tap on a stack (fallback to dragging): picks one unit of [denomination]. */
    fun tapAdd(denomination: Denomination) {
        if (_draft.value.remaining(denomination) == 0) return
        env.haptics.tap()
        _draft.update { it.add(denomination) }
    }

    /** A drag started on a stack. */
    fun onDragStart() = env.haptics.pickUp()

    /** A dragged unit entered the hand zone. */
    fun onEnterZone() = env.haptics.tap()

    /** A dragged unit of [denomination] was dropped on the hand zone. */
    fun drop(denomination: Denomination) {
        if (_draft.value.remaining(denomination) == 0) return
        env.haptics.success()
        _draft.update { it.add(denomination) }
    }

    /** Tap on a chip in the hand zone: puts one unit of [denomination] back. */
    fun putBack(denomination: Denomination) {
        env.haptics.tap()
        _draft.update { it.remove(denomination) }
    }

    /** "잔돈으로 딱 맞추기". */
    fun fitExact() {
        env.haptics.tap()
        _draft.update { it.fitExact() }
    }

    /** "가장 적은 장수로". */
    fun fewest() {
        env.haptics.tap()
        _draft.update { it.fewest() }
    }

    /** "손에 들기": moves the picked units into a new hand with the configured TTL, then calls `onDone`. */
    fun confirm() {
        val d = _draft.value
        if (!d.canConfirm || _submitting.value) return
        _submitting.value = true
        scope.launchCatching(env.messages) {
            try {
                val ttl = env.data.settings.load().handTtl
                // NOTE: only one hand can be active (engineering plan §1), so "더 꺼내기" puts the current hand back
                // and takes the combined pick out as a new hand. These are two ledger events, not one atomic step;
                // if the second one fails the cash simply stays in the wallet and the error is shown.
                if (refillHandId != null && wallet.state.value.activeHand?.id == refillHandId) {
                    wallet.returnHand(ReturnReason.USER)
                }
                wallet.moveToHand(d.pickedIds, expectedAmount = d.expected, memo = null, ttl = ttl)
                env.haptics.success()
                env.messages.show(Strings.heldInHand(d.pickedTotal.formatted()))
                onDone()
            } finally {
                _submitting.value = false
            }
        }
    }
}
