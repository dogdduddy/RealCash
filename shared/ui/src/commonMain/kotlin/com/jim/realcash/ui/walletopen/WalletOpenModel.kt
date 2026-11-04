package com.jim.realcash.ui.walletopen

import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.launchCatching
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Presentation-only selection; all ledger mutations stay in WalletRepository. */
data class WalletOpenUi(val inWallet: List<CashUnit>, val inHand: List<CashUnit>, val changed: Boolean) {
    val handTotal get() = inHand.total()
    val walletTotal get() = inWallet.total()
}

class WalletOpenModel(private val scope: CoroutineScope, private val env: AppEnvironment, val seed: Int = Random.nextInt()) {
    private val repository = env.data.wallet
    private val choices = MutableStateFlow<Map<CashUnitId, Boolean>>(emptyMap())
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _paymentInfoOpen = MutableStateFlow(false)
    val paymentInfoOpen = _paymentInfoOpen.asStateFlow()

    private fun present(): WalletOpenUi = present(repository.state.value.inWallet + repository.state.value.inHand, choices.value)
    private fun present(units: List<CashUnit>, selection: Map<CashUnitId, Boolean>): WalletOpenUi {
        val hand = units.filter { selection[it.id] ?: (it.state == CashUnitState.IN_HAND) }
        val handIds = hand.map { it.id }.toSet()
        return WalletOpenUi(units.filter { it.id !in handIds }, hand,
            units.any { (it.id in handIds) != (it.state == CashUnitState.IN_HAND) })
    }

    val ui = combine(repository.state, choices) { state, selection ->
        present(state.inWallet + state.inHand, selection)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), present())

    fun toggle(id: CashUnitId) {
        if (_submitting.value) return
        val unit = repository.state.value.units[id] ?: return
        if (unit.state != CashUnitState.IN_WALLET && unit.state != CashUnitState.IN_HAND) return
        val selected = choices.value[id] ?: (unit.state == CashUnitState.IN_HAND)
        choices.value = choices.value + (id to !selected)
        env.haptics.tap()
    }

    fun showPaymentInfo() { _paymentInfoOpen.value = true }
    fun closePaymentInfo() { _paymentInfoOpen.value = false }

    /** One moveToHand per confirmation; repeated taps are ignored until the write finishes. */
    fun confirm(onSaved: () -> Unit = {}) {
        if (_submitting.value) return
        if (!present().changed) { onSaved(); return }
        _submitting.value = true
        scope.launchCatching(env.messages) {
            try {
                val ttl = env.data.settings.load().handTtl
                val state = repository.state.value
                val draft = present()
                val ids = draft.inHand.map { it.id }
                // Preserve the whole draft if returning the previous hand succeeds but the next write fails.
                choices.value = (draft.inWallet + draft.inHand).associate { it.id to (it.id in ids) }
                if (state.activeHand != null) repository.returnHand()
                if (ids.isNotEmpty()) repository.moveToHand(ids, ttl = ttl)
                choices.value = emptyMap()
                env.haptics.success()
                onSaved()
            } finally {
                _submitting.value = false
            }
        }
    }
}
