package com.jim.realcash.ui.exchange

import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.launchCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State holder of the 환전 screen.
 *
 * @param onDone called after a successful exchange (navigates back; the "환전 완료" snackbar is posted here).
 */
class ExchangeModel(
    private val scope: CoroutineScope,
    private val env: AppEnvironment,
    private val onDone: () -> Unit,
) {
    private val wallet = env.data.wallet
    private val _draft = MutableStateFlow(ExchangeDraft.of(wallet.state.value.inWallet))
    private val _submitting = MutableStateFlow(false)

    /** The editable draft. */
    val draft: StateFlow<ExchangeDraft> = _draft.asStateFlow()

    /** True while the exchange is being written. */
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    /** Keeps the draft in sync with the wallet while the screen is shown (run it in a `LaunchedEffect`). */
    suspend fun syncWithWallet() {
        wallet.state.collect { state -> _draft.update { it.rebase(state.inWallet) } }
    }

    /** Step 1: a wallet stack was tapped. */
    fun addToTray(denomination: Denomination) {
        if (_draft.value.remaining(denomination) == 0) return
        env.haptics.pickUp()
        _draft.update { it.addToTray(denomination) }
    }

    /** Step 1: a tray chip was tapped. */
    fun removeFromTray(denomination: Denomination) {
        env.haptics.tap()
        _draft.update { it.removeFromTray(denomination) }
    }

    /** "창구에 내기". */
    fun toReceive() {
        env.haptics.tap()
        _draft.update { it.toReceive() }
    }

    /** "내놓을 돈 고치기" / back from step 2. */
    fun backToGive() = _draft.update { it.backToGive() }

    /** A preset chip in step 2. */
    fun onSuggestion(suggestion: ExchangeSuggestion) {
        env.haptics.tap()
        _draft.update { it.withSuggestion(suggestion) }
    }

    /** "+" in step 2. */
    fun increment(denomination: Denomination) {
        env.haptics.tap()
        _draft.update { it.increment(denomination) }
    }

    /** "−" in step 2. */
    fun decrement(denomination: Denomination) {
        env.haptics.tap()
        _draft.update { it.decrement(denomination) }
    }

    /** "바꾸기": exchanges the tray for the chosen split, then calls `onDone`. */
    fun submit() {
        val d = _draft.value
        if (!d.isValid || _submitting.value) return
        _submitting.value = true
        scope.launchCatching(env.messages) {
            try {
                wallet.exchange(d.trayIds, d.toSplit)
                env.haptics.success()
                env.messages.show(Strings.EXCHANGE_DONE)
                onDone()
            } finally {
                _submitting.value = false
            }
        }
    }
}
