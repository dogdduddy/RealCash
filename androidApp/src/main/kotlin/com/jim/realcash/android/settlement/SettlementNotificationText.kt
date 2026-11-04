package com.jim.realcash.android.settlement

import com.jim.realcash.data.IntakeResult
import com.jim.realcash.domain.model.DetectedPayment
import com.jim.realcash.domain.model.Settlement
import com.jim.realcash.domain.model.SettlementFlag
import com.jim.realcash.domain.model.Won
import com.jim.realcash.domain.model.total
import com.jim.realcash.ui.format.formatted

/** What our own settlement notification shows. */
data class SettlementNotificationContent(
    /** The payment the notification is about; also the key of the notification id. */
    val paymentId: String,
    /** First line. */
    val title: String,
    /** Second line (may span lines in the expanded view). */
    val text: String,
    /** Whether to show the 맞아요 / 아니에요 actions. */
    val askConfirm: Boolean = false,
)

/**
 * Pure mapping from intake results (and the outcome of the notification actions) to notification text.
 *
 * AutoSettled → "정산 완료 · <가맹점 또는 금액>" / "거스름돈 ₩x" (+ "직접 꺼내지 않은 결제");
 * NeedsConfirm → "확인 필요 · <금액> <가맹점>" with actions; Refunded → "환불 · ₩x 지갑으로";
 * Duplicate / Ignored → no notification.
 */
object SettlementNotificationText {
    /** Button label: the payment is real, settle it. */
    const val ACTION_CONFIRM = "맞아요"

    /** Button label: not a payment, drop it. */
    const val ACTION_REJECT = "아니에요"

    /** Notification channel name. */
    const val CHANNEL_NAME = "정산"

    /** Notification channel description. */
    const val CHANNEL_DESCRIPTION = "결제 알림을 지갑에서 정산한 결과와 확인 요청"

    /** The notification for [result], or `null` when nothing should be shown. */
    fun of(result: IntakeResult): SettlementNotificationContent? = when (result) {
        is IntakeResult.AutoSettled -> settled(result.payment, result.settlement)
        is IntakeResult.NeedsConfirm -> SettlementNotificationContent(
            paymentId = result.payment.id.value,
            title = "확인 필요 · ${amountAndMerchant(result.payment)}",
            text = "이 결제를 지갑에서 정산할까요?",
            askConfirm = true,
        )
        is IntakeResult.Refunded -> SettlementNotificationContent(
            paymentId = result.payment.id.value,
            title = "환불 · ${result.refundTotal.formatted()} 지갑으로",
            text = result.payment.merchant?.let { "$it 결제 취소" } ?: "결제 취소분을 거스름돈으로 돌려받았어요",
        )
        is IntakeResult.Duplicate, is IntakeResult.Ignored -> null
    }

    /** A settled payment: automatically by the intake, or after 맞아요. */
    fun settled(payment: DetectedPayment, settlement: Settlement): SettlementNotificationContent {
        val subject = payment.merchant ?: payment.amount.formatted()
        val lines = buildList {
            add("거스름돈 ${settlement.changeUnits.total().formatted()}")
            if (SettlementFlag.NOT_HAND_PICKED in settlement.flags) add("직접 꺼내지 않은 결제")
            if (SettlementFlag.ADDED_FROM_WALLET in settlement.flags) add("지갑에서 추가로 꺼냄")
            if (SettlementFlag.INSUFFICIENT_FUNDS in settlement.flags || settlement.shortfall > Won(0)) {
                add("지갑 잔액 부족 ${settlement.shortfall.formatted()}")
            }
        }
        return SettlementNotificationContent(
            paymentId = payment.id.value,
            title = "정산 완료 · $subject",
            text = lines.joinToString(" · "),
        )
    }

    /** After 아니에요: nothing left the wallet. */
    fun rejected(paymentId: String, amount: Won?, merchant: String?): SettlementNotificationContent =
        SettlementNotificationContent(
            paymentId = paymentId,
            title = "기록하지 않음 · ${listOfNotNull(amount?.formatted(), merchant).joinToString(" ").ifEmpty { "결제" }}",
            text = "지갑에서 아무것도 꺼내지 않았어요",
        )

    /** An action could not be applied (already handled in the app, or the wallet refused it). */
    fun failed(paymentId: String, reason: String?): SettlementNotificationContent = SettlementNotificationContent(
        paymentId = paymentId,
        title = "처리하지 못했어요",
        text = reason?.takeIf { it.isNotBlank() } ?: "앱에서 확인해 주세요",
    )

    /** `₩6,500 스타벅스`, or just the amount without merchant. */
    private fun amountAndMerchant(payment: DetectedPayment): String =
        listOfNotNull(payment.amount.formatted(), payment.merchant).joinToString(" ")
}
