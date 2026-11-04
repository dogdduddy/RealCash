# RealCash — HardPay 회고 분석 및 설계 제안 (v0.1)

> 작성일: 2026-11-04
> 상태: 제안 단계. 코드 작성 전. 이 문서의 결정 사항에 합의한 뒤 Phase 0 스파이크로 넘어간다.

---

## 0. 한 줄 요약

- **HardPay** 는 "결제 **직전** 에 가상 현금을 꺼내는 앱" 이었다.
- **RealCash** 는 "현금 지갑을 **들고 다니고**, 결제는 **자동으로 감지** 되는 앱" 으로 바꾼다.
- 핵심 전환은 개입 시점을 **결제 순간 → 결제 전(계획) + 결제 후(자동 정산)** 으로 옮기는 것이다. 카운터 앞에서는 앱을 열지 않는다.
- 세 가지 피드백(눈치, 몰입도, 복귀 귀찮음)은 전부 이 전환 하나로 구조적으로 풀린다. 나머지는 자산 퀄리티와 플랫폼 연동 문제다.

---

## 1. HardPay 분석: 코드로 읽은 기획 의도와 현재 상태

### 1.1 코드에 박혀 있던 기획 의도

HardPay 코드(5커밋, 2026-06-19 하루 작업)를 보면 의도가 꽤 명확하게 구조에 반영돼 있다.

| 설계 결정 | 코드 위치 | 의도 해석 |
|---|---|---|
| 잔액을 저장하지 않고 원장 합산으로 파생 | `LedgerDao.observeBalance()` | 단일 진실(single source of truth). 잔액 불일치 버그를 구조적으로 차단 |
| 그릇(`BowlSelection`)을 입력 방식과 분리된 순수 모델로 | `domain/BowlSelection.kt` | 버튼 탭 → 드래그앤드랍으로 바뀌어도 도메인은 그대로. 처음부터 인터랙션 교체를 염두 |
| `PendingPayment` 를 DataStore 에 영속화 | `PendingPaymentStore` | 삼성페이 갔다가 앱이 죽어도 "썼냐?" 팝업 보장 |
| 꺼낸 금액(`selectedAmount`)과 실제 결제액(`amount`)을 **따로** 저장 | `LedgerEntry` | "현금 감각"과 "실제"의 괴리를 데이터로 남기려는 의도. 이건 좋은 결정이고 RealCash 에도 계승 |
| 삼성페이는 SDK 없이 런치 인텐트만 | `SamsungPayLauncher.kt` | 결제 결과 콜백 없음을 인지하고 `onResume` 으로 우회. 주석에 "NOT a Samsung Pay callback" 이라고 두 번 강조 |
| 드래그 고스트를 `graphicsLayer` 지연 읽기로 | `DenomGhost` | 매 프레임 리컴포즈 회피. 인터랙션 성능에 신경 쓴 흔적 |

즉 HardPay 의 핵심 가설은 **"디지털 결제 앞에 현금 결제의 인지 마찰(cognitive friction)을 끼워넣으면 지출이 준다"** 였고, 구조는 **원장 / 그릇 / 결제확인** 세 계층이었다.

### 1.2 구현 수준 (무엇이 진짜고 무엇이 임시였나)

**완성된 것**
- Room 원장 + 잔액 파생, 가계부 수정
- 그릇 모델, pending 영속화, 확인 다이얼로그, 미확정 배너
- 드래그앤드랍 + 고스트 + 드롭존 하이라이트 + 햅틱

**반쪽인 것**
- 지폐/동전 UI: `BillShape`/`CoinShape` 는 그라데이션 박스 + 숫자 텍스트. 마지막 커밋 "feat: 지폐 UI 추가" 가 여기서 멈춤.

**가짜/임시였던 것 (이게 중요)**
- `supplyMap = DENOMINATIONS.associateWith { 10 }` — 팔레트의 권종 재고는 **항상 10장씩 고정**, `remember` 로만 유지되어 앱 재시작 시 리셋. 실제 보유 권종이 아니다.
- 잔액 분해 `breakdown(balance)` (greedy) 와 팔레트 재고가 **서로 무관**. 잔액 3만원이어도 5만원권을 꺼낼 수 있고, 결제 확정 시 잔액은 음수로 내려간다(빨간색 표시).
- 탭으로 담으면 재고가 안 줄고 드래그로 담으면 줄어든다 (`onDenomSelected` vs `onDragEnd` 비대칭). 재고 개념이 아직 설계되지 않았다는 증거.

