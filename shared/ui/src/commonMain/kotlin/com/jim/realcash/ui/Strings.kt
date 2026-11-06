package com.jim.realcash.ui

import com.jim.realcash.domain.model.SettlementFlag

/**
 * Korean UI copy in one place (plain Kotlin instead of a resource system; see the module notes).
 * Parameterised texts are functions.
 */
object Strings {
    const val APP_NAME = "RealCash"

    // Navigation
    const val TAB_WALLET = "지갑"
    const val TAB_LEDGER = "가계부"
    const val TAB_SETTINGS = "설정"

    // Wallet home
    const val HOME_MENU = "메뉴"
    const val OPEN_WALLET = "지갑 열기"
    const val REMAINING = "남음"
    const val ADD_ALLOWANCE = "용돈 넣기"
    const val WALLET_OPEN_LAYOUT = "T: wallet-open-layout"
    fun settlementSnackbar(amount: String?): String = if (amount == null) "정산 완료" else "$amount 정산 완료"
    fun pendingBadge(count: Int): String = "확인 $count"
    fun handBadge(amount: String): String = "손에 $amount"
    const val THIS_WEEK = "이번 주"
    const val THIS_MONTH = "이번 달"
    const val BALANCE = "가진 돈"
    const val WALLET = "지갑"
    const val HAND = "손"
    const val IN_HAND_TITLE = "손에 든 돈"
    const val NO_HAND = "꺼낸 돈 없음"
    const val NO_HAND_HINT = "살 만큼만 미리 꺼내 두세요."
    const val TAKE_OUT = "꺼내기"
    const val TAKE_MORE = "더 꺼내기"
    const val EXCHANGE = "환전"
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
    fun splitSum(sum: String): String = "합계 $sum"
    fun splitSum(sum: String, target: String): String = "합계 $sum / $target"
    fun splitDiff(diff: String, over: Boolean): String = if (over) "$diff 많아요" else "$diff 모자라요"
    fun allowanceReceived(amount: String): String = "${amount}을 지갑에 넣었어요"
    fun periodStartNote(label: String): String = "$label 시작 기간의 용돈으로 기록돼요"

    // Manual payment
    const val MANUAL_TITLE = "직접 기록"
    const val MANUAL_AMOUNT = "결제 금액 (원)"
    const val MANUAL_MERCHANT = "가맹점 (선택)"
    const val MANUAL_SETTLE = "정산하기"
    const val MANUAL_SUBMITTING = "정산 중…"
    const val MANUAL_WALLET_HINT = "지갑에서 자동으로 꺼냅니다(큰 권종부터)"
    const val MANUAL_RETRY_HINT = "결제는 기록됐어요. 정산하기를 눌러 다시 시도해 주세요."
    fun manualHandHint(amount: String): String = "손에 든 $amount 로 정산됩니다"

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

    // Take out (꺼내기)
    const val TAKE_OUT_TITLE = "꺼내기"
    const val TAKE_MORE_TITLE = "더 꺼내기"
    const val EXPECTED_AMOUNT = "예상 금액 (선택)"
    const val EXPECTED_AMOUNT_HINT = "얼마쯤 쓸 것 같나요?"
    const val HAND_ZONE_EMPTY = "지폐를 여기로 끌어오거나, 아래 지폐를 눌러 담으세요"
    const val HAND_ZONE_DROP = "여기에 놓기"
    const val HAND_ZONE_TAP_HINT = "칩을 누르면 지갑으로 돌아가요"
    const val PICKED = "담은 돈"
    const val FIT_EXACT = "잔돈으로 딱 맞추기"
    const val FEWEST = "가장 적은 장수로"
    const val HOLD_IN_HAND = "손에 들기"
    const val REFILL_NOTE = "지금 손에 든 돈을 지갑에 넣었다가 다시 꺼내요"
    const val FEEDBACK_EXACT = "딱 맞음"
    fun feedbackShort(amount: String): String = "$amount 부족"
    fun feedbackShortWallet(wallet: String, amount: String): String = "지갑에 $wallet 남았어요 · $amount 부족"
    fun feedbackOver(over: String, change: String): String = "$over 초과 → 거스름돈 예상 $change"
    fun pickedVsExpected(picked: String, expected: String): String = "담은 돈 $picked / 예상 $expected"
    fun fitExactDone(total: String): String = "잔돈으로 $total 딱 맞췄어요"
    fun fitNotExact(total: String): String = "딱 맞는 조합이 없어 $total 을 담았어요"
    fun fewestDone(count: Int, total: String): String = "${count}장으로 $total 을 담았어요"
    fun helperShortfall(shortfall: String): String = "지갑을 다 털어도 $shortfall 부족해요"
    fun heldInHand(amount: String): String = "${amount}을 손에 들었어요"
    fun remainingCount(count: Int): String = "남은 ${count}장"

