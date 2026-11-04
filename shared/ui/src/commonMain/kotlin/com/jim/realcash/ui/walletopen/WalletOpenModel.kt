package com.jim.realcash.ui.walletopen

import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.AppEnvironment
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    val haptics get() = env.haptics
    val physics = WalletOpenPhysics()
    private var debounce: Job? = null
    private var afterSave: (() -> Unit)? = null
    private val trayOrder = repository.state.value.activeHand?.unitIds.orEmpty().toMutableList()
    private val choices = MutableStateFlow<Map<CashUnitId, Boolean>>(emptyMap())
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _paymentInfoOpen = MutableStateFlow(false)
    val paymentInfoOpen = _paymentInfoOpen.asStateFlow()

    private fun present(): WalletOpenUi {
        val state = repository.state.value
        return present(state.inWallet + state.inHand, choices.value,
            if (choices.value.isEmpty()) state.activeHand?.unitIds.orEmpty() else trayOrder)
    }
    private fun present(units: List<CashUnit>, selection: Map<CashUnitId, Boolean>, order: List<CashUnitId>): WalletOpenUi {
        val hand = units.filter { selection[it.id] ?: (it.state == CashUnitState.IN_HAND) }
            .sortedBy { order.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
        val handIds = hand.map { it.id }.toSet()
        return WalletOpenUi(units.filter { it.id !in handIds }, hand,
            units.any { (it.id in handIds) != (it.state == CashUnitState.IN_HAND) })
    }

    val ui = combine(repository.state, choices) { state, selection ->
        present(state.inWallet + state.inHand, selection, if (selection.isEmpty()) state.activeHand?.unitIds.orEmpty() else trayOrder)
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), present())

    fun toggle(id: CashUnitId) {
        if (_submitting.value) return
        val unit = repository.state.value.units[id] ?: return
        if (unit.state != CashUnitState.IN_WALLET && unit.state != CashUnitState.IN_HAND) return
        val selected = choices.value[id] ?: (unit.state == CashUnitState.IN_HAND)
        setOnTray(id, !selected)
        env.haptics.tap()
    }

    fun showPaymentInfo() { _paymentInfoOpen.value = true }
    fun closePaymentInfo() { _paymentInfoOpen.value = false }

    /** A drop changes only presentation; the repository remains the ledger authority. */
    fun setOnTray(id: CashUnitId, onTray: Boolean) {
        if (_submitting.value) return
        val unit = repository.state.value.units[id] ?: return
        if (unit.state != CashUnitState.IN_WALLET && unit.state != CashUnitState.IN_HAND) return
        if (choices.value.isEmpty()) {
            trayOrder.clear()
            trayOrder.addAll(repository.state.value.activeHand?.unitIds.orEmpty())
        }
        if (onTray) { if (id !in trayOrder) trayOrder += id } else trayOrder.remove(id)
        choices.value = choices.value + (id to onTray)
        debounce?.cancel()
        debounce = scope.launch { delay(300); save() }
    }

    /** Navigation flushes a pending debounce; callers wait for the same in-flight write. */
    fun confirm(onSaved: (() -> Unit)? = null) {
        debounce?.cancel()
        debounce = null
        if (_submitting.value) { if (afterSave == null) afterSave = onSaved; return }
        if (!present().changed) { choices.value = emptyMap(); onSaved?.invoke(); return }
        afterSave = onSaved
        save()
    }

    private fun save() {
        if (_submitting.value) return
        if (!present().changed) { choices.value = emptyMap(); return }
        _submitting.value = true
        scope.launch {
            try {
                val ttl = env.data.settings.load().handTtl
                val draft = present()
                val ids = draft.inHand.map { it.id }
                // Keep the optimistic tray steady through returnHand's intermediate emission.
                choices.value = (draft.inWallet + draft.inHand).associate { it.id to (it.id in ids) }
                if (repository.state.value.activeHand != null) repository.returnHand()
                if (ids.isNotEmpty()) repository.moveToHand(ids, ttl = ttl)
                choices.value = emptyMap()
                val callback = afterSave
                afterSave = null
                callback?.invoke()
            } catch (cancelled: CancellationException) {
                physics.clear()
                throw cancelled
            } catch (_: Exception) {
                physics.clear()
                env.messages.show("접시를 저장하지 못했어요. 현재 지갑 상태로 되돌렸어요.")
            } finally {
                // Includes partial replacement failures: the latest repository state wins.
                choices.value = emptyMap()
                afterSave = null
                _submitting.value = false
            }
        }
    }
}
