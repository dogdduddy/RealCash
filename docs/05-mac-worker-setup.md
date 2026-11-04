# 05. 새 맥에서 워커 셋업 (Codex에게 맡기는 버전)

> 상황: 워커를 돌리던 맥이 없어져 새 맥에 처음부터 올려야 한다. 사람은 원격으로 Codex에게 지시만 한다.
> 아래 "프롬프트"를 그 맥에서 실행 중인 Codex에 그대로 붙여 넣는다. Codex가 스스로 할 수 없는 두 가지(GitHub 로그인 코드 입력, 전원·덮개)는 사람에게 되묻도록 적어 두었다.

## 사전 조건 (사람)

- 새 맥에 Codex CLI 가 설치되어 있고 `codex login` 이 끝나 있다 (프롬프트를 실행하는 바로 그 Codex).
- 맥이 전원에 연결되어 있고, 덮개를 열어 둔다 (닫으면 잠든다).
- GitHub 계정으로 `dogdduddy/RealCash` 에 push 할 수 있다. 로그인 과정에서 한 번 코드 입력이 필요하다.

## Codex에게 붙여넣는 프롬프트

```
너는 이 맥을 RealCash 프로젝트의 "이미지·UI 워커 전용 머신"으로 셋업하는 중이다. 저장소: https://github.com/dogdduddy/RealCash , 동기화 브랜치: claude/eager-lovelace-gms9hx . 사람은 원격에서 이 대화만 보고 있으니, 네가 할 수 없는 단계(브라우저 로그인 코드 입력 등)는 멈추고 코드와 URL을 그대로 보여 준 뒤 사람이 끝냈다고 답할 때까지 기다려라. 비밀 키·토큰을 저장소 폴더 안에 두지 마라. 아래 순서대로 하고, 각 단계마다 실행한 명령과 결과를 짧게 보고하라.

1. 도구 설치 확인·설치
   - `xcode-select -p` 가 실패하면 `xcode-select --install` 을 띄우고 사람에게 설치 완료를 확인받는다.
   - Homebrew 가 없으면 공식 설치 스크립트로 설치한다(설치 후 `brew` 가 PATH 에 있게 셸 설정 반영).
   - `brew install git node gh python@3.12` 그리고 JDK: `brew install --cask temurin@21` (실패 시 `temurin`). `java -version` 이 17 이상이어야 한다.
   - Android SDK: `~/Library/Android/sdk` 가 이미 있으면(Android Studio 설치됨) 그대로 쓴다. 없으면 `brew install --cask android-commandlinetools` 후
     `sdkmanager --sdk_root="$HOME/Library/Android/sdk" "platform-tools" "platforms;android-36" "build-tools;36.0.0"` 를 실행하고 `yes | sdkmanager --sdk_root="$HOME/Library/Android/sdk" --licenses` 로 라이선스를 받는다.
   - 확인: `git --version`, `node --version`, `python3 --version`, `java -version`, `ls "$HOME/Library/Android/sdk/platforms"`.

2. GitHub 인증
   - `gh auth status` 가 로그인 상태가 아니면 `gh auth login --hostname github.com --git-protocol https --web` 를 실행한다. 출력된 일회용 코드와 URL 을 사람에게 그대로 보여 주고, 사람이 "완료"라고 할 때까지 기다린 뒤 계속한다.
   - `gh auth setup-git` 으로 git 이 gh 자격증명을 쓰게 한다.

3. 워커 전용 클론
   - `git clone https://github.com/dogdduddy/RealCash.git "$HOME/realcash-worker"`
   - `cd "$HOME/realcash-worker" && git checkout claude/eager-lovelace-gms9hx`
   - `git config user.name` 과 `user.email` 을 설정한다. 값은 `gh api user --jq .login` 과 GitHub noreply 주소(`gh api user --jq .id` 로 `<id>+<login>@users.noreply.github.com`)를 쓴다.
   - `git push --dry-run origin HEAD:claude/eager-lovelace-gms9hx` 가 오류 없이 끝나는지 확인한다.

