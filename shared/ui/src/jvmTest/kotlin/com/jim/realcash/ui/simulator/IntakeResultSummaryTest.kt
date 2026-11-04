package com.jim.realcash.ui.simulator

import com.jim.realcash.data.IgnoreReason
import com.jim.realcash.data.IntakeResult
import com.jim.realcash.domain.ledger.HandReturned
import com.jim.realcash.domain.ledger.ReturnReason
import com.jim.realcash.domain.model.CashUnit
import com.jim.realcash.domain.model.CashUnitId
import com.jim.realcash.domain.model.CashUnitSource
import com.jim.realcash.domain.model.CashUnitState
import com.jim.realcash.domain.model.Denomination
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.HandId
import com.jim.realcash.domain.model.PaymentId
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.parser.DefaultRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class IntakeResultSummaryTest {
    private val now = Instant.parse("2026-11-04T09:00:00Z")

    private fun payment(amount: Int, merchant: String? = "스타벅스 강남점", refund: Boolean = false) = DetectedPayment(
        id = PaymentId("p-$amount"),
        amount = Won(amount),
        merchant = merchant,
        sourceApp = DefaultRules.SAMSUNG_WALLET,
        rawText = "raw",
        detectedAt = now,
        confidence = 1f,
        isRefund = refund,
    )

    private fun unit(d: Denomination, i: Int) = CashUnit(CashUnitId("u-$i"), d, CashUnitState.IN_WALLET, now, CashUnitSource.CHANGE)

    private fun settlement(change: List<CashUnit>, flags: Set<SettlementFlag>, shortfall: Won = Won.ZERO) =
        Settlement(PaymentId("p"), HandId("h"), emptyList(), change, shortfall, flags)

    @Test
    fun autoSettledWithChangeAndNoFlags() {
        val r = IntakeResult.AutoSettled(payment(6_500), settlement(listOf(unit(Denomination.W1000, 1), unit(Denomination.W1000, 2), unit(Denomination.W1000, 3), unit(Denomination.C500, 4)), emptySet()))
        val s = IntakeResultSummary.of(r)
        assertEquals("자동 정산 · 스타벅스 강남점 ₩6,500", s.headline)
        assertEquals(listOf("거스름돈 ₩3,500"), s.details)
        assertEquals(ResultTone.SUCCESS, s.tone)
        assertTrue(s.flags.isEmpty())
    }

    @Test
    fun autoSettledShowsFlagsShortfallAndExpiredHand() {
        val flags = setOf(SettlementFlag.ADDED_FROM_WALLET, SettlementFlag.NOT_HAND_PICKED, SettlementFlag.INSUFFICIENT_FUNDS)
        val expired = HandReturned("e", now, HandId("h"), ReturnReason.EXPIRED)
        val r = IntakeResult.AutoSettled(payment(12_000, merchant = null), settlement(emptyList(), flags, shortfall = Won(2_000)), expired)
        val s = IntakeResultSummary.of(r)
        assertEquals("자동 정산 · 가맹점 알 수 없음 ₩12,000", s.headline)
        assertEquals(
            listOf(
                "거스름돈 ₩0",
                "부족 ₩2,000",
                "시간이 지난 손의 돈을 먼저 지갑에 넣었어요",
            ),
            s.details,
        )
        assertEquals(flags, s.flags)
    }

    @Test
    fun needsConfirm() {
        val s = IntakeResultSummary.of(IntakeResult.NeedsConfirm(payment(3_000)))
        assertEquals("확인 필요 · 스타벅스 강남점 ₩3,000", s.headline)
        assertEquals(ResultTone.ATTENTION, s.tone)
        assertEquals(listOf("지갑 홈에서 맞아요 / 아니에요 를 골라 주세요"), s.details)

        val failed = IntakeResultSummary.of(IntakeResult.NeedsConfirm(payment(3_000), autoSettleError = "payment p-3000 is not pending"))
        assertTrue(failed.details.any { it == "자동 정산 실패: payment p-3000 is not pending" })
    }

    @Test
    fun duplicate() {
        val s = IntakeResultSummary.of(IntakeResult.Duplicate(payment(6_500)))
        assertEquals("중복 알림 · 스타벅스 강남점 ₩6,500", s.headline)
        assertEquals(ResultTone.NEUTRAL, s.tone)
    }

    @Test
    fun ignoredReasons() {
        assertEquals("무시됨 · 감지 대상 앱이 아니에요", IntakeResultSummary.of(IntakeResult.Ignored(IgnoreReason.SOURCE_DISABLED)).headline)
        assertEquals("무시됨 · 결제 알림이 아니에요", IntakeResultSummary.of(IntakeResult.Ignored(IgnoreReason.NOT_A_PAYMENT)).headline)
        val low = IntakeResultSummary.of(IntakeResult.Ignored(IgnoreReason.LOW_CONFIDENCE, payment(900)))
        assertEquals("무시됨 · 인식 신뢰도가 너무 낮아요", low.headline)
        assertEquals(listOf("인식한 금액 ₩900"), low.details)
        assertEquals(ResultTone.NEUTRAL, low.tone)
    }

    @Test
    fun refunded() {
        val units = listOf(unit(Denomination.W10000, 1), unit(Denomination.W1000, 2), unit(Denomination.W1000, 3))
        val s = IntakeResultSummary.of(IntakeResult.Refunded(payment(12_000, refund = true), units))
        assertEquals("환불 · 스타벅스 강남점 ₩12,000", s.headline)
        assertEquals(listOf("₩12,000을 지갑에 돌려놨어요"), s.details)
        assertEquals(ResultTone.SUCCESS, s.tone)
    }

    @Test
    fun presetsMatchTheDefaultRuleFormats() {
        assertEquals("스타벅스 강남점에서 6,500원 결제", SimulatorPresets.samsung().text)
        assertEquals(DefaultRules.SAMSUNG_WALLET, SimulatorPresets.samsung().packageName)
        assertEquals(DefaultRules.TOSS, SimulatorPresets.toss().packageName)
        assertEquals("김밥천국 역삼점에서 12,000원 결제 취소", SimulatorPresets.samsungCancel(Won(12_000), "김밥천국 역삼점").text)
        assertTrue(SimulatorPresets.samsung().canSend)
        assertTrue(!SimulatorForm(SimSource.CUSTOM).canSend)
    }
}
