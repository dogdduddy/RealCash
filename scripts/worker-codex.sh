#!/usr/bin/env bash
# Codex 워커: assets/jobs/queue/의 이미지 의뢰서를 하나씩 codex exec로 처리한다.
# 결과는 assets/inbox/<id>/ 에 모이고, Claude 세션이 검수한다. git은 건드리지 않는다(동기화 워커 몫).
# 실행: bash scripts/start.sh (직접 실행하지 말 것). 설명: docs/03-gpt-image-pipeline.md
WORKER=codex
. "$(dirname "$0")/lib/common.sh"
cd "$ROOT" || exit 1

Q="$ROOT/assets/jobs/queue"; RUN="$ROOT/assets/jobs/running"
DONE="$ROOT/assets/jobs/done"; FAIL="$ROOT/assets/jobs/failed"
INBOX="$ROOT/assets/inbox"
mkdir -p "$Q" "$RUN" "$DONE" "$FAIL" "$INBOX"
BLOCKED="$ROOT/assets/jobs/blocked"
mkdir -p "$BLOCKED"

LIMIT_RETRY=${LIMIT_RETRY:-1200}         # 한도 걸렸을 때 재시도 간격 (20분)
JOB_TIMEOUT=${JOB_TIMEOUT:-1800}         # 작업 하나 최대 30분
JOB_GAP=${JOB_GAP:-60}                   # 작업 사이 간격
POLL=${POLL:-120}                        # 큐가 비었을 때 확인 간격
MAX_PER_DAY=${IMAGE_JOBS_PER_DAY:-0}     # 0 = 일일 상한 없음, 카운터는 기록용
MAX_UNREVIEWED=${IMAGE_MAX_UNREVIEWED:-4}
MAX_ATTEMPTS=${IMAGE_MAX_ATTEMPTS:-3}    # 같은 id의 실제 생성 시도, 서비스 한도 재시도 제외

for value in "$MAX_PER_DAY" "$MAX_UNREVIEWED" "$MAX_ATTEMPTS"; do
  case "$value" in ''|*[!0-9]*) log "invalid worker limit: $value"; exit 1;; esac
done
if [ "$MAX_UNREVIEWED" -lt 1 ] || [ "$MAX_ATTEMPTS" -lt 1 ]; then
  log "review and attempt limits must be positive"; exit 1
fi

# 두 번째 워커가 진행 중인 의뢰를 큐로 되돌리지 못하게 먼저 단일 실행 잠금.
lock="$LOG_DIR/codex-worker.lock"
if ! mkdir "$lock" 2>/dev/null; then
  owner=$(cat "$lock/pid" 2>/dev/null)
  case "$owner" in
    ''|*[!0-9]*) log "worker lock has no valid owner; inspect $lock"; exit 1;;
  esac
  if kill -0 "$owner" 2>/dev/null; then log "worker already running pid=$owner"; exit 0; fi
  rm -f "$lock/pid"
  rmdir "$lock" 2>/dev/null && mkdir "$lock" 2>/dev/null || exit 1
fi
echo "$$" > "$lock/pid"
cleanup() { rm -f "$lock/pid"; rmdir "$lock" 2>/dev/null; }
trap cleanup EXIT
trap 'exit 0' TERM INT

valid_id() { [[ "$1" =~ ^[a-z0-9][a-z0-9.-]*$ ]]; }

dependencies_ready() {
  local prompt=$1 dep
  for dep in $(sed -n 's/^# requires-review: //p' "$prompt"); do
    valid_id "$dep" || return 1
    [ -f "$INBOX/$dep/REVIEWED" ] &&
      grep -Eq '(^|[[:space:]])PASS([[:space:]]|$)|통과' "$INBOX/$dep/REVIEWED" || return 1
  done
  for dep in $(sed -n 's/^# requires-gate: //p' "$prompt"); do
    [[ "$dep" =~ ^[A-Z0-9-]+$ ]] || return 1
    [ -f "$ROOT/assets/jobs/gates/$dep.PASS" ] || return 1
  done
}

block_job() {
  local prompt=$1 reason=$2 base
  base=$(basename "$prompt")
  # 이전 차단 의뢰도 덮어쓰지 않는다.
  if [ -e "$BLOCKED/$base" ]; then base="$(date +%s)-$$-$base"; fi
  mv "$prompt" "$BLOCKED/$base"
  echo "$reason" > "$BLOCKED/$base.reason"
  log "job blocked $base: $reason"
}

