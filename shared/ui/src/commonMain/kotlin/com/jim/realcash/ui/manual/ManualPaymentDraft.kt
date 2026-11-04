package com.jim.realcash.ui.manual

import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.format.AmountInput

/** Immutable input and outcome of a manually entered payment; no repository or Compose state. */
data class ManualPaymentDraft(
    val digits: String = "",
    val merchant: String = "",
    val result: ManualPaymentResult? = null,
) {
    val amount: Won? get() = AmountInput.parse(digits)
    val amountNotCash: Boolean get() = amount?.isCashRepresentable == false
    val isValid: Boolean get() = amount?.let { it > Won.ZERO && it.isCashRepresentable } == true && result == null
    val merchantOrNull: String? get() = merchant.trim().takeIf { it.isNotEmpty() }

    fun withAmountText(text: String): ManualPaymentDraft = copy(digits = AmountInput.sanitize(text))
}

/** Display values from the actual repository settlement (no settlement calculation in the UI). */
data class ManualPaymentResult(
    val settlement: Settlement,
    val paid: Won,
) {
    val change: Won get() = settlement.changeUnits.total()
    val changeCounts: Map<Denomination, Int> get() = settlement.changeUnits.groupingBy { it.denomination }.eachCount()
}
