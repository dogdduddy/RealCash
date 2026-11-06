package com.jim.realcash.ui.allowance

import com.jim.realcash.domain.calc.BudgetPeriods
import com.jim.realcash.domain.calc.SplitStyle
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.format.formatted
import com.jim.realcash.ui.launchCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate

/**
 * State holder of the allowance screen (screen 2).
 *
 * @param onDone called after the allowance was stored (navigates back).
 */
class ReceiveAllowanceModel(
    private val scope: CoroutineScope,
    private val env: AppEnvironment,
    private val onDone: () -> Unit,
) {
    private val _draft = MutableStateFlow(AllowanceDraft())
    private val _submitting = MutableStateFlow(false)

    /** The editable draft. */
    val draft: StateFlow<AllowanceDraft> = _draft.asStateFlow()

    /** True while the allowance is being written. */
    val submitting: StateFlow<Boolean> = _submitting.asStateFlow()

    /** First day of the current budget period; the allowance is recorded for it. */
    val periodStart: StateFlow<LocalDate> = env.data.settings.settings
        .map { periodStartFor(it.budgetPeriodPolicy) }
        .stateIn(scope, SharingStarted.Eagerly, periodStartFor(env.data.settings.settings.value.budgetPeriodPolicy))

    private fun periodStartFor(policy: com.jim.realcash.domain.model.BudgetPeriodPolicy): LocalDate =
        BudgetPeriods.currentPeriod(policy, env.today()).start

    /** The amount field changed. */
    fun onAmountChange(text: String) = _draft.update { it.withAmountText(text) }

    /** A suggestion chip was picked. */
    fun onStyle(style: SplitStyle) {
        env.haptics.tap()
        _draft.update { it.withStyle(style) }
    }

    /** "+" of [denomination]. */
    fun increment(denomination: Denomination) {
        env.haptics.tap()
        _draft.update { it.increment(denomination) }
    }

    /** "−" of [denomination]. */
    fun decrement(denomination: Denomination) {
        env.haptics.tap()
        _draft.update { it.decrement(denomination) }
    }

    /** Stores the allowance ("지갑에 넣기") if the draft is valid, then calls `onDone`. */
    fun submit() {
        val d = _draft.value
        if (!d.isValid || _submitting.value) return
        _submitting.value = true
        scope.launchCatching(env.messages) {
            try {
                val policy = env.data.settings.load().budgetPeriodPolicy
                env.data.wallet.receiveAllowance(d.split(), periodStartFor(policy))
                env.haptics.success()
                env.messages.show(Strings.allowanceReceived(d.sum.formatted()))
                onDone()
            } finally {
                _submitting.value = false
            }
        }
    }
}
