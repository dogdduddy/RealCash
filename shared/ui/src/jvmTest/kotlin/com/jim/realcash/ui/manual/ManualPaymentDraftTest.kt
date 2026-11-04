package com.jim.realcash.ui.manual

import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.Won
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ManualPaymentDraftTest {
    @Test
    fun emptyAndZeroAmountsDisableSettlement() {
        assertNull(ManualPaymentDraft().amount)
        assertFalse(ManualPaymentDraft().isValid)
        assertFalse(ManualPaymentDraft(digits = "0").isValid)
        assertFalse(ManualPaymentDraft().withAmountText("0").isValid)
    }

    @Test
    fun nonCashAmountDisablesSettlement() {
        val draft = ManualPaymentDraft().withAmountText("6,505")
        assertTrue(draft.amountNotCash)
        assertFalse(draft.isValid)
    }

    @Test
    fun positiveCashAmountEnablesSettlementWithoutMerchant() {
        val draft = ManualPaymentDraft().withAmountText("₩6,500")
        assertEquals("6500", draft.digits)
        assertEquals(Won(6_500), draft.amount)
        assertTrue(draft.isValid)
        assertNull(draft.merchantOrNull)
        assertEquals("카페", draft.copy(merchant = "  카페  ").merchantOrNull)
        assertNull(draft.copy(merchant = "  ").merchantOrNull)
    }

    @Test
    fun invalidOrOverflowingRawAmountIsRejected() {
        listOf("abc", "2147483648", "-10").forEach { assertFalse(ManualPaymentDraft(digits = it).isValid) }
    }

    @Test
    fun completedDraftCannotBeSubmittedAgain() {
        val result = ManualPaymentResult(Settlement(PaymentId("manual-1"), null, emptyList(), emptyList(), Won.ZERO, emptySet()), Won.ZERO)
        val draft = ManualPaymentDraft(digits = "6500", result = result)
        assertFalse(draft.isValid)
        assertEquals(result, draft.result)
    }
}