**없는 것**
- 결제 감지, 권종 보유 상태 영속화, 거스름돈 개념, 환전(5만원→1만원×5), 온보딩, 테스트

### 1.3 피드백 3가지를 코드 관점에서 재해석

> 1. 결제 당시에 켜면 알바생이나 뒷사람 눈치가 보여서 쓰기 꺼려진다.

- 앱 구조상 `startPayment()` 가 삼성페이를 띄우는 **유일한 진입점** 이고, 그 직전에 드래그앤드랍을 해야 한다. 즉 앱이 사용자에게 개입을 요구하는 타이밍이 **카운터 앞, 결제 직전 한 곳** 뿐이다.
- 이건 UI 문제가 아니라 **개입 시점(timing) 문제** 다. 드래그를 아무리 빠르게 만들어도 줄 서서 하는 행위 자체가 어색하다.

> 2. 지폐나 동전 퀄리티가 낮아서 몰입도가 떨어진다.

- 자산 퀄리티는 맞다. 그런데 더 큰 원인이 하나 있다: `supplyMap` 이 매번 10장으로 리셋되니 사용자가 보는 건 **"내 지갑"이 아니라 "무한 ATM"** 이다. 어제 꺼낸 5만원권이 오늘 다시 그 자리에 있으면 소유감이 생길 수 없다.
- 몰입은 **그림 퀄리티 × 개체의 영속성(identity)** 이다. 둘 다 필요하다. 사용자가 말한 "처음 받은 돈에서 쓰지 않은 것들이 그대로 유지되길 원한다" 는 요구가 정확히 이 지점이다.

> 3. 계산 후 앱에 다시 접속해서 버튼 누르는 게 귀찮다.

- `onResume` 의존 + `ConfirmPaymentDialog` 에서 실제 금액을 **손으로 입력**. 결제 결과를 앱이 알 방법이 없어서 생긴 구조다.
- 부수 문제: `startPayment()` 는 pending 이 남아있으면 새 결제를 막는다. 한 번 확인을 미루면 다음 결제가 막히는 UX 함정이 코드에 있다. 자동 감지로 대체하면 이 함정도 함께 사라진다.

### 1.4 HardPay 에서 가져갈 것 / 버릴 것

| 계승 | 폐기 |
|---|---|
| 원장 파생 잔액 (단일 진실) | 결제 직전 개입 플로우 |
| 그릇(=손에 들기) 순수 모델 | 10장 고정 무한 재고 |
| 꺼낸 금액 vs 실제 금액 분리 기록 | 수동 금액 입력 확인 다이얼로그 (폴백으로만 유지) |
| 드래그앤드랍 + 햅틱 인터랙션 패턴 | 그라데이션 박스 지폐 |
| pending 영속화 사고방식 | 삼성페이 런치 인텐트 (결제는 사용자가 평소대로 함) |

---

## 2. 핵심 통찰: "현금 효과" 의 진짜 메커니즘

행동경제학에서 현금이 카드보다 지출을 억제하는 이유(pain of paying, Prelec & Loewenstein 1998; Raghubir & Srivastava 2008)를 분해하면 네 가지다.

| 메커니즘 | 설명 | HardPay | RealCash |
|---|---|---|---|
| **(a) 유한한 물리적 재고** | 지갑에 있는 만큼만 쓸 수 있다. 큰 지폐가 깨지면 눈에 보이게 줄어든다 | ✗ (무한 재고) | ✅ 지폐 개체 영속화 |
| **(b) 꺼내는 행위의 고통** | 지폐를 세고 건네는 순간의 손실 체감 | ✅ (유일하게 구현) | ✅ 시점을 "계획 순간"으로 이동 |
| **(c) 거슬러 받는 피드백** | 5만원 내고 잔돈 받으면 "5만원이 깨졌다"는 감각 | ✗ | ✅ 거스름돈 자동 계산 |
| **(d) 지갑을 열 때마다 보이는 잔액** | 수동적 노출. 의식하지 않아도 남은 돈이 눈에 들어옴 | △ (숫자만) | ✅ 지갑 홈 + 위젯 |

HardPay 는 (b) 하나만 구현했고, 그마저도 가장 사회적 비용이 큰 타이밍에 배치했다. RealCash 는 (a)(c)(d) 를 추가하고 (b) 를 결제 전 계획 시점으로 옮긴다.

---

## 3. RealCash 제품 컨셉

### 3.1 컨셉 문장

