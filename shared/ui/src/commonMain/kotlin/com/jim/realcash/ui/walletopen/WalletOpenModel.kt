package com.jim.realcash.ui.walletopen

import com.jim.realcash.domain.ledger.WalletState
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.AppEnvironment
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private var queuedChoices = emptyMap<CashUnitId, Boolean>()
    private val trayOrder = repository.state.value.activeHand?.unitIds.orEmpty().toMutableList()
    private val choices = MutableStateFlow<Map<CashUnitId, Boolean>>(emptyMap())
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _paymentInfoOpen = MutableStateFlow(false)
    val paymentInfoOpen = _paymentInfoOpen.asStateFlow()

    private fun present(
        state: WalletState = repository.state.value,
        selection: Map<CashUnitId, Boolean> = choices.value,
    ): WalletOpenUi {
        val units = state.inWallet + state.inHand
        val savedOrder = state.activeHand?.unitIds.orEmpty()
        val order = if (selection.isEmpty()) savedOrder else trayOrder
        val hand = units.filter { selection[it.id] ?: (it.state == CashUnitState.IN_HAND) }
            .sortedBy { order.indexOf(it.id).takeIf { index -> index >= 0 } ?: Int.MAX_VALUE }
        val handIds = hand.map { it.id }
        val selectedIds = handIds.toSet()
        return WalletOpenUi(units.filter { it.id !in selectedIds }, hand, handIds != savedOrder)
    }

    val ui = combine(repository.state, choices) { state, selection -> present(state, selection) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), present())

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

    /** Returns the whole optimistic tray through the same debounced/queued path as a drop. */
    fun returnAll() {
        val units = present().inHand
        if (units.isEmpty()) return
        units.forEach { setOnTray(it.id, false) }
        trayOrder.clear()
        env.haptics.success()
    }

    /** A drop changes only presentation; the repository remains the ledger authority. */
    fun setOnTray(id: CashUnitId, onTray: Boolean) {
        val unit = repository.state.value.units[id] ?: return
        if (unit.state != CashUnitState.IN_WALLET && unit.state != CashUnitState.IN_HAND) return
        if (choices.value.isEmpty()) {
            trayOrder.clear()
            trayOrder.addAll(repository.state.value.activeHand?.unitIds.orEmpty())
        }
        if (onTray) { if (id !in trayOrder) trayOrder += id } else trayOrder.remove(id)
        choices.value = choices.value + (id to onTray)
        if (_submitting.value) {
            queuedChoices = queuedChoices + (id to onTray)
            return
        }
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
        val draft = present()
        if (!draft.changed) {
            choices.value = emptyMap()
            val callback = afterSave
            afterSave = null
            callback?.invoke()
            return
        }
        val ids = draft.inHand.map { it.id }
        // Keep the optimistic tray steady through returnHand's intermediate emission.
        choices.value = (draft.inWallet + draft.inHand).associate { it.id to (it.id in ids) }
        queuedChoices = emptyMap()
        _submitting.value = true
        scope.launch {
            var succeeded = false
            var cancelled = false
            try {
                val ttl = env.data.settings.load().handTtl
                if (repository.state.value.activeHand != null) repository.returnHand()
                if (ids.isNotEmpty()) repository.moveToHand(ids, ttl = ttl)
                succeeded = true
            } catch (error: CancellationException) {
                cancelled = true
                physics.clear()
                throw error
            } catch (_: Exception) {
                physics.clear()
                env.messages.show("접시를 저장하지 못했어요. 현재 지갑 상태로 되돌렸어요.")
            } finally {
                // Only retire the saved snapshot. Drops received during the write overlay its result,
                // including a partial failure, and are flushed before a navigation callback can run.
                choices.value = queuedChoices
                queuedChoices = emptyMap()
                val callback = afterSave
                afterSave = null
                _submitting.value = false
                if (!cancelled) {
                    if (choices.value.isNotEmpty()) {
                        if (succeeded) afterSave = callback
                        save()
                    } else if (succeeded) callback?.invoke()
                }
            }
        }
    }
}