    // Exchange (환전)
    const val EXCHANGE_TITLE = "환전 창구"
    const val EXCHANGE_STEP_GIVE = "내놓을 돈"
    const val EXCHANGE_STEP_RECEIVE = "받을 돈"
    const val EXCHANGE_GIVE_NOTE = "은행 창구처럼, 바꿀 돈을 먼저 창구에 올려 주세요."
    const val EXCHANGE_RECEIVE_NOTE = "어떻게 받을지 정해 주세요. 합이 맞아야 바꿔 드려요."
    const val EXCHANGE_TRAY_EMPTY = "아래 지갑에서 지폐나 동전을 눌러 올려요"
    const val EXCHANGE_TRAY = "창구에 올린 돈"
    const val EXCHANGE_TO_COUNTER = "창구에 내기"
    const val EXCHANGE_SUGGEST_MINIMAL = "가장 적은 장수로"
    const val EXCHANGE_SUGGEST_SMALLER = "작은 권종으로"
    const val EXCHANGE_DO = "바꾸기"
    const val EXCHANGE_DONE = "환전 완료"
    const val EXCHANGE_SAME = "내놓은 돈과 똑같아요. 다른 조합을 골라 주세요"
    const val EXCHANGE_BACK_TO_GIVE = "내놓을 돈 고치기"
    fun exchangeGiven(total: String): String = "내놓은 돈 $total"

    // Ledger (가계부)
    const val LEDGER_TITLE = "가계부"
    const val LEDGER_SPENT = "쓴 돈"
    const val LEDGER_RECEIVED = "받은 용돈"
    const val LEDGER_REMAINING = "남은 돈"
    const val LEDGER_DIRECT = "직접 꺼낸 결제"
    const val LEDGER_BROKEN = "깨진 큰 지폐"
    const val LEDGER_PLAN_VS_ACTUAL = "꺼낸 돈 vs 실제"
    const val LEDGER_EMPTY = "아직 기록이 없어요. 용돈을 받으면 여기에 쌓여요."
    const val LEDGER_NO_DATA = "—"
    const val LEDGER_REFUND = "환불"
    const val LEDGER_ALLOWANCE = "용돈"
    const val LEDGER_EXCHANGE = "환전"
    const val LEDGER_PAYMENT_UNKNOWN = "결제"
    fun ledgerDirect(direct: Int, total: Int): String = "$direct/$total"
    fun ledgerBroken(count: Int): String = "${count}장"
    fun ledgerPlanVsActual(planned: String, actual: String): String = "$planned → $actual"
    fun ledgerAllowance(amount: String): String = "용돈 $amount"
    fun ledgerHandExpired(amount: String): String = "안 쓴 $amount 지갑으로 복귀"
    fun ledgerChange(change: String): String = "거스름돈 $change"
    fun ledgerPaid(paid: String): String = "낸 돈 $paid"
    fun ledgerToday(label: String): String = "오늘 · $label"

    // Settings (설정)
    const val SETTINGS_TITLE = "설정"
    const val SET_PERIOD = "예산 주기"
    const val SET_WEEKLY = "주간"
    const val SET_MONTHLY = "월간"
    const val SET_START_WEEKDAY = "시작 요일"
    const val SET_START_MONTHDAY = "시작일"
    const val SET_PULL = "자동 차감 방식"
    const val SET_PULL_NOTE = "꺼내지 않고 결제하면 큰 지폐부터 깨지게 해서, 다음엔 미리 꺼내고 싶어지게 만들어요."
    const val SET_PULL_LARGEST = "큰 권종부터 (지폐가 깨져요)"
    const val SET_PULL_FEWEST = "가장 적은 장수로"
    const val SET_PULL_EXACT = "딱 맞게"
    const val SET_TTL = "손에 든 돈 유지 시간"
    const val SET_TTL_NOTE = "이 시간 안에 결제가 없으면 지갑으로 돌아가요."
    const val SET_THRESHOLD = "자동 정산 신뢰도"
    const val SET_THRESHOLD_NOTE = "알림 해석 신뢰도가 이 값 이상이면 묻지 않고 정산해요."
    const val SET_SOURCES = "감지 앱"
    const val SET_SOURCES_NOTE = "이 앱들의 결제 알림만 읽어요."
    const val SET_ADD_PACKAGE = "패키지 추가"
    const val SET_ADD_PACKAGE_HINT = "예: com.example.card"
    const val SET_ADD = "추가"
    const val SET_REMOVE = "빼기"
    const val SET_PACKAGE_INVALID = "패키지 이름 형식이 아니에요"
    const val SET_PACKAGE_DUPLICATE = "이미 목록에 있어요"
    fun ttlLabel(hours: Int): String = "${hours}시간"
    fun monthDayLabel(day: Int): String = "매월 ${day}일"
    fun thresholdLabel(percent: Int): String = "$percent%"

    // Debug
    const val DEBUG_SECTION = "디버그"
    const val DEBUG_SIMULATOR_BODY = "결제 알림을 흉내 내 정산 흐름을 시험해요."

    // Errors
    const val BACK = "뒤로"
    fun failed(detail: String?): String = if (detail.isNullOrBlank()) "처리하지 못했어요" else "처리하지 못했어요 · $detail"
}
