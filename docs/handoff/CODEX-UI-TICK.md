# Codex UI 작업 상시 지시 (워커가 모든 UI 의뢰서 앞에 붙인다)

너는 RealCash(Kotlin Multiplatform + Compose Multiplatform 가계부 앱)의 **UI 코드 작업 하나**를 맡았다. 이 글 아래에 붙은 의뢰서가 이번 작업의 전부다. 사람과 대화할 수 없으니, 의뢰서와 저장소 파일만 보고 판단한다.

## 작업 위치
- 지금 폴더는 워커 전용 git worktree다. 동기화 브랜치의 최신 커밋이 분리된 HEAD로 체크아웃되어 있다.
- 무엇이든 읽어도 된다. 먼저 `CLAUDE.md`, `docs/01-realcash-analysis-and-design.md`(§3 사용자 플로우, §5.5 지폐 자산, §5.6 화면 구성)와 `shared/ui/` 현재 코드를 읽는다. 도메인 규칙은 `shared/domain`, 저장소 API는 `shared/data`를 읽어서 쓴다.

## 고쳐도 되는 곳 (이 밖은 한 글자도 바꾸지 마라)
- `shared/ui/**`
- `assets/img/**` (검수를 통과한 그림을 앱에 연결할 때만)

## 절대 하지 마라
- `shared/domain/**`, `shared/data/**`, 그 밖의 모듈 변경. 도메인·데이터 로직을 UI 안에 새로 짜 넣지도 마라 (필요하면 요약에 "도메인 쪽 필요"로 적는다).
- Gradle 파일(`settings.gradle.kts`, `build.gradle.kts`, `gradle/**`, 버전 카탈로그 `gradle/libs.versions.toml`), `scripts/`, `docs/`, `CLAUDE.md`, `README.md`, `codex/`, `assets/` 중 `assets/img/` 밖, `iosApp/` 변경.
- 새 의존성 추가 (라이브러리, 플러그인, 버전 변경).
- git 명령 (add, commit, branch, checkout, stash, reset, clean, push 모두). 커밋과 push는 워커가 한다.
- 이미지 생성. 그림이 필요하면 요약에 "이미지 의뢰 필요"로 적는다.
- 비밀 값 출력 (환경 변수 전체, 토큰, 키, `~/.codex` 등 설정 파일 내용).
- 허용 경로 밖을 건드리면 워커가 작업 전체를 되돌리고 실패로 기록한다.

## 작업 기준
- 화면 문구는 한국어. 금액은 원 단위 정수, 천 단위 쉼표.
- 지폐 그림은 오리지널 스타일라이즈드 디자인만 쓴다 (실제 한국은행 도안 흉내 금지).
- 의뢰서의 [완료 기준]을 하나씩 만족시키고, 기존 테스트를 깨지 마라.
- JDK가 있으면 `./gradlew :shared:ui:jvmTest`가 통과하는 상태로 끝낸다. 워커가 끝난 뒤 같은 검사를 다시 돌려 실패하면 작업 전체를 버린다. JDK나 네트워크 때문에 돌릴 수 없으면 그렇다고 적는다.
- 의뢰서가 스크린샷을 요구하면 의뢰서가 지정한 `shared/ui/` 안 경로에만 저장한다.

## 끝낼 때
마지막 응답에 짧게 적는다.
1. 바꾼 파일 목록
2. [완료 기준]별로 어떻게 만족했는지
3. 실제로 돌린 검사와 결과 (돌리지 못했으면 이유)
4. 남은 일, 도메인·이미지 쪽에 필요한 것
