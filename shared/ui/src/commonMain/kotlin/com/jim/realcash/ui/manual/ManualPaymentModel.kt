package com.jim.realcash.ui.manual

import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.AppEnvironment
import com.jim.realcash.ui.Strings
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Records a manual detection, then delegates all cash selection and settlement to the repository. */
class ManualPaymentModel(private val scope: CoroutineScope, private val env: AppEnvironment) {
    private val data = env.data
    private val _draft = MutableStateFlow(ManualPaymentDraft())
    val draft = _draft.asStateFlow()
    private val _submitting = MutableStateFlow(false)
    val submitting = _submitting.asStateFlow()
    private val _recorded = MutableStateFlow(false)
    val recorded = _recorded.asStateFlow()
    val wallet = data.wallet.state

    // Keep the detection after a partial failure, so retrying cannot create a second payment.
    private var payment: DetectedPayment? = null

    fun beginSession() {
        if (_submitting.value) return
        if (_recorded.value && payment?.id in data.wallet.state.value.pendingPayments) return
        // A pending payment may have been confirmed/rejected in the confirmation sheet while this sheet was closed.
        _recorded.value = false
        payment = null
        _draft.value = ManualPaymentDraft()
    }

    fun onAmountChange(text: String) {
        if (canEdit()) _draft.value = _draft.value.withAmountText(text)
    }

    fun onMerchantChange(text: String) {
        if (canEdit()) _draft.value = _draft.value.copy(merchant = text)
    }

    fun submit() {
        val draft = _draft.value
        if (_submitting.value || !draft.isValid) return
        _submitting.value = true
        scope.launch {
            try {
                val policy = data.settings.load().pullPolicy
                val p = payment ?: DetectedPayment(
                    id = PaymentId(data.ids.next()),
                    amount = checkNotNull(draft.amount),
                    merchant = draft.merchantOrNull,
                    sourceApp = "manual",
                    rawText = "",
                    detectedAt = env.clock.now(),
                    confidence = 1f,
                    isRefund = false,
                )
                if (!_recorded.value) {
                    data.wallet.recordDetectedPayment(p)
                    payment = p
                    _recorded.value = true
                }
                val settlement = data.wallet.settle(p.id, policy, manual = true).settlement
                val paid = settlement.paidUnitIds.mapNotNull { data.wallet.state.value.units[it] }.total()
                _draft.value = draft.copy(result = ManualPaymentResult(settlement, paid))
                _recorded.value = false
                env.haptics.success()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                env.messages.show(Strings.failed(e.message))
            } finally {
                _submitting.value = false
            }
        }
    }

    private fun canEdit(): Boolean = !_submitting.value && !_recorded.value && _draft.value.result == null
}