> **"카드로 내지만, 지갑은 현금처럼."**
> 결제 수단은 그대로 둔다. 바뀌는 건 돈을 **보고, 꺼내고, 거슬러 받는 감각** 이다.

### 3.2 설계 원칙 3가지

1. **결제 순간에 앱을 열지 않는다 (Zero-touch at checkout)**
   카운터 앞에서 사용자가 해야 할 일은 평소처럼 삼성페이/애플페이/카드를 내는 것뿐이다.
2. **지폐는 개체다 (Bills are objects)**
   각 지폐는 ID 를 가진 영속 개체. 자동으로 최적화·재분배되지 않는다. 5만원권이 1만원권 5장으로 바뀌는 건 사용자가 직접 환전할 때만이다.
3. **정산은 자동, 결정은 수동 (Auto-detect, manual decide)**
   결제 감지와 거스름돈 계산은 앱이 한다. 어떤 지폐를 꺼낼지, 환전할지, 남은 돈을 이월할지는 사용자가 한다.

### 3.3 새 사용자 플로우

```
[A] 용돈 받기 ──▶ [B] 꺼내기(계획) ──▶ [C] 결제(평소대로) ──▶ [D] 자동 감지 ──▶ [E] 거스름돈 ──▶ [G] 주간 마감
                        │                                           │
                        └── 안 꺼내고 결제 ────────────────────────┘ (자동 차감, "직접 꺼내지 않은 결제" 표시)
                                                [F] 환전 (언제든, 사용자 명시)
```

**[A] 용돈 받기 (Allowance)**
- 기간(주/월)과 예산을 정하면 **권종 조합을 사용자가 고른다.** ATM 처럼: "5만원권 2장 + 1만원권 5장 + 5천원권 2장".
- 이 조합이 그대로 지갑에 들어간다. 이후 앱은 이 지폐들을 절대 자동 재편성하지 않는다.
- 이월 정책: 기간 종료 시 남은 지폐는 (1) 다음 기간으로 그대로 이월 (2) 저금통으로 이동 — 사용자가 선택.

**[B] 꺼내기 (Hand / 손에 들기) — HardPay 그릇의 진화형**
- 시점: **집에서 나가기 전, 매장을 둘러보는 중, 장바구니에 담는 중.** 카운터가 아니다.
- 예상 금액을 입력하거나, 그냥 지폐를 드래그해서 "손에 든다".
- 부족하면 즉시 피드백: "지갑에 1만 2천원 남았어요. 5천원 부족".
- 손에 든 상태는 영속화되고 TTL 을 가진다(예: 6시간). 결제가 감지되지 않으면 지갑으로 자동 복귀 + 알림 "쓰지 않은 3만원을 지갑에 다시 넣었어요".
- 잠금화면 위젯 / Live Activity 에 "손에 든 돈 ₩30,000" 표시. 매장에서 폰을 꺼내도 결제 앱을 보는 것처럼 자연스럽다.

**[C] 결제 (Pay)**
- 사용자는 삼성페이/애플페이/실물카드로 평소처럼 결제. RealCash 는 아무것도 하지 않는다.

**[D] 자동 감지 (Detect)**
- 결제 알림(삼성월렛, 토스, 카드사 앱)을 읽어 금액·가맹점·시각을 파싱.
- 손에 든 금액과 매칭:
  - 정확히 일치 → 자동 정산. 알림: "정산 완료. 스타벅스 ₩6,500".
  - 손에 든 돈 > 결제액 → 거스름돈 계산 후 자동 정산. 알림: "거스름돈 ₩3,500 을 지갑에 넣었어요".
  - 손에 든 돈 < 결제액 → 부족분을 지갑에서 자동으로 꺼냄(큰 권종부터) + 거스름돈. 알림에 "지갑에서 1만원권 1장을 추가로 꺼냈어요" 명시.
  - 손에 든 돈 없음 → 지갑에서 자동 차감. 이 결제는 **"직접 꺼내지 않은 결제"** 뱃지가 붙고, 다음 홈 진입 시 어떤 지폐가 나갔는지 애니메이션으로 보여준다. (편의와 마찰의 균형: 자동이지만 숨기지 않는다)
  - 파싱 신뢰도 낮음 → 알림 액션 "맞아요 / 아니에요" 로 확인. 앱 진입 불필요.
- 중복 제거: 삼성월렛 푸시 + 카드사 푸시가 같은 결제로 두 번 올 수 있음. 금액 + 시간창(예: 2분) + 가맹점 유사도로 dedupe.
- 취소/환불 알림: 역방향 정산(지폐를 거스름돈 규칙으로 다시 지갑에).

