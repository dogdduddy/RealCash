package com.jim.realcash.ui

import com.jim.realcash.domain.model.SettlementFlag

/**
 * Korean UI copy in one place (plain Kotlin instead of a resource system; see the module notes).
 * Parameterised texts are functions.
 */
object Strings {
    const val APP_NAME = "RealCash"

    // Tabs
    const val TAB_WALLET = "지갑"
    const val TAB_LEDGER = "가계부"
    const val TAB_SETTINGS = "설정"

    // Wallet home
    const val THIS_WEEK = "이번 주"
    const val THIS_MONTH = "이번 달"
    const val BALANCE = "가진 돈"
    const val WALLET = "지갑"
    const val HAND = "손"
    const val IN_HAND_TITLE = "손에 든 돈"
    const val NO_HAND = "꺼낸 돈 없음 · T5b 에서 꺼내기 추가"
    const val RETURN_HAND = "지갑에 다시 넣기"
    const val EXPECTED = "예상"
    const val WALLET_EMPTY_TITLE = "지갑이 비어 있어요"
    const val WALLET_EMPTY_BODY = "이번 기간 용돈을 받아 지폐를 채워 보세요."
    const val RECEIVE_ALLOWANCE = "용돈 받기"
    const val COINS = "동전"
    const val BILLS = "지폐"
    const val SIMULATOR_SHORTCUT = "알림 시뮬레이터"
    const val DISMISS = "닫기"
    fun daysLeft(days: Int): String = if (days <= 0) "오늘 마감" else "${days}일 남음"
    fun pendingBanner(count: Int): String = "확인 필요한 결제 ${count}건 →"
    fun settledCard(merchant: String?, amount: String?, change: String?): String = buildString {
        append("정산 완료")
        if (merchant != null || amount != null) append(" · ")
        if (merchant != null) append(merchant)
        if (merchant != null && amount != null) append(' ')
        if (amount != null) append(amount)
        if (change != null) append(" · 거스름돈 ").append(change)
    }
    fun handReturned(amount: String): String = "${amount}을 지갑에 다시 넣었어요"

    // Receive allowance
    const val ALLOWANCE_TITLE = "용돈 받기"
    const val ALLOWANCE_AMOUNT = "받을 금액"
    const val ALLOWANCE_AMOUNT_HINT = "숫자만 입력"
    const val STYLE_ATM = "ATM처럼"
    const val STYLE_BALANCED = "1만원권 많이"
    const val SPLIT_TITLE = "권종 조합"
    const val PUT_IN_WALLET = "지갑에 넣기"
    const val AMOUNT_NOT_CASH = "10원 단위로 입력해 주세요"
    fun splitSum(sum: String, target: String): String = "합계 $sum / $target"
    fun splitDiff(diff: String, over: Boolean): String = if (over) "$diff 많아요" else "$diff 모자라요"
    fun allowanceReceived(amount: String): String = "${amount}을 지갑에 넣었어요"
    fun periodStartNote(label: String): String = "$label 시작 기간의 용돈으로 기록돼요"

    // Confirm payments
    const val CONFIRM_TITLE = "확인 필요한 결제"
    const val CONFIRM_EMPTY = "확인할 결제가 없어요"
    const val UNKNOWN_MERCHANT = "가맹점 알 수 없음"
    const val YES = "맞아요"
    const val NO = "아니에요"
    const val REJECTED = "결제가 아닌 것으로 처리했어요"
    fun confidence(percent: String): String = "신뢰도 $percent"
    fun changeLine(change: String): String = "거스름돈 $change"
    fun shortfallLine(shortfall: String): String = "부족 $shortfall"

    /** Badge text of a settlement flag. */
    fun flagLabel(flag: SettlementFlag): String = when (flag) {
        SettlementFlag.NOT_HAND_PICKED -> "직접 꺼내지 않은 결제"
        SettlementFlag.ADDED_FROM_WALLET -> "지갑에서 추가로 꺼냄"
        SettlementFlag.INSUFFICIENT_FUNDS -> "부족"
        SettlementFlag.MANUAL -> "수동 확인"
    }

    // Simulator
    const val SIMULATOR_TITLE = "알림 시뮬레이터"
    const val SIM_SOURCE = "보낸 앱"
    const val SIM_SOURCE_SAMSUNG = "삼성월렛"
    const val SIM_SOURCE_TOSS = "토스"
    const val SIM_SOURCE_CUSTOM = "직접 입력"
    const val SIM_PACKAGE = "패키지 이름"
    const val SIM_TITLE = "제목"
    const val SIM_TEXT = "본문"
    const val SIM_TIME = "시각"
    const val SIM_TIME_NOW = "지금 (보내는 순간)"
    const val SIM_SEND = "결제 알림 보내기"
    const val SIM_PRESETS = "프리셋"
    const val SIM_PRESET_SAMSUNG = "삼성 6,500원"
    const val SIM_PRESET_TOSS = "토스 12,000원"
    const val SIM_PRESET_CANCEL = "삼성 취소"
    const val SIM_RESULT = "결과"

    // Placeholders / settings
    const val LEDGER_TITLE = "가계부"
    const val SETTINGS_TITLE = "설정"
    const val PLACEHOLDER_NOTE = "T5b 에서 채워질 화면이에요."
    const val DEBUG_SECTION = "디버그"
    const val DEBUG_SIMULATOR_BODY = "결제 알림을 흉내 내 정산 흐름을 시험해요."

    // Errors
    const val BACK = "뒤로"
    fun failed(detail: String?): String = if (detail.isNullOrBlank()) "처리하지 못했어요" else "처리하지 못했어요 · $detail"
}
