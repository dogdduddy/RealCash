package com.jim.realcash.android

import com.jim.realcash.android.settlement.SettlementNotificationText
import com.jim.realcash.data.IgnoreReason
import com.jim.realcash.data.IntakeResult
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SettlementNotificationTextTest {
    private val at = Instant.parse("2026-11-04T03:00:00Z")

    private fun payment(amount: Int, merchant: String? = "스타벅스", refund: Boolean = false) = DetectedPayment(
        id = PaymentId("p1"),
        amount = Won(amount),
        merchant = merchant,
        sourceApp = "com.samsung.android.spay",
        rawText = "raw",
        detectedAt = at,
        confidence = 0.9f,
        isRefund = refund,
    )

    private fun unit(d: Denomination, n: Int, source: CashUnitSource = CashUnitSource.CHANGE) =
        CashUnit(CashUnitId("u$n"), d, CashUnitState.IN_WALLET, at, source)

    private fun settlement(change: List<CashUnit>, flags: Set<SettlementFlag> = emptySet(), shortfall: Int = 0) =
        Settlement(PaymentId("p1"), handId = null, paidUnitIds = emptyList(), changeUnits = change, shortfall = Won(shortfall), flags = flags)

    @Test
    fun autoSettledWithMerchantAndChange() {
        val change = listOf(unit(Denomination.W1000, 1), unit(Denomination.W1000, 2), unit(Denomination.W1000, 3), unit(Denomination.C500, 4))
        val content = assertNotNull(SettlementNotificationText.of(IntakeResult.AutoSettled(payment(6_500), settlement(change))))
        assertEquals("p1", content.paymentId)
        assertEquals("정산 완료 · 스타벅스", content.title)
        assertEquals("거스름돈 ₩3,500", content.text)
        assertFalse(content.askConfirm)
    }

    @Test
    fun autoSettledWithoutMerchantShowsAmountAndNotHandPicked() {
        val result = IntakeResult.AutoSettled(payment(10_000, merchant = null), settlement(emptyList(), setOf(SettlementFlag.NOT_HAND_PICKED)))
        val content = assertNotNull(SettlementNotificationText.of(result))
        assertEquals("정산 완료 · ₩10,000", content.title)
        assertEquals("거스름돈 ₩0 · 직접 꺼내지 않은 결제", content.text)
    }

    @Test
    fun autoSettledListsWalletTopUpAndShortfall() {
        val flags = setOf(SettlementFlag.ADDED_FROM_WALLET, SettlementFlag.INSUFFICIENT_FUNDS)
        val content = assertNotNull(SettlementNotificationText.of(IntakeResult.AutoSettled(payment(20_000), settlement(emptyList(), flags, 3_000))))
        assertEquals("거스름돈 ₩0 · 지갑에서 추가로 꺼냄 · 지갑 잔액 부족 ₩3,000", content.text)
    }

    @Test
    fun needsConfirmAsksWithAmountThenMerchant() {
        val content = assertNotNull(SettlementNotificationText.of(IntakeResult.NeedsConfirm(payment(6_500))))
        assertEquals("확인 필요 · ₩6,500 스타벅스", content.title)
        assertTrue(content.askConfirm)
    }

    @Test
    fun needsConfirmWithoutMerchant() {
        val content = assertNotNull(SettlementNotificationText.of(IntakeResult.NeedsConfirm(payment(6_500, merchant = null))))
        assertEquals("확인 필요 · ₩6,500", content.title)
    }

    @Test
    fun refundGoesToTheWallet() {
        val units = listOf(unit(Denomination.W5000, 1, CashUnitSource.REFUND), unit(Denomination.W1000, 2, CashUnitSource.REFUND))
        val content = assertNotNull(SettlementNotificationText.of(IntakeResult.Refunded(payment(6_000, refund = true), units)))
        assertEquals("환불 · ₩6,000 지갑으로", content.title)
        assertEquals("스타벅스 결제 취소", content.text)
        assertFalse(content.askConfirm)
    }

    @Test
    fun duplicateAndIgnoredShowNothing() {
        assertNull(SettlementNotificationText.of(IntakeResult.Duplicate(payment(6_500))))
        assertNull(SettlementNotificationText.of(IntakeResult.Ignored(IgnoreReason.NOT_A_PAYMENT)))
        assertNull(SettlementNotificationText.of(IntakeResult.Ignored(IgnoreReason.LOW_CONFIDENCE, payment(6_500))))
    }

    @Test
    fun actionOutcomes() {
        val rejected = SettlementNotificationText.rejected("p1", Won(6_500), "스타벅스")
        assertEquals("기록하지 않음 · ₩6,500 스타벅스", rejected.title)
        assertFalse(rejected.askConfirm)
        val failed = SettlementNotificationText.failed("p1", null)
        assertEquals("처리하지 못했어요", failed.title)
        assertEquals("앱에서 확인해 주세요", failed.text)
    }
}
