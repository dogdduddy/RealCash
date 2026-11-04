# RealCash 엔지니어링 계획 (Phase 0 ~ 1)

> 작성일: 2026-11-04 · 상태: 확정(기본값). 기획 배경은 [01-realcash-analysis-and-design.md](01-realcash-analysis-and-design.md) 참고.
> 역할: 설계·리뷰·피드백은 리드(Fable 5.1), 구현은 서브에이전트(Opus 5.5, effort high).

---

## 1. 미결 질문 5개에 대한 기본 결정

| # | 질문 | 결정 | 이유 |
|---|---|---|---|
| 1 | 예산 주기 | **주간(weekly)** 기본, 시작 요일 설정 가능. 월간은 데이터 모델만 열어두고 UI는 Phase 3 | 피드백 루프가 짧을수록 "지폐가 줄어드는" 감각이 강하다. 지폐 개수도 UI가 감당할 범위에 머문다 |
| 2 | iOS 최소 버전 | 앱은 **iOS 17+**, 자동 감지는 **iOS 27+** 에서만 활성. 그 아래는 수동/공유시트 모드 | App Intents 안정 버전이 17. 자동 감지만 27 게이트 |
| 3 | 동전 범위 | **8개 권종 전부 개체로** 유지 (50000/10000/5000/1000/500/100/50/10) | 카드 결제는 10원 단위까지 나온다. UI 는 동전을 스택으로 압축 표시해 해결. 모델을 단순하게 유지 |
| 4 | 감지 소스 우선순위 | 기본 **삼성월렛 > 토스 > 카드사 앱**, 2분 창 dedupe. Phase 0 샘플 수집 후 재검토 | 삼성월렛 푸시가 결제 수단과 가장 가깝다. 확정은 실데이터 보고 |
| 5 | 자동 차감 정책 | 기본 **LARGEST_FIRST**(큰 권종 우선, 거스름돈 발생). 설정에서 FEWEST_UNITS 로 변경 가능(Phase 3) | 직접 안 꺼내면 지폐가 깨지게 하는 의도적 마찰 |

추가 결정
- **활성 Hand(손에 든 돈)는 동시에 하나만.** 매칭 로직을 단순하게 유지한다. 여러 개는 봉투 예산(Phase 3)과 함께 검토.
- **잔액은 음수가 되지 않는다.** 지갑이 부족하면 `Settlement.shortfall` 에 부족분을 기록하고 지갑은 비워진다. (HardPay 는 음수 허용이었음)
- **원장은 append-only 이벤트.** 지갑 상태는 이벤트 fold 로 파생. 이유: 취소/환불 역정산, "어떤 지폐가 나갔는지" 애니메이션, 마감 리뷰 통계가 전부 이벤트에서 나온다. 저장은 이벤트 테이블 + (선택) 스냅샷.

---

## 2. 환경 제약 (2026-11-04 기준 클라우드 세션)

- JDK 21, Gradle 8.14.3 (`/opt/gradle/bin/gradle`), Kotlin 컴파일러는 Gradle 플러그인으로 받음.
- **Android SDK 없음.** `dl.google.com` 이 네트워크 정책에서 차단되어 AGP·androidx·SDK 를 받을 수 없다. → Android 모듈은 사용자가 환경 설정 **Allowed domains** 에 `dl.google.com` 을 추가한 뒤 진행.
- Maven Central 은 간헐적으로 429 를 돌려준다. Gradle 은 재시도하므로 실패 시 한 번 더 실행.
- iOS 타깃은 Linux 호스트에서 컴파일 불가(Kotlin/Native 가 경고 후 스킵). Swift 코드는 사용자/동료가 Xcode 에서 검증.
- 따라서 **이 세션에서 실제로 검증되는 것은 `shared/domain` 의 JVM 타깃 테스트** 다. 도메인 로직을 여기에 최대한 몰아넣는다.

---

## 3. 저장소 구조

```
RealCash/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── gradle/wrapper/                 # 8.14.3
├── shared/
│   ├── domain/                     # 순수 Kotlin (KMP: jvm, iosArm64, iosSimulatorArm64; android 는 SDK 확보 후 추가)
│   ├── data/                       # SQLDelight 이벤트 저장, 설정, 파서 규칙 로더      (T4)
│   └── ui/                         # Compose Multiplatform 화면                        (T5)
├── androidApp/                     # NotificationListenerService, 위젯, 진입점         (T3, SDK 필요)
├── iosApp/
│   └── NotificationIntentPoC/      # iOS 27 알림 트리거 → App Intent PoC (Swift)        (T2)
└── docs/
```

패키지 루트: `com.jim.realcash` (HardPay 의 `com.jim.hardpay` 관례 유지).

