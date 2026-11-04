package com.jim.realcash.android.debug

import com.jim.realcash.android.BuildConfig
import com.jim.realcash.ui.DebugEntry

/**
 * Android-only screens listed in the shared 설정 → 디버그 section; debug builds only (release builds get none).
 * System back on these screens is handled by the shared RealCashRoot like on every other pushed screen.
 *
 * TODO(onboarding): the notification-access status and the settings button (NotificationAccessScreen) must be
 *   reachable in release builds through the onboarding / settings flow; only the debug list is gated here.
 */
fun androidDebugEntries(debug: Boolean = BuildConfig.DEBUG): List<DebugEntry> = if (!debug) emptyList() else listOf(
    DebugEntry(
        title = DebugStrings.ACCESS_TITLE,
        description = DebugStrings.ACCESS_ENTRY_BODY,
    ) { onBack -> NotificationAccessScreen(onBack) },
    DebugEntry(
        title = DebugStrings.SAMPLES_TITLE,
        description = DebugStrings.SAMPLES_ENTRY_BODY,
    ) { onBack -> SampleCollectorScreen(onBack) },
)

/** Korean copy of the Android debug screens. */
object DebugStrings {
    const val ACCESS_TITLE = "알림 접근"
    const val ACCESS_ENTRY_BODY = "결제 알림 읽기 권한과 정산 알림 표시 상태를 확인해요."

    /** Prominent disclosure (Play policy) shown before sending the user to the special-access settings. */
    const val ACCESS_DISCLOSURE =
        "RealCash는 삼성월렛·토스 같은 결제 앱이 보내는 결제 알림을 읽어 금액과 가맹점을 알아내고, " +
            "손에 든 돈이나 지갑에서 자동으로 정산해요. 그래서 ‘알림 접근’ 권한이 필요해요. " +
            "설정에서 고른 결제 앱의 알림만 처리하고, 알림 내용은 이 기기 안에서만 처리·저장돼요. " +
            "서버로 보내지 않으며 이 앱에는 인터넷 권한도 없어요. 권한은 언제든 설정에서 끌 수 있어요."
    const val ACCESS_LISTENER_LABEL = "알림 접근"
    const val ACCESS_ON = "켜짐"
    const val ACCESS_OFF = "꺼짐"
    const val ACCESS_CONNECTED = "연결됨"
    const val ACCESS_NOT_CONNECTED = "아직 연결되지 않음"
    const val ACCESS_OPEN_SETTINGS = "알림 접근 설정 열기"
    const val ACCESS_REBIND = "연결 다시 요청"
    const val ACCESS_POST_LABEL = "정산 알림 표시"
    const val ACCESS_POST_ALLOWED = "허용됨"
    const val ACCESS_POST_BLOCKED = "꺼짐"
    const val ACCESS_POST_REQUEST = "알림 허용"
    const val ACCESS_POST_SETTINGS = "앱 알림 설정 열기"
    const val ACCESS_POST_BODY = "정산 결과와 ‘맞아요 / 아니에요’ 확인 요청을 알림으로 보여 주려면 알림 표시를 허용해 주세요."
    const val ACCESS_SOURCES_LABEL = "정산 처리하는 앱"
    const val ACCESS_SOURCES_NONE = "없음"

    const val SAMPLES_TITLE = "알림 샘플 수집"
    const val SAMPLES_ENTRY_BODY = "결제 앱 알림 원문을 기기에 모아 파서 규칙을 만들어요."
    const val SAMPLES_PRIVACY =
        "수집한 알림 원문은 이 기기의 앱 저장소에만 저장돼요. 카드번호 일부·가맹점·금액이 들어 있을 수 있으니 " +
            "내보내거나 공유하기 전에 카드번호 같은 민감한 부분을 꼭 가려 주세요."
    const val SAMPLES_TOGGLE = "샘플 수집"
    const val SAMPLES_TOGGLE_BODY = "아래 목록에 있는 앱의 알림이 올 때마다 원문과 파싱 결과를 저장해요. 알림 접근 권한이 필요해요."
    const val SAMPLES_PACKAGES = "수집할 앱"
    const val SAMPLES_PACKAGE_HINT = "패키지 이름 (예: com.example.card)"
    const val SAMPLES_ADD = "추가"
    const val SAMPLES_REMOVE = "삭제"
    const val SAMPLES_RESET = "기본 목록으로"
    const val SAMPLES_INVALID = "패키지 이름 형식이 아니거나 이미 있어요."
    const val SAMPLES_EXPORT = "내보내기"
    const val SAMPLES_COPY = "JSON 복사"
    const val SAMPLES_CLEAR = "모두 삭제"
    const val SAMPLES_CLEAR_CONFIRM_TITLE = "샘플을 모두 삭제할까요?"
    const val SAMPLES_CLEAR_CONFIRM_BODY = "저장된 알림 샘플이 기기에서 지워져요. 되돌릴 수 없어요."
    const val SAMPLES_CANCEL = "취소"
    const val SAMPLES_NOTHING = "저장된 샘플이 없어요."
    const val SAMPLES_CLEARED = "샘플을 모두 삭제했어요."
    const val SAMPLES_NOT_PARSED = "파싱 안 됨"
    const val SAMPLES_REFUND = "취소/환불"

    fun samplesRecent(shown: Int, total: Int) = "최근 샘플 (${total}건 중 ${shown}건)"

    fun copied(count: Int) = "샘플 ${count}건을 JSON으로 복사했어요."
}