**[E] 거스름돈 (Change)**
- 거스름돈은 **최소 개수 권종 조합** 으로 들어온다(실제 가게가 주는 방식). 원화 권종은 canonical 하므로 greedy 가 최적.
- 결과적으로 큰 지폐가 사라지고 잔돈이 쌓인다 → "지폐가 깨지는 고통" 이 자연 발생.
- 잔돈이 많이 쌓이면 다음 꺼내기에서 "잔돈으로 딱 맞춰 내기" 를 제안(현금 사용자가 실제로 하는 행동).

**[F] 환전 (Exchange)**
- 5만원권 1장 → 1만원권 5장 같은 교환을 사용자가 명시적으로 수행. 자동 아님.
- 사용자가 말한 "귀찮지만 아날로그 감성 재미" 가 여기다. 환전 UI 자체를 조금 번거롭게(은행 창구 느낌) 만드는 것도 의도적 마찰로서 선택지.

**[G] 주간/월간 마감 (Review)**
- 기간 끝에 지갑을 펼쳐 보여준다: 남은 지폐, 깨진 큰 지폐 수, "직접 꺼내지 않은 결제" 비율, 꺼낸 금액 vs 실제 금액 괴리(HardPay 에서 계승한 데이터).
- 남은 돈 이월 or 저금통.

### 3.4 눈치 문제(피드백 1) 재검증

- 결제 직전 개입이 사라졌으므로 구조적으로 해결.
- 매장에서 폰을 꺼내 보는 순간이 남는다면(예상 금액 맞춰 꺼내기), 그건 "결제 앱 확인" 과 구분되지 않는다. 위젯/Live Activity 로 앱 진입 없이 확인 가능하게 하면 더 줄어든다.

### 3.5 "그냥 결제" 모드가 너무 편해지는 리스크

- 안 꺼내고 결제해도 자동으로 차감되면 현금 효과가 희석될 수 있다.
- 대응: (1) 자동 차감은 **큰 권종부터** 꺼내서 거스름돈이 잔뜩 생기게 한다(의도적 불편) (2) 홈 진입 시 반드시 "지난 결제에서 나간 지폐" 애니메이션을 본다 (3) 주간 마감에서 "직접 꺼낸 비율" 을 보여준다. 마찰을 **결제 순간이 아닌 다른 순간으로 재배치** 하는 것이다.

### 3.6 나중 아이디어 (Phase 3+)

- **봉투 예산법(envelope budgeting)**: 식비 봉투, 커피 봉투처럼 지갑을 여러 봉투로 나눔. 현금 사용자의 실제 관행이고 지폐 개체 모델 위에 자연스럽게 올라간다.
- 지폐 마모(worn) 상태: 오래 가진 지폐는 조금 낡아 보임. 소유감 강화.
- 가족/공유 지갑.

---

## 4. 결제 감지 — 플랫폼별 기술 조사 결과

### 4.1 Android

**경로: `NotificationListenerService` (알림 접근 권한)**

- 삼성월렛은 등록 카드 결제 시 **앱 내 PUSH 로 결제 내역을 전달하고 SMS 로는 보내지 않는다** (삼성 커뮤니티 공식 답변 기준). 토스·카드사 앱도 결제 승인 푸시를 보낸다. 즉 알림 읽기가 올바른 경로다.
- SMS 읽기(`READ_SMS`)는 Play 정책상 가계부 용도로 사용 불가. 알림 접근은 별도의 **특별 권한(Special app access)** 으로, 런타임 다이얼로그가 아니라 사용자가 설정 화면에서 직접 켜야 한다. 온보딩 UX 에 반영 필요.
- Play 심사: 알림 접근에 전용 선언 폼이 있다는 공식 문서는 이번 조사에서 확인하지 못했다. 다만 민감 권한 일반 정책("핵심 기능에 필수, 눈에 띄는 고지, 최소 수집")이 적용된다고 보고 설계한다. 한국 가계부 앱들(편한가계부 등)이 수년간 같은 방식으로 운영 중이므로 경로 자체는 검증됐다. 데이터는 **온디바이스 처리, 서버 전송 없음** 을 명시하면 심사 리스크가 줄어든다.
- 파싱 전략: 패키지별 규칙 파서(삼성월렛 `com.samsung.android.spay`, 토스 `viva.republica.toss`, 카드사 앱들) + 금액 정규식 + 가맹점 추출 + 신뢰도 점수. 규칙은 **원격 설정(Remote Config)** 으로 업데이트 가능하게 하여 알림 문구 변경에 앱 업데이트 없이 대응.
- **미확인 사항**: 삼성월렛·토스 알림의 실제 문구 포맷은 공개 자료가 없다. **실기기에서 알림 샘플을 수집하는 것이 Phase 0 의 1순위 과제** 다. (디버그 모드에서 수신 알림 원문을 로그로 저장하는 수집 도구를 먼저 만든다.)
- 폴백: 결제 앱 영수증 화면을 **공유 시트** 로 RealCash 에 보내기 / 수동 입력(HardPay 다이얼로그 계승).

