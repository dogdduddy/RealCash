package com.jim.realcash.ui.allowance

import com.jim.realcash.domain.calc.SplitStyle
import com.jim.realcash.domain.calc.SplitSuggester
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.format.AmountInput

/**
 * Editable state of the allowance screen (pure; unit-tested).
 *
 * Typing an amount or picking a style replaces [counts] with [SplitSuggester.suggest]; the steppers then edit the
 * counts freely ([edited] = true). The split is valid only when [SplitSuggester.validate] holds for a positive amount.
 */
data class AllowanceDraft(
    /** The amount field's digits (no separators). */
    val digits: String = "",
    /** Count per denomination; missing = 0. */
    val counts: Map<Denomination, Int> = emptyMap(),
    /** The last chosen suggestion style. */
    val style: SplitStyle = SplitStyle.LARGEST_FIRST,
    /** True once a stepper changed the suggested counts. */
    val edited: Boolean = false,
) {
    /** The typed amount, or `null` when the field is empty. */
    val amount: Won? get() = AmountInput.parse(digits)

    /** Sum of [counts]. */
    val sum: Won get() = counts.total()

    /** True when the typed amount cannot be paid in Korean cash (not a multiple of 10원). */
    val amountNotCash: Boolean get() = amount?.isCashRepresentable == false

    /** True when the split can be put into the wallet. */
    val isValid: Boolean
        get() {
            val a = amount ?: return false
            return a.value > 0 && SplitSuggester.validate(a, counts)
        }

    /** True when the sum line should be shown as an error (an amount is typed but the split does not match it). */
    val showSumError: Boolean get() = amount != null && !isValid

    /** Count of [denomination]. */
    fun countOf(denomination: Denomination): Int = counts[denomination] ?: 0

    /** The split to store: non-zero counts only, largest denomination first. */
    fun split(): Map<Denomination, Int> =
        Denomination.descending.mapNotNull { d -> countOf(d).takeIf { it > 0 }?.let { d to it } }.toMap()

    /** New amount text: digits only; the counts restart from the current style's suggestion. */
    fun withAmountText(text: String): AllowanceDraft {
        val newDigits = AmountInput.sanitize(text)
        return copy(digits = newDigits, counts = suggestion(AmountInput.parse(newDigits), style), edited = false)
    }

    /** Picks a suggestion [style]; the counts restart from its suggestion. */
    fun withStyle(style: SplitStyle): AllowanceDraft = copy(style = style, counts = suggestion(amount, style), edited = false)

    /** One more unit of [denomination]. */
    fun increment(denomination: Denomination): AllowanceDraft =
        copy(counts = counts + (denomination to countOf(denomination) + 1), edited = true)

    /** One unit less of [denomination] (never below zero). */
    fun decrement(denomination: Denomination): AllowanceDraft {
        val current = countOf(denomination)
        if (current == 0) return this
        return copy(counts = counts + (denomination to current - 1), edited = true)
    }

    private companion object {
        fun suggestion(amount: Won?, style: SplitStyle): Map<Denomination, Int> =
            if (amount == null || amount.value <= 0 || !amount.isCashRepresentable) emptyMap() else SplitSuggester.suggest(amount, style)
    }
}
