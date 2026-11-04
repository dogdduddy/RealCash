# RealCash 이미지 워커 공통 함수 (macOS 기본 bash 3.2 호환)
# 이 파일과 scripts/ 아래 파일은 Claude 세션이 수정하지 않는다. 사람이 고친다.
#
# 런타임 파일(모두 scripts/.gitignore로 제외):
#   scripts/STOP                 있으면 워커가 현재 작업을 마치고 멈춘다
#   scripts/log/runner-날짜.log  워커 기록
#   scripts/log/*.pid, *.lock/   pid와 단일 실행 잠금
#   scripts/log/codex-count-날짜 날짜별 생성 횟수 (기록용)
#   scripts/log/sync-last.out    마지막 git pull/push 출력

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
LOOP_DIR="$ROOT/scripts"
LOG_DIR="$LOOP_DIR/log"
STOP_FILE="$LOOP_DIR/STOP"
mkdir -p "$LOG_DIR"

NAP_CHUNK=${NAP_CHUNK:-30}

log() {
  echo "[$(date '+%F %T')] [${WORKER:-?}] $*" >> "$LOG_DIR/runner-$(date +%F).log"
}

stopped() { [ -f "$STOP_FILE" ]; }

# STOP 파일을 확인하며 잘게 나눠 잔다
nap() {
  local s=$1
  while [ "$s" -gt 0 ] && ! stopped; do
    if [ "$s" -lt "$NAP_CHUNK" ]; then sleep "$s"; s=0; else sleep "$NAP_CHUNK"; s=$((s - NAP_CHUNK)); fi
  done
}

# 사용: run_with_timeout 초 명령...   (macOS에는 timeout 명령이 없어서 직접 구현)
run_with_timeout() {
  local t=$1; shift
  "$@" &
  local pid=$!
  ( sleep "$t"; kill -TERM "$pid" 2>/dev/null; sleep 15; kill -KILL "$pid" 2>/dev/null ) >/dev/null 2>&1 &
  local watcher=$!
  wait "$pid"
  local rc=$?
  kill "$watcher" 2>/dev/null
  wait "$watcher" 2>/dev/null
  return $rc
}

# 사용 한도·일시적 과부하 메시지인지 (파일 끝부분만 본다)
looks_like_limit() {
  tail -n 40 "$1" 2>/dev/null | grep -Eiq \
    'session limit|usage limit|limit reached|limit has been reached|hit your (usage |session )?limit|rate.?limit|resets? (at|in)|try again (at|in|later)|quota|too many requests|429|overloaded|529'
}

# 맥 알림: 12:00~22:59 사이에만 띄운다 (자는 동안 깨우지 않기). 맥이 아니면 아무것도 안 한다.
notify() {
  local h
  h=$(date +%H); h=${h#0}
  if [ "$h" -ge 12 ] && [ "$h" -lt 23 ] && command -v osascript >/dev/null 2>&1; then
    osascript -e 'on run argv' -e 'display notification (item 1 of argv) with title "RealCash"' -e 'end run' \
      "$1" >/dev/null 2>&1
  fi
}