### 4.2 iOS

**주 경로: iOS 27 단축어 "알림 수신 시" 자동화 트리거 (2026년 신규)**

- iOS 27 에서 단축어 자동화에 "특정 앱의 알림을 받으면" 트리거가 추가됐다. 알림 앱 선택 + 키워드 필터가 가능하고, 알림 제목/본문이 단축어 입력으로 전달된다. 동료분이 말한 기능이 이것으로 보인다.
- 설계: RealCash iOS 앱이 **App Intent** (`RecordDetectedPaymentIntent(title, body, sourceApp)`) 를 노출 → 사용자가 단축어 자동화에서 "삼성월렛/토스/카드사 앱 알림 수신 시 → RealCash 결제 기록" 을 연결 → 앱이 Android 와 **동일한 파서** 로 처리.
- 사용자 설정 요구: "실행 전 확인" 끄기, "잠금 상태에서 실행 허용" 켜기. `.shortcut` 파일을 앱이 제공하고 온보딩에서 가져오기 가이드를 보여준다.
- 리스크: 단축어는 사용자가 쉽게 끄거나 지울 수 있고, 앱이 그 상태를 직접 확인할 수 없다. → "최근 N일 감지 없음" 휴리스틱으로 재설정 안내.
- iOS 26 이하: 자동 감지 불가. 공유 시트 + 수동 입력으로 동작. 지원 OS 를 iOS 27+ 로 잡을지는 결정 사항.

**보조 경로: iOS 17+ "거래(Transaction)" 트리거**

- 애플페이 결제 시 가맹점·금액·카드를 단축어에 전달한다. 오프라인 단말 탭만 감지되고 온라인 결제는 안 된다(커뮤니티 보고).
- 한국은 2026년 중반 기준으로도 현대카드만 확실하다. 추가 카드사 도입은 지연 보도가 반복됨. **애플페이 사용자 한정 보조 경로** 로만 취급.

### 4.3 공통 매칭 엔진 (shared)

```
DetectedPayment(amount, merchant?, sourceApp, detectedAt, confidence)
        │
        ▼
 dedupe(window=2min, amount, merchant~)
        │
        ▼
 match(Hand?) ──▶ EXACT        → autoSettle
             ──▶ HAND_OVER     → change = minimalChange(hand − amount); autoSettle
             ──▶ HAND_UNDER    → pullFromWallet(amount − hand, largestFirst); change; autoSettle(flag=ADDED_FROM_WALLET)
             ──▶ NO_HAND       → pullFromWallet(amount, largestFirst); change; autoSettle(flag=NOT_HAND_PICKED)
             ──▶ LOW_CONFIDENCE→ notify(confirm/reject)
```

파서·매칭·거스름돈 계산은 전부 순수 Kotlin 으로 공유 모듈에 두고 단위 테스트로 덮는다. 플랫폼 코드는 "알림 문자열을 가져오는 것" 까지만 한다.

---

## 5. 아키텍처 제안

### 5.1 기술 스택: Kotlin Multiplatform + Compose Multiplatform (권장)

| 선택지 | 장점 | 단점 | 판단 |
|---|---|---|---|
| **KMP + Compose Multiplatform** | Kotlin/Compose 그대로. 도메인·UI 공유. 드래그앤드랍 커스텀 제스처가 순수 Compose 라 공유 가능. CMP iOS 는 2025년 stable | iOS 프로덕션 사례가 Flutter 보다 적음. 텍스트 입력·접근성 등 iOS 디테일에서 가끔 걸림 | **권장**. 사용자가 Android 개발자라는 점이 결정적 |
| Flutter | iOS 트랙레코드 최다, 패키지 풍부 | Dart 신규 학습. 기존 Compose 인터랙션 코드 폐기 | 차선 |
| 네이티브 각각 | 플랫폼 디테일 최상 | UI 2배 작업. 사이드 프로젝트 규모에 과함 | 비권장 |