### 버전 정책
- 버전은 **추측하지 말고** Maven Central / plugins.gradle.org 의 `maven-metadata.xml` 로 존재를 확인한 뒤 적는다.
- Kotlin 2.2.x 최신 안정, kotlinx-coroutines 1.10.x, kotlinx-datetime 0.7.x(`kotlin.time.Instant` 사용), kotlinx-serialization 1.9.x, kotlin-test.
- SQLDelight 2.4.x (T4), Compose Multiplatform 1.9.x (T5). Gradle 8.14.3 과 호환되는 조합인지 확인.

---

## 4. `shared/domain` 스펙 (T1)

### 4.1 원칙
- 플랫폼 의존성 0. `kotlin-stdlib`, `kotlinx-datetime`, `kotlinx-serialization`(이벤트 직렬화용) 만.
- 모든 계산은 순수 함수. 시간·ID 는 주입(`Clock`, `IdGenerator`).
- 돈은 `@JvmInline value class Won(val value: Int)` (원 단위 정수, 음수 금지는 생성 시 검증하지 않고 연산 결과에서 보장).

### 4.2 타입

```kotlin
package com.jim.realcash.domain.model

enum class Denomination(val won: Int, val kind: Kind) {
    W50000(50_000, BILL), W10000(10_000, BILL), W5000(5_000, BILL), W1000(1_000, BILL),
    C500(500, COIN), C100(100, COIN), C50(50, COIN), C10(10, COIN);
    enum class Kind { BILL, COIN }
    companion object { val descending: List<Denomination>; fun of(won: Int): Denomination? }
}

@JvmInline value class CashUnitId(val value: String)
@JvmInline value class HandId(val value: String)
@JvmInline value class PaymentId(val value: String)

/** 지폐 또는 동전 1개. 개체이며 자동 재편성되지 않는다. */
data class CashUnit(
    val id: CashUnitId,
    val denomination: Denomination,
    val state: CashUnitState,          // IN_WALLET | IN_HAND | SPENT | EXCHANGED_AWAY
    val acquiredAt: Instant,
    val source: CashUnitSource,        // ALLOWANCE | CHANGE | EXCHANGE | ADJUSTMENT | REFUND
)

data class Hand(
    val id: HandId,
    val unitIds: List<CashUnitId>,
    val createdAt: Instant,
    val expiresAt: Instant,            // 기본 createdAt + 6h
    val expectedAmount: Won?,
    val memo: String?,
)

data class DetectedPayment(
    val id: PaymentId,
    val amount: Won,
    val merchant: String?,
    val sourceApp: String,             // 패키지명 또는 iOS 앱 식별자
    val rawText: String,               // 재파싱용. 기기 밖으로 나가지 않음
    val detectedAt: Instant,
    val confidence: Float,             // 0..1
    val isRefund: Boolean,
)

data class Settlement(
    val paymentId: PaymentId,
    val handId: HandId?,
    val paidUnitIds: List<CashUnitId>,
    val changeUnits: List<CashUnit>,   // 새로 생성된 거스름돈 (source = CHANGE)
    val shortfall: Won,                // 지갑으로 못 덮은 금액. 보통 0
    val flags: Set<SettlementFlag>,    // NOT_HAND_PICKED | ADDED_FROM_WALLET | INSUFFICIENT_FUNDS | MANUAL
)

enum class PullPolicy { LARGEST_FIRST, FEWEST_UNITS, EXACT_IF_POSSIBLE }

data class BudgetPeriodPolicy(val cycle: Cycle, val startDay: Int /* 월=1..일=7 또는 1..28 */) {
    enum class Cycle { WEEKLY, MONTHLY }
}
```

### 4.3 이벤트와 리듀서

```kotlin
package com.jim.realcash.domain.ledger

sealed interface LedgerEvent { val id: String; val at: Instant }
data class AllowanceReceived(…, val units: List<CashUnit>, val periodStart: LocalDate)
data class MovedToHand(…, val hand: Hand)
data class HandReturned(…, val handId: HandId, val reason: ReturnReason /* EXPIRED | USER | SETTLED */)
data class PaymentDetected(…, val payment: DetectedPayment)
data class PaymentRejected(…, val paymentId: PaymentId)             // 사용자가 "아니에요"
data class Settled(…, val settlement: Settlement)
data class Refunded(…, val paymentId: PaymentId, val units: List<CashUnit>)  // 환불 → 거스름돈 규칙으로 지갑 복귀
data class Exchanged(…, val fromUnitIds: List<CashUnitId>, val toUnits: List<CashUnit>)
data class Adjusted(…, val removedUnitIds: List<CashUnitId>, val addedUnits: List<CashUnit>, val reason: String)

data class WalletState(
    val units: Map<CashUnitId, CashUnit>,
    val activeHand: Hand?,
    val pendingPayments: Map<PaymentId, DetectedPayment>,   // NEEDS_CONFIRM 상태
    val settlements: List<Settlement>,
) {
    val inWallet: List<CashUnit>; val inHand: List<CashUnit>
    val balance: Won                                        // inWallet + inHand 합
    fun countsByDenomination(state: CashUnitState): Map<Denomination, Int>
}

object WalletReducer { fun reduce(state: WalletState, event: LedgerEvent): WalletState }   // 불변식 위반 시 예외
fun List<LedgerEvent>.fold(): WalletState
```