4. 환경변수를 영구 설정
   - `~/.zshrc` 끝에 아래 두 줄을 추가한다(이미 있으면 중복 추가하지 않는다).
     export ANDROID_HOME="$HOME/Library/Android/sdk"
     export SYNC_BRANCH=claude/eager-lovelace-gms9hx
   - 현재 셸에도 같은 값을 export 한다.

5. Codex 자체 점검
   - `codex exec "echo codex-ok"` 가 "codex-ok" 를 출력하는지 확인한다. 실패하면 `codex login` 상태를 사람에게 알리고 멈춘다.

6. 워커 시작
   - `cd "$HOME/realcash-worker" && bash scripts/start.sh` 를 실행한다. 사전 점검에서 [x] 가 나오면 그 줄을 보고하고 고친 뒤 다시 실행한다.
   - 30초 뒤 `bash scripts/status.sh` 출력을 그대로 보고한다. codex·sync·ui 세 워커가 "실행 중"이어야 한다.
   - 첫 UI 작업은 Gradle 과 의존성을 내려받느라 10분 이상 걸릴 수 있다. `tail -20 scripts/log/runner-$(date +%F).log` 를 5분 간격으로 두 번 보고하라.

7. 멈추는 법(보고만)
   - 끝으로 사람에게 `bash scripts/stop.sh`(현재 작업 후 정지), `bash scripts/stop.sh --now`(즉시), `bash scripts/status.sh`(상태) 세 명령을 알려 주고 종료한다.
```

## 효과음(CC0) 조달 프롬프트 — 셋업이 끝난 뒤 별도로

```
RealCash 워커 클론(~/realcash-worker)에서 CC0 효과음 5종을 모아 브랜치로 올려라. 저장소 외부 파일을 수정하지 말고, 저장소 안에는 assets/sfx/ 와 assets/LOG.md 만 바꾼다.
1. https://kenney.nl/assets/casino-audio 와 https://kenney.nl/assets/ui-audio , https://kenney.nl/assets/impact-sounds 에서 zip 을 받아 임시 폴더에 푼다(모두 CC0 1.0, 출처 표기 불필요하지만 기록은 한다).
2. 아래 용도에 맞는 파일을 파일명·길이로 골라 복사하고 이름을 바꾼다(OGG 또는 WAV, 1초 이내):
   - assets/sfx/wallet_open.ogg : 지갑 열기 (가죽·천이 펴지는 짧은 소리. 없으면 카드 뒤집는 소리 계열)
   - assets/sfx/wallet_close.ogg : 지갑 닫기 (둔탁하고 짧은 스냅)
   - assets/sfx/bill_rustle.ogg : 지폐 스치는 소리 (종이·카드 슬라이드)
   - assets/sfx/bill_pick.ogg : 지폐 집기 (짧은 종이 집는 소리)
   - assets/sfx/coin_drop.ogg : 동전 놓기 (칩·동전 떨어지는 소리, 하나짜리)
3. assets/LOG.md 끝에 "## 효과음 (CC0)" 블록을 추가해 각 파일의 원본 팩 이름·원본 파일명·라이선스(CC0 1.0)를 적는다.
4. `git checkout -b sfx/kenney-cc0 claude/eager-lovelace-gms9hx`, 위 파일들만 add, 커밋 메시지 "assets: CC0 효과음 5종 (Kenney)", `git push -u origin sfx/kenney-cc0`. 그리고 동기화 브랜치로 되돌아온다(`git checkout claude/eager-lovelace-gms9hx`). 작업 트리를 깨끗하게 남겨라.
5. 어떤 파일을 왜 골랐는지 표로 보고한다. 실제로 들어 보고 고르는 것은 사람이 나중에 한다.
```

## 그 다음(클라우드 세션이 함)

- 올라온 `sfx/kenney-cc0` 브랜치를 리뷰해 머지하고 `assets/sfx` 를 Compose 리소스(`files/`)로 노출한다.
- 접힌 지갑·펼친 지갑·접시 이미지가 검수를 통과하면 홈 v2 → 열린 지갑 → 물리 인터랙션 → 햅틱·소리 순으로 UI 작업을 의뢰한다(docs/04 §5).