**플랫폼 네이티브로 남기는 부분 (얇게)**
- Android: `NotificationListenerService`, Glance 위젯, 햅틱
- iOS: App Intents(단축어 수신), WidgetKit / Live Activity, 햅틱, `.shortcut` 배포

### 5.2 모듈 구조

```
RealCash/
├── shared/
│   ├── domain/        # 순수 Kotlin. Bill, Wallet, Hand, Payment, ChangeCalculator, Matcher, NotificationParser
│   ├── data/          # SQLDelight(원장/지폐), multiplatform DataStore(설정), RemoteConfig(파서 규칙)
│   └── ui/            # Compose Multiplatform 화면 + 드래그앤드랍 + 지폐 렌더링
├── androidApp/        # NotificationListenerService, Glance 위젯, 진입점
├── iosApp/            # SwiftUI 셸, App Intents, WidgetKit/LiveActivity, Shortcut 파일
└── docs/
```

### 5.3 도메인 모델 (초안)

```kotlin
// 지폐는 개체다. 자동 재편성되지 않는다.
data class Bill(
    val id: BillId,
    val denomination: Int,               // 50_000 … 10
    val state: BillState,                // IN_WALLET | IN_HAND | SPENT | EXCHANGED_AWAY
    val acquiredAt: Instant,
    val source: BillSource,              // ALLOWANCE | CHANGE | EXCHANGE
    val envelopeId: EnvelopeId? = null,  // Phase 3: 봉투 예산
)

data class Hand(                          // HardPay BowlSelection 의 영속화 버전
    val id: HandId,
    val billIds: List<BillId>,
    val createdAt: Instant,
    val expiresAt: Instant,
    val expectedAmount: Int? = null,
    val memo: String? = null,
)

data class DetectedPayment(
    val id: PaymentId,
    val amount: Int,
    val merchant: String?,
    val sourceApp: String,               // 패키지명 / 앱 식별자
    val rawText: String,                 // 디버그·재파싱용 (온디바이스만)
    val detectedAt: Instant,
    val confidence: Float,
    val status: PaymentStatus,           // AUTO_SETTLED | NEEDS_CONFIRM | REJECTED | REFUNDED
)

data class Settlement(
    val paymentId: PaymentId,
    val handId: HandId?,
    val paidBillIds: List<BillId>,
    val changeBillIds: List<BillId>,     // 새로 생성된 거스름돈 지폐
    val flags: Set<SettlementFlag>,      // NOT_HAND_PICKED | ADDED_FROM_WALLET | MANUAL
)

// 원장은 append-only 이벤트. 잔액·지갑 상태는 전부 파생 (HardPay 원칙 계승)
sealed interface LedgerEvent {
    data class AllowanceReceived(val bills: List<Bill>) : LedgerEvent
    data class MovedToHand(val hand: Hand) : LedgerEvent
    data class HandReturned(val handId: HandId, val reason: ReturnReason) : LedgerEvent
    data class PaymentDetected(val payment: DetectedPayment) : LedgerEvent
    data class Settled(val settlement: Settlement) : LedgerEvent
    data class Exchanged(val from: List<BillId>, val to: List<Bill>) : LedgerEvent
    data class Adjusted(val reason: String, val delta: Int) : LedgerEvent   // 수동 보정
}
```

- 잔액 = `sum(IN_WALLET) + sum(IN_HAND)`. 저장하지 않는다.
- "꺼낸 금액 vs 실제 금액" 은 `Hand.expectedAmount` / `sum(hand bills)` / `DetectedPayment.amount` 세 값으로 HardPay 보다 더 풍부하게 남는다.

### 5.4 거스름돈·자동 차감 알고리즘

- **거스름돈**: 원화 권종(50000/10000/5000/1000/500/100/50/10)은 canonical coin system 이므로 greedy 가 최소 개수를 보장한다. 단순 greedy 로 간다.
- **자동 차감(손에 든 돈 부족/없음)**: 지갑에서 **금액 이상이 되는 최소 조합** 을 고르되, 동률이면 **큰 권종 우선** 으로 꺼낸다. 의도적으로 거스름돈이 많이 생기게 하여 "직접 안 꺼내면 지폐가 깨진다" 는 마찰을 만든다.
- **잔돈 털기 제안**: 지갑의 소액 권종만으로 결제액을 정확히 맞출 수 있으면 꺼내기 화면에서 제안한다.

