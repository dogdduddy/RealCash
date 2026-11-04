# RealCash

> 카드로 내지만, 지갑은 현금처럼.

디지털 결제(삼성페이·애플페이·카드)는 그대로 쓰면서, 돈을 **보고 · 꺼내고 · 거슬러 받는 감각** 만 현금처럼 되살려 지출을 줄이는 앱.
[HardPay](https://github.com/dogdduddy/HardPay) 사이드 프로젝트의 후속으로, Kotlin Multiplatform 으로 Android 와 iOS 를 함께 만든다.

## 핵심 아이디어

1. **결제 순간에 앱을 열지 않는다.** 개입은 결제 전(지폐 꺼내기)과 후(결제 알림 자동 정산)로 옮긴다.
2. **지폐는 개체다.** 처음 받은 권종 조합이 그대로 유지되고, 거스름돈은 가게처럼 최소 개수로 들어오며, 환전은 사용자가 직접 한다.
3. **정산은 자동, 결정은 수동.** 결제 알림을 읽어 거스름돈까지 계산하고, 무엇을 꺼낼지는 사용자가 정한다.

## 상태 (2026-11-04)

MVP 코드가 올라와 있고 데스크톱과 Android 디버그 빌드로 전체 플로우가 동작한다. 실기기 검증과 실제 알림 문구 수집(Phase 0)이 진행 중이다.

| 모듈 | 내용 | 테스트 |
|---|---|---|
| `shared/domain` | 지폐 개체 모델, append-only 이벤트 원장 + 리듀서, 거스름돈·차감·정산 계산기, 알림 파서 | 86 |
| `shared/data` | SQLDelight 이벤트 저장, `WalletRepository`, `PaymentIntake`(멱등 결제 인테이크), 설정·파서 규칙 | 23 |
| `shared/ui` | Compose Multiplatform 화면: 지갑 홈, 용돈 받기, 꺼내기(드래그앤드랍), 정산 확인, 환전, 가계부, 설정, 알림 시뮬레이터 | 70 |
| `androidApp` | 결제 알림 리스너, 정산 알림(맞아요/아니에요), 알림 샘플 수집기(디버그) | 26 |
| `iosApp/NotificationIntentPoC` | iOS 27 단축어 알림 트리거 → App Intent 검증용 PoC (Xcode 필요) | - |

## 빌드

```bash
./gradlew build                      # 전체 (Android SDK 필요: ANDROID_HOME 또는 local.properties 의 sdk.dir)
./gradlew :shared:ui:run             # 데스크톱 앱 (~/.realcash/realcash.db)
./gradlew :shared:ui:jvmTest         # 화면 테스트 + shared/ui/build/screenshots/*.png 오프스크린 스크린샷
./gradlew :androidApp:assembleDebug  # androidApp/build/outputs/apk/debug/
```

## 문서

- [docs/01-realcash-analysis-and-design.md](docs/01-realcash-analysis-and-design.md) — HardPay 회고 분석, 제품 컨셉, 결제 감지 기술 조사, 아키텍처·로드맵
- [docs/02-engineering-plan.md](docs/02-engineering-plan.md) — 기본 결정, 도메인 스펙, 작업 분해와 규칙
- [docs/03-gpt-image-pipeline.md](docs/03-gpt-image-pipeline.md) — GPT 이미지·UI 작업 위임 파이프라인 운영서 (맥 워커)
- [iosApp/NotificationIntentPoC/README.md](iosApp/NotificationIntentPoC/README.md) — iOS 알림 트리거 검증 절차

## 작업 방식

설계·리뷰는 Claude 세션이, 구현은 서브에이전트가, 이미지와 UI 다듬기는 맥의 Codex 워커가 맡는다 (`CLAUDE.md`).