**불변식 (테스트로 고정)**
- 한 `CashUnit` 은 정확히 하나의 상태. `IN_HAND` 인 유닛은 모두 `activeHand.unitIds` 에 있고, 그 역도 성립.
- `balance == sum(inWallet) + sum(inHand)`.
- `Exchanged` 는 `sum(from) == sum(to)`.
- `Settled` 후 paidUnits 는 전부 `SPENT`, changeUnits 는 `IN_WALLET`, hand 는 해제.
- 이벤트는 멱등 적용이 아니라 순서 의존. 같은 이벤트 두 번 적용 시 예외.

### 4.4 계산기 (순수 함수)

```kotlin
package com.jim.realcash.domain.calc

object ChangeCalculator {
    /** 원화 권종은 canonical 이므로 greedy 가 최소 개수. amount 는 10원 단위여야 함(아니면 IllegalArgumentException). */
    fun minimalChange(amount: Won): Map<Denomination, Int>
}

object SplitSuggester {
    /** 용돈 받기 화면의 기본 제안. LARGEST_FIRST = ATM 식, BALANCED = 1만원권 비중 높임. 합이 amount 와 같아야 함 */
    fun suggest(amount: Won, style: SplitStyle): Map<Denomination, Int>
    fun validate(amount: Won, split: Map<Denomination, Int>): Boolean
}

object PullSelector {
    /**
     * available 에서 amount 이상을 덮는 유닛 조합을 고른다.
     * EXACT_IF_POSSIBLE: 정확히 amount 가 되는 부분집합이 있으면 그것(bounded DP, amount ≤ 5,000,000원 아니면 greedy 폴백), 없으면 LARGEST_FIRST.
     * LARGEST_FIRST: (1) 단독으로 amount 를 덮는 유닛이 있으면 그중 '가장 작은' 권종 1개 (2) 없으면 큰 권종부터 누적해 amount 이상이 되는 순간 멈춤.
     * FEWEST_UNITS: 개수 최소, 동률이면 초과액 최소.
     * 덮을 수 없으면 전부 선택하고 shortfall 반환.
     */
    fun select(available: List<CashUnit>, amount: Won, policy: PullPolicy): PullResult
    data class PullResult(val unitIds: List<CashUnitId>, val total: Won, val shortfall: Won)
}

object Settler {
    /**
     * 섹션 4.3 매칭 규칙 그대로:
     *  hand == amount → paid = hand
     *  hand >  amount → paid = hand, change = minimalChange(diff)
     *  hand <  amount → paid = hand + PullSelector(inWallet, diff, LARGEST_FIRST), change, flag ADDED_FROM_WALLET
     *  hand 없음     → PullSelector(inWallet, amount, policy), change, flag NOT_HAND_PICKED
     *  지갑 부족     → shortfall > 0, flag INSUFFICIENT_FUNDS
     * 환불(isRefund) 은 여기서 처리하지 않고 Refunded 이벤트로 별도.
     */
    fun settle(state: WalletState, payment: DetectedPayment, policy: PullPolicy, now: Instant, ids: IdGenerator): Settlement
}

object PaymentDeduplicator {
    /** 같은 금액 + |Δt| ≤ window(기본 120s) + (가맹점 둘 중 하나 null 이거나 정규화 후 동일) 이면 중복 */
    fun isDuplicate(candidate: DetectedPayment, recent: List<DetectedPayment>, window: Duration = 120.seconds): Boolean
}

object HandExpiry { fun isExpired(hand: Hand, now: Instant): Boolean }

object BudgetPeriods {
    fun currentPeriod(policy: BudgetPeriodPolicy, today: LocalDate): ClosedRange<LocalDate>
}
```

### 4.5 알림 파서

```kotlin
package com.jim.realcash.domain.parser

data class RawNotification(val sourceApp: String, val title: String?, val text: String?, val bigText: String?, val postedAt: Instant)

@Serializable
data class ParserRule(
    val id: String,
    val sourceApps: List<String>,          // 패키지명 / iOS 식별자
    val amountRegex: String,               // 첫 캡처 그룹 = 숫자(쉼표 포함 가능)
    val merchantRegex: String? = null,
    val refundKeywords: List<String> = listOf("취소", "승인취소", "환불"),
    val ignoreKeywords: List<String> = emptyList(),   // "충전", "이체" 등 결제 아닌 알림
    val baseConfidence: Float,
)

interface NotificationParser { fun parse(n: RawNotification): DetectedPayment? }
class RuleBasedParser(rules: List<ParserRule>, ids: IdGenerator) : NotificationParser
object DefaultRules { val rules: List<ParserRule> }   // ⚠ Phase 0 샘플 수집 전까지는 '추정 포맷' 플레이스홀더. 각 규칙에 TODO 주석 필수.
```

