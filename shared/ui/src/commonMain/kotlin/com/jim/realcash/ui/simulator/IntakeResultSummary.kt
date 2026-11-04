package com.jim.realcash.ui.simulator

import com.jim.realcash.data.IgnoreReason
import com.jim.realcash.data.IntakeResult
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.ui.Strings
import com.jim.realcash.ui.format.formatted

/** Tone of a result line. */
enum class ResultTone {
    /** Money moved as expected. */
    SUCCESS,

    /** The user has to act. */
    ATTENTION,

    /** Nothing happened. */
    NEUTRAL,
}

/** An [IntakeResult] in words (simulator output). */
data class IntakeResultSummary(
    /** One-line outcome. */
    val headline: String,
    /** Further lines (change, flags, notes). */
    val details: List<String>,
    /** Tone for coloring. */
    val tone: ResultTone,
    /** Settlement flags, shown as badges (see `Strings.flagLabel`); not repeated in [details]. */
    val flags: Set<SettlementFlag> = emptySet(),
) {
    /** The mapping from [IntakeResult] to text. */
    companion object {
        /** Describes [result] in Korean. */
        fun of(result: IntakeResult): IntakeResultSummary {
            val subject = subject(result)
            val expiredNote = expiredHandNote(result)
            return when (result) {
                is IntakeResult.AutoSettled -> IntakeResultSummary(
                    headline = "자동 정산 · $subject",
                    details = listOfNotNull(
                        "거스름돈 ${result.changeTotal.formatted()}",
                        result.settlement.shortfall.takeIf { it > Won.ZERO }?.let { "부족 ${it.formatted()}" },
                        expiredNote,
                    ),
                    tone = ResultTone.SUCCESS,
                    flags = result.flags,
                )
                is IntakeResult.NeedsConfirm -> IntakeResultSummary(
                    headline = "확인 필요 · $subject",
                    details = listOfNotNull(
                        "지갑 홈에서 맞아요 / 아니에요 를 골라 주세요",
                        result.autoSettleError?.let { "자동 정산 실패: $it" },
                        expiredNote,
                    ),
                    tone = ResultTone.ATTENTION,
                )
                is IntakeResult.Duplicate -> IntakeResultSummary(
                    headline = "중복 알림 · $subject",
                    details = listOf("이미 처리한 결제라 아무것도 바뀌지 않았어요"),
                    tone = ResultTone.NEUTRAL,
                )
                is IntakeResult.Ignored -> IntakeResultSummary(
                    headline = "무시됨 · ${ignoreReasonText(result.reason)}",
                    details = listOfNotNull(result.payment?.let { "인식한 금액 ${it.amount.formatted()}" }),
                    tone = ResultTone.NEUTRAL,
                )
                is IntakeResult.Refunded -> IntakeResultSummary(
                    headline = "환불 · $subject",
                    details = listOfNotNull("${result.refundTotal.formatted()}을 지갑에 돌려놨어요", expiredNote),
                    tone = ResultTone.SUCCESS,
                )
            }
        }

        /** Why a notification was ignored, in words. */
        fun ignoreReasonText(reason: IgnoreReason): String = when (reason) {
            IgnoreReason.SOURCE_DISABLED -> "감지 대상 앱이 아니에요"
            IgnoreReason.NOT_A_PAYMENT -> "결제 알림이 아니에요"
            IgnoreReason.LOW_CONFIDENCE -> "인식 신뢰도가 너무 낮아요"
        }

        private fun subject(result: IntakeResult): String {
            val merchant = result.merchant ?: Strings.UNKNOWN_MERCHANT
            val amount = result.amount?.formatted() ?: ""
            return "$merchant $amount".trim()
        }

        private fun expiredHandNote(result: IntakeResult): String? {
            val expired = when (result) {
                is IntakeResult.AutoSettled -> result.expiredHand
                is IntakeResult.NeedsConfirm -> result.expiredHand
                is IntakeResult.Refunded -> result.expiredHand
                else -> null
            }
            return expired?.let { "시간이 지난 손의 돈을 먼저 지갑에 넣었어요" }
        }
    }
}
