#!/usr/bin/env bash
# 워커 시작: 사전 점검 → Codex 이미지 워커, 동기화 워커, Codex UI 워커를 백그라운드로 실행 (맥이 잠들지 않게 caffeinate)
# 사용: bash scripts/start.sh        (SYNC_BRANCH=main 이 기본. 다른 브랜치면 SYNC_BRANCH=<브랜치> bash scripts/start.sh)
#       UI_WORKER=0 bash scripts/start.sh  → UI 워커 없이 이미지만
cd "$(dirname "$0")/.." || exit 1
ROOT=$(pwd)
ok=1
UI_WORKER=${UI_WORKER:-1}
if [ "$UI_WORKER" = 1 ]; then WORKERS="codex sync ui"; else WORKERS="codex sync"; fi
NEED="git codex node python3"
SYNC_REMOTE=${SYNC_REMOTE:-origin}
SYNC_BRANCH=${SYNC_BRANCH:-main}
export SYNC_REMOTE SYNC_BRANCH
say() { printf '%s\n' "$*"; }

say "== RealCash 워커 사전 점검 ($WORKERS) =="
for c in $NEED; do
  if command -v "$c" >/dev/null 2>&1; then say "[ok] $c"; else say "[x] $c 가 없어요"; ok=0; fi
done
[ "$ok" -eq 1 ] || { say "필요한 도구를 설치한 뒤 다시 실행하세요."; exit 1; }
if [ "$UI_WORKER" = 1 ]; then
  if [ "${UI_CHECK_CMD:-}" = skip ]; then say "[!] UI_CHECK_CMD=skip — UI 작업을 검사 없이 브랜치로 올려요"
  elif command -v java >/dev/null 2>&1 && java -version >/dev/null 2>&1; then say "[ok] java (UI 검사 ${UI_CHECK_CMD:-./gradlew -q :shared:ui:jvmTest})"
  else
    say "[!] java 가 없어요 — UI 작업은 검사(./gradlew)에서 실패로 끝나요."
    say "    JDK 17 이상을 설치하거나, 검사 없이 돌리려면 UI_CHECK_CMD=skip, UI를 끄려면 UI_WORKER=0."
  fi
  [ -f docs/handoff/CODEX-UI-TICK.md ] || say "[!] docs/handoff/CODEX-UI-TICK.md 가 없어요 — UI 워커가 기다리기만 해요"
fi

. "$ROOT/scripts/lib/common.sh"

# codex exec는 git 저장소 안에서만 돈다. 동기화 워커도 클론이 필요하다.
if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  say "[x] git 저장소가 아니에요. RealCash를 git clone 한 폴더에서 실행하세요."; exit 1
fi
if ! git config user.email >/dev/null 2>&1; then
  say "[x] git 사용자 정보가 없어요. git config user.name / user.email 을 설정하세요."; exit 1
fi
if git remote get-url "$SYNC_REMOTE" >/dev/null 2>&1; then say "[ok] git $SYNC_REMOTE"
else say "[x] git 원격 $SYNC_REMOTE 이 없어요 — 클라우드 세션과 주고받을 수 없어요"; exit 1; fi
branch=$(git symbolic-ref --short HEAD 2>/dev/null)
if [ "$branch" = "$SYNC_BRANCH" ]; then say "[ok] 브랜치 $SYNC_BRANCH"
else
  say "[x] 지금 브랜치는 '$branch', 동기화 브랜치는 '$SYNC_BRANCH' 예요."
  say "    git checkout $SYNC_BRANCH 하거나, SYNC_BRANCH=$branch bash scripts/start.sh 로 실행하세요."
  exit 1
fi

say "… Codex 응답 확인 (3분 이내)"
run_with_timeout 180 codex exec --dangerously-bypass-approvals-and-sandbox -C "$ROOT" "OK 한 단어만 답하라. 파일은 만들지 마라." > "$LOG_DIR/preflight-codex.out" 2>&1 < /dev/null
if grep -Eqi '^[[:space:]]*ok[.!]?[[:space:]]*$' "$LOG_DIR/preflight-codex.out"; then say "[ok] Codex 동작"
elif looks_like_limit "$LOG_DIR/preflight-codex.out"; then say "[!] Codex 지금 한도 상태 — 워커가 풀릴 때까지 기다려요"
else say "[x] Codex 실패 (codex login 확인):"; tail -n 5 "$LOG_DIR/preflight-codex.out"; exit 1; fi

# 정지 중인 워커가 현재 작업을 마치는 중이면 STOP을 지우지 않는다.
if [ -f "$STOP_FILE" ]; then
  for w in codex sync ui; do
    pidf="$LOG_DIR/$w.pid"
    if [ -f "$pidf" ] && kill -0 "$(cat "$pidf")" 2>/dev/null; then
      say "워커가 종료 중입니다. 현재 작업이 끝난 뒤 start.sh를 다시 실행하세요."
      exit 1
    fi
  done
fi
rm -f "$STOP_FILE"
echo "$SYNC_REMOTE/$SYNC_BRANCH" > "$LOG_DIR/sync-branch"
for w in $WORKERS; do
  pidf="$LOG_DIR/$w.pid"
  if [ -f "$pidf" ] && kill -0 "$(cat "$pidf")" 2>/dev/null; then say "… $w 워커는 이미 실행 중"; continue; fi
  python3 "$ROOT/scripts/lib/launch_worker.py" "$w" || exit 1
done

say ""
say "워커가 돌기 시작했어요: $WORKERS (동기화 브랜치: $SYNC_BRANCH)."
say "- 맥은 전원에 연결하고 덮개는 열어 두세요 (덮개를 닫으면 잠들어요)."
say "- 진행 상황: bash scripts/status.sh"
say "- 멈추기: bash scripts/stop.sh   (즉시: bash scripts/stop.sh --now)"