unreviewed_count() {
  local status n=0
  for status in "$INBOX"/*/STATUS; do
    [ -f "$status" ] || continue
    [ -f "${status%/STATUS}/REVIEWED" ] || n=$((n + 1))
  done
  echo "$n"
}

expected_image_count() {
  local id=$1 f n=0 signature
  for f in "$INBOX/$id/$id-"[12].png; do
    [ -s "$f" ] || continue
    signature=$(od -An -tx1 -N8 "$f" | tr -d ' \n')
    [ "$signature" = 89504e470d0a1a0a ] && n=$((n + 1))
  done
  echo "$n"
}

# 이전에 중단된 작업은 큐로 되돌린다
for f in "$RUN"/*.prompt.md; do
  [ -e "$f" ] || continue
  if [ -e "$Q/$(basename "$f")" ]; then block_job "$f" "duplicate interrupted request"
  else mv "$f" "$Q/"; fi
done
log "worker start daily_cap=$MAX_PER_DAY max_unreviewed=$MAX_UNREVIEWED max_attempts=$MAX_ATTEMPTS"
last_hold=""

while ! stopped; do
  if [ "$(unreviewed_count)" -ge "$MAX_UNREVIEWED" ]; then
    [ "$last_hold" = review ] || log "review backlog reached $MAX_UNREVIEWED; waiting for review"
    last_hold=review; nap "$POLL"; continue
  fi
  last_hold=""
  job=""
  for candidate in "$Q"/*.prompt.md; do
    [ -f "$candidate" ] || continue
    candidate_name=$(basename "$candidate" .prompt.md)
    candidate_id=${candidate_name#*-}
    if ! [[ "$candidate_name" =~ ^[0-9][0-9]-[a-z0-9][a-z0-9.-]*$ ]]; then
      block_job "$candidate" "invalid request name"; continue
    fi
    if [ -e "$DONE/$candidate_name.prompt.md" ] || [ -e "$FAIL/$candidate_name.prompt.md" ]; then
      block_job "$candidate" "request filename already processed; use a new retry sequence"; continue
    fi
    if [ -f "$INBOX/$candidate_id/STATUS" ] || [ "$(expected_image_count "$candidate_id")" -gt 0 ]; then
      block_job "$candidate" "existing result; review and archive before explicit retry"; continue
    fi
    attempts=$(cat "$INBOX/$candidate_id/ATTEMPTS" 2>/dev/null || echo 0)
    case "$attempts" in ''|*[!0-9]*) block_job "$candidate" "invalid attempt counter"; continue;; esac
    if [ "$attempts" -ge "$MAX_ATTEMPTS" ]; then
      block_job "$candidate" "attempt limit $MAX_ATTEMPTS reached"; continue
    fi
    dependencies_ready "$candidate" || continue
    job=$candidate; break
  done
  if [ -z "$job" ]; then nap "$POLL"; continue; fi

  countfile="$LOG_DIR/codex-count-$(date +%F)"
  count=$(cat "$countfile" 2>/dev/null || echo 0)
  if [ "$MAX_PER_DAY" -gt 0 ] && [ "$count" -ge "$MAX_PER_DAY" ]; then
    log "daily cap $MAX_PER_DAY reached"; nap 1800; continue
  fi

  name=$(basename "$job" .prompt.md)     # 예: 01-char.base.candidate-a
  id=${name#*-}                           # 앞의 순서 번호 제거
  mv "$job" "$RUN/$name.prompt.md"
  mkdir -p "$INBOX/$id"
  attempts=$(cat "$INBOX/$id/ATTEMPTS" 2>/dev/null || echo 0)
  out="$INBOX/$id/codex.log"
  log "job start $id"
  start=$(date +%s)
  run_with_timeout "$JOB_TIMEOUT" \
    codex exec --dangerously-bypass-approvals-and-sandbox -C "$ROOT" \
      "$(cat "$RUN/$name.prompt.md")" \
      > "$out" 2>&1 < /dev/null
  rc=$?
  dur=$(( $(date +%s) - start ))

  # 다른 채팅의 생성물이나 attempt-N/의 이전 이미지를 성공으로 오인하지 않는다.
  # 의뢰서가 지정한 inbox 최상위의 id-1.png/id-2.png만 받는다.
  n=$(expected_image_count "$id")

  if [ "$n" -eq 0 ] &&
    { looks_like_limit "$out" || tail -n 40 "$out" | grep -Eiq 'session limit'; } &&
    { [ "$rc" -ne 0 ] || [ "$dur" -lt 120 ]; }; then
    log "limit on $id → back to queue, retry in ${LIMIT_RETRY}s"
    mv "$RUN/$name.prompt.md" "$Q/"
    mv "$out" "$INBOX/$id/codex-limit-$(date +%H%M%S)-$$.log"
    nap "$LIMIT_RETRY"
    continue
  fi

  echo $((attempts + 1)) > "$INBOX/$id/ATTEMPTS"
  echo $((count + 1)) > "$countfile"
  if [ "$n" -gt 0 ]; then
    mv "$RUN/$name.prompt.md" "$DONE/"
    echo "done $(date '+%F %T') images=$n rc=$rc ${dur}s" > "$INBOX/$id/STATUS"
    log "job done $id ($n images, ${dur}s)"
  else
    mv "$RUN/$name.prompt.md" "$FAIL/"
    echo "failed $(date '+%F %T') rc=$rc ${dur}s" > "$INBOX/$id/STATUS"
    log "job failed $id rc=$rc"
  fi
  nap "$JOB_GAP"
done
log "worker stop"