### 5.5 지폐 자산 전략

- GPT 이미지 생성으로 권종별 앞면 디자인 → 벡터화 또는 고해상도 래스터 (hamdoku 경험 활용).
- **실제 한국은행 도안을 그대로 복제하지 않는다.** 화폐 도안은 한국은행 "화폐도안 이용기준" 의 제약을 받는다. 색상·크기 비율·분위기만 차용한 **오리지널 스타일라이즈드 디자인** 으로 간다. 권종 식별은 색 + 숫자 + 크기 차이로 충분하다.
- 물리감: 드래그 시 미세한 기울기와 그림자, 드롭 시 바운스, 같은 권종 겹침 스택(HardPay `backLayers` 아이디어 계승), 다크모드 변형.
- 자산은 `shared/ui` 리소스로 두어 양 플랫폼 동일.

### 5.6 화면 구성 (MVP)

1. **지갑 홈** — 지폐가 실물처럼 겹쳐 배치. 상단 "손에 든 돈" 영역. 이번 기간 잔여·종료일.
2. **용돈 받기** — 금액 입력 → 권종 조합 선택(ATM 느낌) → 지갑으로 떨어지는 애니메이션.
3. **꺼내기** — HardPay 드래그앤드랍 계승. 예상 금액 입력(선택) → 부족/초과 즉시 표시 → "손에 들기".
4. **정산 알림 / 확인 시트** — 자동 정산 결과, 저신뢰 확인, 거스름돈 애니메이션.
5. **환전** — 지갑 지폐 선택 → 바꿀 권종 조합 → 교환.
6. **가계부 / 마감** — 결제 리스트(직접 꺼낸/자동 뱃지), 기간 마감 리뷰.
7. **설정 / 연결** — Android: 알림 접근 켜기 + 감지 소스 앱 선택. iOS: 단축어 설치 가이드 + 테스트 알림 보내기.

---

## 6. 로드맵

### Phase 0 — 기술 검증 스파이크 (1~2주)
목표: 가장 불확실한 세 가지를 코드 한 줄 설계 전에 확인한다.
- [ ] **Android 알림 샘플 수집**: `NotificationListenerService` 로 삼성월렛·토스·카드사 앱 알림 원문(패키지, title, text, extras)을 로그에 쌓는 디버그 앱. 실제 결제 10~20건 수집. 이 결과가 파서 규칙의 기준이 된다.
- [ ] **iOS 27 단축어 → App Intent PoC**: 임의 앱 알림을 트리거로 title/body 를 App Intent 로 받는 최소 앱. "실행 전 확인 끄기", "잠금 상태 실행" 동작 확인. iOS 27 미만 기기에서의 폴백 확인.
- [ ] **Compose Multiplatform iOS 드래그앤드랍 성능**: HardPay 의 `DenomGhost`/`detectDragGestures` 를 CMP 로 옮겨 iOS 시뮬레이터·실기기에서 60fps 확인.

### Phase 1 — Android MVP (3~4주)
- 지폐 개체 모델 + SQLDelight 원장
- 용돈 받기 / 지갑 홈 / 꺼내기 / 환전
- 알림 감지(삼성월렛 + 토스 우선) + 매칭 엔진 + 거스름돈
- 수동 입력 폴백
- 임시 자산(HardPay 수준) 으로 플로우 먼저 검증

### Phase 2 — iOS (3~4주)
- CMP 로 공유 UI 그대로 올림
- App Intents + `.shortcut` 배포 + 온보딩
- 위젯(Glance / WidgetKit), Live Activity "손에 든 돈"

### Phase 3 — 몰입도 (2~3주)
- GPT 기반 지폐 자산 교체, 물리 애니메이션
- 주간/월간 마감 리뷰
- 봉투 예산, 저금통, 지폐 마모

### Phase 4 — 운영
- 파서 규칙 원격 설정
- 카드사 앱 확대, 취소/환불 처리 고도화

---

## 7. 리스크와 열린 질문