신뢰도: 금액 추출 +0.6, 가맹점 추출 +0.2, 알려진 sourceApp +0.2. `ignoreKeywords` 매치 시 null. 금액은 "1,234원" / "1234원" / "₩1,234" 모두 지원. 자동 정산 임계치는 호출자 설정(기본 0.8).

### 4.6 테스트 (jvmTest, kotlin-test)
- `ChangeCalculator`: 0원, 10원, 9,990원, 123,450원, 10원 단위 아닌 값 예외, 결과 합 == 입력(랜덤 200개).
- `SplitSuggester`: 합 불변, validate 거짓 케이스.
- `PullSelector`: 문서 예시 — 지갑 {50000×1,10000×2,1000×3,500×2}, 6,500원 → LARGEST_FIRST 는 10000 한 장; EXACT_IF_POSSIBLE 은 {5000? 없음} → 1000×3 + 500×2 = 4000 부족 → 1000×3+500×2 가 아닌 정확 조합 불가 → LARGEST_FIRST 폴백. 지갑 {1000×7}, 6,500원 → 7장, 거스름돈 500. 부족 케이스 shortfall.
- `Settler`: 다섯 분기 각각 + 불변식(결과 적용 후 balance 변화 == -(amount - shortfall)).
- `WalletReducer`: 전체 시나리오(용돈 → 손에 들기 → 결제 → 거스름돈 → 환전 → 환불) fold 후 불변식, 중복 이벤트 예외, 다른 Hand 활성 중 MovedToHand 예외.
- `PaymentDeduplicator`: 같은 금액 90초 차 중복, 121초 비중복, 가맹점 다르면 비중복.
- `RuleBasedParser`: 규칙별 샘플 문자열 테이블 테스트(플레이스홀더 포맷), ignore/refund 분기.
- `BudgetPeriods`: 주간 시작 월요일, 월간 시작 25일(월말 넘김) 경계.

### 4.7 완료 기준 (DoD)
- `gradle :shared:domain:jvmTest` 통과, 경고 0 목표(실험 API OptIn 은 모듈 단위로 허용).
- `gradle build` 가 Linux 에서 성공(iOS 타깃 스킵 경고는 허용).
- 공개 API 전부 KDoc 한 줄 이상. 한국어 또는 영어 일관.
- 스펙과 다르게 구현한 지점은 보고서에 명시(리드가 판단).

---

## 5. 작업 분해와 순서

| ID | 작업 | 담당 | 선행 | 이 세션에서 검증 |
|---|---|---|---|---|
| T0 | 엔지니어링 계획(이 문서) | 리드 | - | - |
| T1 | Gradle KMP 스켈레톤 + `shared/domain` + 테스트 | Opus | T0 | ✅ jvmTest |
| T2 | iOS 27 알림 트리거 → App Intent PoC (Swift) | Opus | T0 | ✗ (Xcode 필요) |
| T3 | `androidApp` + 알림 수집기(디버그) + 알림 접근 온보딩 | Opus | T1, `dl.google.com` 허용 | SDK 확보 시 ✅ |
| T4 | `shared/data` SQLDelight 이벤트 저장 + 파서 규칙 로더 | Opus | T1 | ✅ jvmTest |
| T5 | `shared/ui` CMP 화면(지갑 홈·용돈·꺼내기·정산) | Opus | T1, T4 | 데스크톱 타깃으로 부분 ✅ |
| T6 | 리뷰·피드백·커밋·푸시 (각 T 완료마다) | 리드 | - | - |

### 작업 규칙 (서브에이전트 공통)
- 자기 작업 범위 밖의 파일은 수정하지 않는다. `docs/`, `README.md` 는 리드 전용.
- **커밋·푸시 하지 않는다.** 작업 트리에 변경을 남기면 리드가 리뷰 후 커밋한다.
- 빌드 산출물(`build/`, `.gradle/`, `.kotlin/`, `*.iml`, `.idea/`, `local.properties`, Xcode `DerivedData`, `xcuserdata`) 은 `.gitignore` 에.
- 모델명·세션명을 코드/주석에 넣지 않는다.
- 보고서에는 (1) 생성·수정 파일 목록 (2) 스펙과 달리 결정한 것과 이유 (3) 실행한 검증 명령과 결과 (4) 남은 리스크를 쓴다.
