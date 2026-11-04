# 03. GPT 이미지 위임 파이프라인 운영서

> 원본 설계: [GPT 이미지 위임 구조](https://claude.ai/artifact/UEGA9LoS91LmyoVEmoYvxs) (hamdoku에서 쓰던 방식). 이 문서는 RealCash 저장소에 맞춘 운영 요약이다.

클라우드 Claude 세션은 그림을 그리지 않는다. 의뢰서를 GitHub에 올리면, 사람의 맥에서 도는 Codex 워커가 ChatGPT 요금제의 내장 이미지 생성(`$imagegen`)으로 그림을 만들어 다시 올리고, Claude가 검수해서 앱에 넣는다. Git 저장소가 둘 사이의 우편함이다. Claude 사용량과 ChatGPT 이미지 한도는 따로 돌아서 한쪽이 막혀도 다른 쪽은 계속 간다.

## 1. 역할

| 역할 | 어디서 | 하는 일 | 하지 않는 일 |
|---|---|---|---|
| Claude 세션 | 클라우드 | 의뢰서 작성·push, 검수, 크로마키·크기 맞춤, manifest·LOG 기록, 앱 반영 | codex 실행, `scripts/` 수정 |
| 동기화 워커 `scripts/worker-sync.sh` | 맥 | 5분마다 동기화 브랜치 pull, `assets/inbox`·`assets/jobs`만 커밋·push | 그 밖의 파일 커밋, 생성 중 커밋 |
| Codex 워커 `scripts/worker-codex.sh` | 맥 | queue의 의뢰서를 하나씩 `codex exec`로 실행, PNG 확인, STATUS 기록 | git, 코드 작성, 검수 |
| 사람 | 어디서든 | 시안 고르기, final 승인, 맥 켜 두기, `codex login` | 파일 옮기기 같은 손일 |

## 2. 폴더 약속

폴더 위치와 파일 이름이 곧 상태다.

```
assets/
  jobs/
    queue/      NN-<id>.prompt.md   ← Claude가 넣는다 (NN 작은 것부터)
    running/    처리 중 (gitignore, 반쪽 결과를 안 올리려고. .gitkeep만 추적)
    done/       생성 성공한 의뢰서 (기록용)
    failed/     생성 실패한 의뢰서
    blocked/    규칙 위반 의뢰서 + <파일>.reason (이름 형식, 이미 처리된 이름, 기존 결과, 3회 초과)
    gates/      <게이트>.PASS        ← 선행 검수 통과 표시 (검수 근거 없이 만들지 않는다)
    hold/       잠시 빼 둔 의뢰서 (워커가 안 봄)
  inbox/<id>/
    <id>-1.png    결과 원본 (inbox 최상위의 이 이름만, PNG 서명까지 맞아야 성공)
    STATUS        "done 날짜 images=1 rc=0 116s" 또는 "failed …"
    ATTEMPTS      실제 생성 시도 횟수 (한도 재시도는 안 셈, 지우지 않는다)
    codex.log     Codex 출력 (codex-limit-*.log는 한도에 걸린 시도)
    REVIEWED      ← Claude가 쓴다: "PASS draft" / "PASS 미채택" / "FAIL 이유"
    attempt-N/    불합격한 이전 시도 보관
  raw/            검수 통과 원본
  img/            정리본 (앱 리소스로 옮기기 전 단계. 앱 모듈이 생기면 docs/01 §5.5대로 shared/ui 리소스로)
  manifest.json   에셋 id → { path, size, status: draft|final, source: gpt, aiGenerated }
  LOG.md          에셋별 출처 기록
```

워커 런타임 파일은 `scripts/log/`(로그·pid·잠금·날짜별 카운터)와 `scripts/STOP`에 생기고 `scripts/.gitignore`로 빠진다.

## 3. 의뢰서 쓰는 법

- 이름은 `NN-<id>.prompt.md`. NN은 두 자리(00~99), id는 소문자·숫자·점·하이픈(`bill.w10000.a`). 형식이 틀리면 blocked.
- **먼저 `.tmp`로 쓰고 다 쓴 뒤 이름을 바꾼다** (`*.tmp`는 `assets/.gitignore`로 빠짐).
- 의뢰서 하나 = 이미지 1장 = 이미지 도구 호출 1회.
- 그 자체로 완결된 프롬프트여야 한다. Codex는 대화 맥락이 없다. 참조 그림은 저장소 안 경로로 적고 "직접 열어 보라"고 쓴다.
- 재의뢰는 새 NN을 붙인다. done/failed에 같은 파일 이름이 있으면 blocked.
- 선행 조건(선택): `# requires-review: <id>`는 그 id의 REVIEWED가 PASS일 때만, `# requires-gate: G-001`은 `assets/jobs/gates/G-001.PASS`가 있을 때만 실행. 안 되면 건너뛰고 다른 의뢰를 먼저 한다.
- **돈 그림 규칙 (RealCash):** 실제 한국은행 도안을 복제하지 않는다. 실존 인물 초상, "한국은행" 등 읽히는 글자, 일련번호, 관인, 은선·홀로그램·미세문자 흉내를 [절대 금지]에 이유(저작권·위조 오인 방지)와 함께 적는다. 권종은 색 + 숫자 + 크기로 구별한다. 녹색 계열 지폐는 `#FF00FF` 배경.

```
$imagegen
내장 이미지 생성 도구만 사용하라. 스크립트 작성, API 키 사용, 패키지 설치를 하지 마라.

[참조] 아래 파일을 직접 열어 보고 따르라.
1. assets/raw/<기준그림>.png — 화풍 기준
2. assets/img/…/기존그림.png — 같은 색·조명·질감으로 맞춘다

[용도] 이 그림이 화면 어디에 쓰이고, 무엇이 위에 겹치는지

[요청] 무엇을, 어떤 자세·구도로

[절대 금지] 들어가면 안 되는 것과 그 이유 (글자·로고·특정 무늬 등)

[스타일] 화풍, 질감, 조명, 색

[배경] 단색 #00FF00 크로마키 배경. 피사체에 녹색을 쓰지 마라. 그림자는 피사체 안에만.
       (피사체에 녹색이 있으면 #FF00FF, 화면 전체 배경이면 "그림 전체를 꽉 채운 불투명 배경")

[크기] 1024x1024, 피사체는 가운데, 가장자리 여백 10%

[저장] 완성된 이미지를 assets/inbox/<id>/<id>-1.png 로 복사하라.
       이미지 저장 외에는 어떤 파일도 만들거나 고치지 마라.

[생성 횟수] 이미지 생성 도구 호출은 1회만. 다른 크기로 나와도 재생성하지 말고
           원본을 그대로 저장하고 실제 크기를 보고한다. 정규화와 검수는 검수 담당자가 한다.
```

첫 시험 의뢰서: `assets/jobs/queue/00-bill.w10000.a.prompt.md` (만 원권급 지폐 앞면, 모든 돈 그림의 화풍 기준).

## 4. Codex 워커

1. 단일 실행 잠금(`scripts/log/codex-worker.lock`)을 잡고, 끊겼던 `running/` 의뢰를 queue로 되돌린다.
2. 미검수(STATUS는 있고 REVIEWED는 없는 inbox 폴더)가 4개 이상이면 쉰다.
3. queue에서 이름 순으로 첫 실행 가능 의뢰를 고른다. 규칙 위반은 blocked + `.reason`, 선행 조건 미충족은 건너뜀.
4. running으로 옮기고 `codex exec --dangerously-bypass-approvals-and-sandbox -C <저장소> "<의뢰서>"`를 최대 30분.
5. `assets/inbox/<id>/<id>-1.png`, `-2.png`만 PNG 서명까지 확인해 센다 (attempt-N/의 옛 그림은 안 셈).
6. 그림이 없고 로그 끝에 사용 한도 문구가 있으면 시도로 안 치고 queue로 되돌려 20분 뒤 재시도.
7. 성공은 done/, 실패는 failed/로 옮기고 STATUS·ATTEMPTS 기록. 60초 쉬고 다음. `scripts/STOP`이 생기면 현재 작업을 마치고 멈춘다.

| 환경 변수 | 기본값 | 뜻 |
|---|---|---|
| `IMAGE_MAX_UNREVIEWED` | 4 | 미검수가 이만큼 쌓이면 대기 |
| `IMAGE_MAX_ATTEMPTS` | 3 | 같은 id 실제 생성 시도 상한 |
| `IMAGE_JOBS_PER_DAY` | 0 | 하루 상한 (0이면 없음, `scripts/log/codex-count-날짜`는 기록용) |
| `JOB_TIMEOUT` | 1800 | 의뢰 하나 최대 초 |
| `LIMIT_RETRY` | 1200 | 한도에 걸렸을 때 재시도 간격(초) |
| `POLL` / `JOB_GAP` | 120 / 60 | 큐가 빌 때 확인 간격 / 의뢰 사이 간격 |
| `SYNC_INTERVAL` | 300 | 동기화 주기(초) |
| `SYNC_REMOTE` / `SYNC_BRANCH` | origin / main | 동기화 원격과 브랜치 |

환경 변수는 `start.sh`를 실행할 때 앞에 붙인다: `SYNC_BRANCH=main IMAGE_MAX_UNREVIEWED=4 bash scripts/start.sh`.

## 5. 동기화 워커

- 체크아웃된 브랜치가 `SYNC_BRANCH`가 아니거나 rebase·merge 중이면 아무것도 안 한다.
- `running/`에 의뢰가 있으면 커밋하지 않고 받기만 한다.
- `git add -A -- assets/inbox assets/jobs` → `[sync] 이미지 작업 결과` 커밋. 그 밖의 경로는 커밋하지 않는다.
- `git pull --rebase --autostash`, 실패하면 rebase를 취소하고 `scripts/log/sync-last.out`에 남긴다(맥 알림은 12~23시만). 앞선 커밋이 있으면 push.
- **충돌 규칙:** Claude 쪽 push가 충돌하면 `assets/inbox/`와 `assets/jobs/`는 원격(맥) 쪽의 이동·생성을 살린다.

**SYNC_BRANCH 주의:** 기본값은 `main`이다. 이 저장소에 아직 `main`이 없다면 (1) `main` 브랜치를 만들어 기본 브랜치로 쓰거나, (2) 맥에서 `SYNC_BRANCH=<쓰는 브랜치> bash scripts/start.sh`로 시작한다. Claude 세션도 의뢰서를 같은 브랜치에 push해야 맥에 닿는다.

## 6. 검수 (Claude 쪽)

대상: `assets/inbox/*/STATUS`가 있고 `REVIEWED`가 없는 폴더. 도구 설치·명령은 `tools/loop/README.md`.

1. `git pull` 뒤 원본 PNG를 직접 본다. 금지 사항(글자, 초상, 보안 요소 흉내), 화풍·색·비율.
2. 투명이 필요하면 크로마키: `chroma-key.ts in.png out.png [--key magenta]`. 어두운 배경 시트로 테두리·구멍 확인 (마젠타 키는 분홍 기운이 같이 빠질 수 있다).
3. 크기 맞춤: `fit.ts in.png out.png 512x256 --margin 0.02`.
4. 시트: `review-sheet.ts --bg dark|checker`, `--small 40,64`, `--compare a b c`, `--overlay base variant`.
5. 취향이 갈리는 결정은 비교 이미지를 사용자에게 보여 주고 고르게 한다.
6. **통과:** 원본 → `assets/raw/<id>.png`, 정리본 → `assets/img/…`, `manifest.json`에 `{ path, size, status: "draft", source: "gpt", aiGenerated: true }`, `LOG.md`에 의뢰서·원본 크기·처리 명령·검수 근거, `REVIEWED`에 `PASS draft 날짜 한 줄 근거`.
7. **불합격:** `REVIEWED`에 `FAIL 이유`, 결과(STATUS·REVIEWED·PNG·로그)를 `attempt-N/`으로 옮기고, 의뢰서를 고쳐 새 NN으로 다시 넣는다. ATTEMPTS는 지우지 않는다. 같은 id 3번 실패면 사람에게 묻는다.
8. 안 고른 시안은 `PASS 미채택` (워커가 미검수로 세지 않게).
9. `final`은 사람이 승인한 뒤에만.

## 7. 맥 한 번 준비 체크리스트 (사람)

- [ ] Codex CLI 설치 후 ChatGPT 요금제 계정으로 `codex login`. 이미지 생성은 이 계정 한도를 쓴다.
- [ ] **워커 전용 클론**을 만든다 (Codex를 샌드박스 없이 돌리므로 평소 작업 폴더와 분리, 비밀 키를 두지 않는다): `git clone https://github.com/dogdduddy/RealCash.git ~/realcash-worker`
- [ ] 그 클론에서 push가 되게 인증하고 `git config user.name` / `user.email`을 설정한다.
- [ ] 동기화 브랜치 결정: `main`을 쓰면 `git checkout main`. 다른 브랜치면 그 브랜치를 체크아웃하고 아래 명령마다 `SYNC_BRANCH=<브랜치>`를 붙인다.
- [ ] `node`, `python3`이 있는지 확인 (`xcode-select --install`로 python3, node는 공식 설치본이나 Homebrew). 스크립트는 macOS 기본 bash 3.2로 돈다.
- [ ] 전원 연결, 덮개 열기 (닫으면 잠든다). 워커는 `caffeinate`로 잠들지 않게 뜬다.
- [ ] `bash scripts/start.sh` — 도구·git 원격·브랜치·Codex 응답을 점검한 뒤 두 워커를 띄운다.
- [ ] `bash scripts/status.sh` — 워커 상태, 의뢰 수, 미검수 수, blocked 이유, 오늘 기록.
- [ ] `bash scripts/stop.sh` — 현재 작업을 마치고 멈춤. `bash scripts/stop.sh --now`는 즉시 종료(끊긴 의뢰는 다음 시작 때 queue로 돌아간다).
- [ ] 워커 점검(선택): `bash scripts/tests/run.sh`, `cd tools/loop && npm install && npm test`.

## 8. 한계와 주의

- 맥이 꺼지거나 잠들면 아무것도 안 만들어진다.
- 결과가 Claude에게 닿기까지 동기화 주기(5분) + 생성 시간(보통 2~3분).
- 이미지 결과는 매번 달라서, 같은 화풍을 유지하려면 기준 그림(첫 통과본 `assets/raw/bill.w10000.a.png` 등)을 의뢰서 [참조]에 계속 넣는다.
- 도구는 1024를 달라고 해도 다른 크기를 줄 수 있다. 재생성하지 말고 검수 쪽에서 맞춘다.
- 스토어의 AI 생성 표시와 재의뢰 근거를 위해 `LOG.md`와 manifest의 `aiGenerated`를 빠짐없이 적는다.