### 리스크
| 리스크 | 영향 | 대응 |
|---|---|---|
| 알림 문구 변경으로 파싱 실패 | 자동 정산 중단 | 원격 규칙 + 신뢰도 점수 + 알림 액션 확인 + 수동 폴백 |
| Play 심사에서 알림 접근 거부 | Android 핵심 기능 불가 | 핵심 기능 설명, 온디바이스 처리 명시, 영수증 공유 시트 폴백 |
| iOS 사용자가 단축어를 꺼버림 | 감지 중단을 앱이 모름 | "N일 감지 없음" 휴리스틱 안내 |
| 중복 알림(삼성월렛 + 카드사) | 이중 차감 | 금액·시간창·가맹점 dedupe, 소스 앱 우선순위 설정 |
| "그냥 결제" 가 너무 편해 현금 효과 희석 | 제품 가치 손상 | 큰 권종 우선 차감, 홈 진입 시 지폐 이탈 애니메이션, 마감 리뷰에 직접 꺼낸 비율 |
| 실제 화폐 도안 저작권 | 스토어 반려·법적 이슈 | 오리지널 스타일라이즈드 디자인 |

### 결정이 필요한 질문
1. **예산 주기**: 주급형(주) vs 월급형(월)? 둘 다 지원하면 복잡도 증가. MVP 는 하나로.
2. **iOS 최소 지원 버전**: iOS 27+ 만 지원하고 자동 감지를 전제로 갈지, 26 이하도 수동 모드로 받을지.
3. **동전 범위**: 10원·50원까지 가면 거스름돈 UI 가 지저분해진다. 카드 결제 금액은 보통 10원 단위까지 나오므로 필요하긴 하다. "동전은 자동으로 동전지갑에 모인다" 같은 단순화 가능.
4. **감지 소스 우선순위**: 삼성월렛 푸시 vs 카드사 푸시 중 어느 것을 1차 소스로 볼지 (Phase 0 샘플 보고 결정).
5. **자동 차감 정책**: 손에 안 들고 결제했을 때 큰 권종 우선(마찰 강화) vs 최소 개수(편의)? 기본값 + 설정으로 둘지.

---

## 8. 다음 액션

- **합의 필요**: 섹션 3 컨셉·원칙, 5.1 스택 결정, 7 의 질문 5개.
- **합의 후 바로 시작**: Phase 0 스파이크. 특히 Android 알림 샘플 수집 앱은 하루면 만들 수 있고, 이 데이터 없이는 파서 설계를 확정할 수 없다.

---

## 참고 자료

- 삼성월렛 결제 내역은 앱 PUSH 로만 전달(SMS 아님): [Samsung Community 답변](https://r1.community.samsung.com/t5/samsung-wallet/%EB%AC%B8%EC%9E%90-%EC%95%8C%EB%A6%BC-%EC%84%9C%EB%B9%84%EC%8A%A4-%EC%9E%90%EB%8F%99-%EB%93%B1%EB%A1%9D/m-p/22739185/highlight/true)
- iOS 27 단축어 알림 트리거: [MacStories — Deflector](https://www.macstories.net/ios/deflector-turns-ios-27-notification-triggers-into-a-shortcut-launcher/), [Beard.fm 가이드](https://wiki.beard.fm/whats-new-ios-27/how-to-use-ios-27s-notification-triggers-for-advanced-cross-), [FWRDSMS — 자동화 실행 설정](https://www.fwrdsms.com/blog/shortcuts-automation-not-running)
- iOS 17 Transaction 트리거: [iDrop News](https://www.idropnews.com/?p=197589), [r/shortcuts 튜토리얼](https://redlib.hbubli.cc/r/shortcuts/comments/17dqih9/tutorial_how_to_automatically_add_apple_pay)
- 애플페이 한국 카드사 확대 지연: [다음 뉴스 2026-07](https://v.daum.net/v/20260720104621631)
- Google Play 민감 권한 일반 정책: [Permissions Declaration](https://support.google.com/googleplay/android-developer/answer/9214102), [Sensitive Information](https://support.google.com/googleplay/android-developer/answer/16558241)
- Android 알림 파싱 가계부 오픈소스 사례: [finance-notification-expense-manager](https://github.com/0rchid241/finance-notification-expense-manager/issues/2), [allowance-manager](https://github.com/2blue-99/allowance-manager)
- KMP vs Flutter 2026: [Shorebird](https://shorebird.dev/blog/flutter-vs-kotlin-multiplatform), [CMP 1.8 iOS stable](https://besthub.dev/articles/compose-multiplatform-1-8-0-for-ios-stable-release-performance-benchmarks-and-new-features-d43d5b2a8dc4)
- 행동경제학: Prelec & Loewenstein (1998) *The Red and the Black: Mental Accounting of Savings and Debt*; Raghubir & Srivastava (2008) *Monopoly Money: The Effect of Payment Coupling and Form on Spending Behavior*
