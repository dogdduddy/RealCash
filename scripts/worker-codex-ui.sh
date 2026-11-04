#!/usr/bin/env bash
# Codex UI 워커: codex/ui/queue/의 UI 의뢰서를 하나씩 codex exec로 처리한다.
# Codex는 워커 전용 git worktree(기본 scripts/log/ui-worktree)에서 일하고, 허용 경로(shared/ui/, assets/img/)만
# 바뀌었고 검사(UI_CHECK_CMD)가 통과하면 워커가 codex/ui/<id> 브랜치로 커밋해 push한다.
# 결과 기록(STATUS 등)은 이 클론의 codex/ui/inbox/<id>/에 쓰고, 동기화 워커가 동기화 브랜치로 올린다.
# 메인 클론의 작업 트리와 브랜치는 건드리지 않는다(동기화 워커·이미지 워커와 동시에 돌아도 안전하게).
# 실행: bash scripts/start.sh (직접 실행하지 말 것). 설명: docs/03-gpt-image-pipeline.md
WORKER=ui
. "$(dirname "$0")/lib/common.sh"
cd "$ROOT" || exit 1

UI="$ROOT/codex/ui"
Q="$UI/queue"; RUN="$UI/running"; DONE="$UI/done"; FAIL="$UI/failed"; BLOCKED="$UI/blocked"
INBOX="$UI/inbox"
mkdir -p "$Q" "$RUN" "$DONE" "$FAIL" "$BLOCKED" "$INBOX"
TICK_PROMPT="$ROOT/docs/handoff/CODEX-UI-TICK.md"

SYNC_REMOTE=${SYNC_REMOTE:-origin}
SYNC_BRANCH=${SYNC_BRANCH:-main}
WT=${UI_WORKTREE:-$LOG_DIR/ui-worktree}
LIMIT_RETRY=${LIMIT_RETRY:-1200}             # 한도 걸렸을 때 재시도 간격 (20분)
UI_JOB_TIMEOUT=${UI_JOB_TIMEOUT:-3600}       # Codex 실행 하나 최대 60분
UI_CHECK_TIMEOUT=${UI_CHECK_TIMEOUT:-1800}   # 검사 명령 최대 30분
UI_CHECK_CMD=${UI_CHECK_CMD:-./gradlew -q :shared:ui:jvmTest}   # skip = 검사 생략
UI_WAIT_FOR_IMAGES=${UI_WAIT_FOR_IMAGES:-1}  # 이미지 의뢰가 큐/실행 중이면 UI는 기다린다
UI_JOB_GAP=${UI_JOB_GAP:-60}
POLL=${POLL:-120}
MAX_UNREVIEWED=${UI_MAX_UNREVIEWED:-2}       # 미검수 UI 결과가 이만큼이면 대기 (브랜치 충돌 방지)
MAX_ATTEMPTS=${UI_MAX_ATTEMPTS:-3}           # 같은 id의 실제 시도, 서비스 한도 재시도 제외
UI_ONCE=${UI_ONCE:-0}                        # 테스트용: 한 바퀴만 돌고 끝낸다

for value in "$MAX_UNREVIEWED" "$MAX_ATTEMPTS" "$UI_JOB_TIMEOUT" "$UI_CHECK_TIMEOUT"; do
  case "$value" in ''|*[!0-9]*) log "invalid worker limit: $value"; exit 1;; esac
done
if [ "$MAX_UNREVIEWED" -lt 1 ] || [ "$MAX_ATTEMPTS" -lt 1 ]; then
  log "review and attempt limits must be positive"; exit 1
fi

# 단일 실행 잠금 (두 번째 워커가 진행 중인 의뢰를 큐로 되돌리지 못하게)
lock="$LOG_DIR/codex-ui-worker.lock"
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

ONCE_DONE=""
pause() { if [ "$UI_ONCE" = 1 ]; then ONCE_DONE=1; else nap "$1"; fi; }

valid_id() { [[ "$1" =~ ^[a-z0-9][a-z0-9.-]*$ ]]; }

review_passed() { # REVIEWED가 PASS인지. UI id와 이미지 id 모두 찾는다.
  local f
  for f in "$INBOX/$1/REVIEWED" "$ROOT/assets/inbox/$1/REVIEWED"; do
    [ -f "$f" ] && grep -Eq '(^|[[:space:]])PASS([[:space:]]|$)|통과' "$f" && return 0
  done
  return 1
}

dependencies_ready() {
  local prompt=$1 dep
  for dep in $(sed -n 's/^# requires-review: //p' "$prompt"); do
    valid_id "$dep" || return 1
    review_passed "$dep" || return 1
  done
  for dep in $(sed -n 's/^# requires-gate: //p' "$prompt"); do
    [[ "$dep" =~ ^[A-Z0-9-]+$ ]] || return 1
    [ -f "$ROOT/assets/jobs/gates/$dep.PASS" ] || return 1
  done
}

block_job() {
  local prompt=$1 reason=$2 base
  base=$(basename "$prompt")
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

image_work_pending() {
  local f
  for f in "$ROOT"/assets/jobs/queue/*.prompt.md "$ROOT"/assets/jobs/running/*.prompt.md; do
    [ -e "$f" ] && return 0
  done
  return 1
}

wt_git() { git -C "$WT" -c core.quotePath=false "$@"; }

# 바뀐 경로 목록: HEAD 대비 추적 파일 변경 + 무시되지 않은 새 파일. 빌드 산출물(.gitignore)은 제외.
list_changes() { # $1 = 전체 목록 파일, $2 = 새 파일 목록 파일
  wt_git ls-files --others --exclude-standard > "$2"
  { wt_git diff --name-only --no-renames HEAD --; cat "$2"; } | sed '/^$/d' | sort -u > "$1"
}

# worktree 변경을 패치로 남긴다 (새 파일 포함)
save_patch() { # $1 = 출력 파일, $2 = 새 파일 목록
  local p
  while IFS= read -r p; do
    [ -n "$p" ] && wt_git add -N -- "$p" 2>/dev/null
  done < "$2"
  wt_git diff --binary --no-renames HEAD > "$1"
}

# worker 전용 worktree를 HEAD로 되돌린다. git clean은 쓰지 않고, 목록에 있는 새 파일만 지운다.
restore_worktree() { # $1 = 새 파일 목록
  local p
  wt_git reset -q --hard HEAD
  while IFS= read -r p; do
    [ -n "$p" ] && rm -f -- "$WT/$p"
  done < "$1"
}

wt_clean() { [ -z "$(wt_git status --porcelain --untracked-files=all)" ]; }

ensure_worktree() {
  if [ -e "$WT/.git" ] && git -C "$WT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then return 0; fi
  # 클론 폴더를 옮기면 worktree 연결이 끊긴다. 한 번 고쳐 본다 (git 2.30+).
  if [ -e "$WT/.git" ] && git worktree repair "$WT" >/dev/null 2>&1 &&
    git -C "$WT" rev-parse --is-inside-work-tree >/dev/null 2>&1; then
    log "worktree repaired at $WT"; return 0
  fi
  if [ -e "$WT" ] && [ -n "$(ls -A "$WT" 2>/dev/null)" ]; then
    log "worktree path $WT exists but is not a git worktree; inspect it"; return 1
  fi
  git worktree prune >/dev/null 2>&1
  mkdir -p "$(dirname "$WT")"
  if git worktree add -q --detach "$WT" "$BASE_REF" >> "$LOG_DIR/ui-git-last.out" 2>&1; then
    log "worktree created at $WT"
  else
    log "worktree add failed (see scripts/log/ui-git-last.out)"; return 1
  fi
}

summary_of() { # 의뢰서 [요청] 다음의 첫 내용 줄, 60자 이내
  python3 - "$1" <<'PY'
import sys
lines = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
out = ""
for i, line in enumerate(lines):
    s = line.strip()
    if s.startswith("[요청]"):
        rest = s[len("[요청]"):].strip()
        if not rest:
            rest = next((m.strip() for m in lines[i + 1:] if m.strip()), "")
        out = rest.lstrip("-*• ").strip()
        break
print(" ".join(out.split())[:60])
PY
}

finish_job() { # $1 = done|failed, $2 = 의뢰서 이름, $3 = STATUS 첫 줄, [$4 = 덧붙일 파일]
  local result=$1 name=$2 line=$3 extra=${4:-} id=${2#*-}
  echo $((attempts + 1)) > "$INBOX/$id/ATTEMPTS"
  echo $((count + 1)) > "$countfile"
  { echo "$line"; [ -n "$extra" ] && [ -f "$extra" ] && tail -n 30 "$extra"; } > "$INBOX/$id/STATUS"
  if [ "$result" = done ]; then mv "$RUN/$name.prompt.md" "$DONE/"; else mv "$RUN/$name.prompt.md" "$FAIL/"; fi
  echo "$(date '+%F %T') $id $result ${branch:-}" > "$LOG_DIR/ui-last"
  log "job $result $id: $line"
}

# 실패: 변경을 failed.patch로 남기고 worktree를 되돌린 뒤 기록한다 ($id, $name, $tmp_new는 현재 작업 것)
fail_with_patch() { # $1 = STATUS 첫 줄, [$2 = 덧붙일 파일]
  save_patch "$INBOX/$id/failed.patch" "$tmp_new"
  restore_worktree "$tmp_new"
  finish_job failed "$name" "$1" "${2:-}"
}

# 이전에 중단된 의뢰는 큐로 되돌린다
for f in "$RUN"/*.prompt.md; do
  [ -e "$f" ] || continue
  if [ -e "$Q/$(basename "$f")" ]; then block_job "$f" "duplicate interrupted request"
  else mv "$f" "$Q/"; fi
done
BASE_REF="$SYNC_REMOTE/$SYNC_BRANCH"
log "worker start base=$BASE_REF worktree=$WT max_unreviewed=$MAX_UNREVIEWED max_attempts=$MAX_ATTEMPTS check='$UI_CHECK_CMD'"
last_hold=""
hold() { # 같은 대기 사유는 한 번만 기록
  [ "$last_hold" = "$1" ] || log "$2"
  last_hold=$1; pause "$POLL"
}

while ! stopped && [ -z "$ONCE_DONE" ]; do
  if [ "$UI_WAIT_FOR_IMAGES" = 1 ] && image_work_pending; then
    hold images "image jobs pending; UI waits (images first)"; continue
  fi
  if [ "$(unreviewed_count)" -ge "$MAX_UNREVIEWED" ]; then
    hold review "UI review backlog reached $MAX_UNREVIEWED; waiting for review"; continue
  fi
  branch_now=$(git symbolic-ref --short HEAD 2>/dev/null)
  if [ "$branch_now" != "$SYNC_BRANCH" ]; then
    hold branch "clone is on '$branch_now', not $SYNC_BRANCH; waiting"; continue
  fi
  if ! git rev-parse --verify -q "$BASE_REF^{commit}" >/dev/null; then
    hold base "base $BASE_REF not found (has the sync worker pulled?); waiting"; continue
  fi
  if [ ! -f "$TICK_PROMPT" ]; then
    hold prompt "standing prompt docs/handoff/CODEX-UI-TICK.md missing; waiting"; continue
  fi

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
    if [ -f "$INBOX/$candidate_id/STATUS" ]; then
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
  if [ -z "$job" ]; then last_hold=""; pause "$POLL"; continue; fi
  last_hold=""

  # worker 전용 worktree를 동기화 브랜치의 원격 최신 커밋으로 맞춘다
  ensure_worktree || { pause "$POLL"; continue; }
  tmp_all="$LOG_DIR/ui-changes.txt"; tmp_new="$LOG_DIR/ui-new.txt"
  if ! wt_clean; then
    list_changes "$tmp_all" "$tmp_new"
    leftover="$LOG_DIR/ui-leftover-$(date +%Y%m%d-%H%M%S).patch"
    save_patch "$leftover" "$tmp_new"
    restore_worktree "$tmp_new"
    log "worktree had leftover changes (interrupted job?); saved to $leftover and restored"
  fi
  if ! wt_git checkout -q --detach "$BASE_REF" >> "$LOG_DIR/ui-git-last.out" 2>&1 || ! wt_clean; then
    log "could not prepare worktree at $BASE_REF (see scripts/log/ui-git-last.out)"; pause "$POLL"; continue
  fi
  base=$(wt_git rev-parse HEAD)

  name=$(basename "$job" .prompt.md)
  id=${name#*-}
  mv "$job" "$RUN/$name.prompt.md"
  mkdir -p "$INBOX/$id"
  attempts=$(cat "$INBOX/$id/ATTEMPTS" 2>/dev/null || echo 0)
  countfile="$LOG_DIR/ui-count-$(date +%F)"
  count=$(cat "$countfile" 2>/dev/null || echo 0)
  branch=""
  out="$INBOX/$id/codex.log"
  rm -f "$INBOX/$id/failed.patch" "$INBOX/$id/DIFFSTAT" "$INBOX/$id/check.log"
  log "job start $id base=$base"
  start=$(date +%s)
  run_with_timeout "$UI_JOB_TIMEOUT" \
    codex exec --dangerously-bypass-approvals-and-sandbox -C "$WT" \
      "$(cat "$TICK_PROMPT"; echo; cat "$RUN/$name.prompt.md")" \
      > "$out" 2>&1 < /dev/null
  rc=$?
  dur=$(( $(date +%s) - start ))

  # Codex가 규칙을 어기고 커밋했어도 변경은 작업 트리로 되돌려 같은 검사를 받게 한다
  if [ "$(wt_git rev-parse HEAD 2>/dev/null)" != "$base" ]; then
    log "codex moved HEAD in $id; folding its commits back into the working tree"
    wt_git update-ref --no-deref HEAD "$base"
  fi
  wt_git reset -q 2>/dev/null   # 인덱스를 HEAD로 (작업 트리는 그대로)
  list_changes "$tmp_all" "$tmp_new"
  nchanged=$(wc -l < "$tmp_all" | tr -d ' ')

  if looks_like_limit "$out" && { [ "$rc" -ne 0 ] || { [ "$nchanged" -eq 0 ] && [ "$dur" -lt 120 ]; }; }; then
    stamp=$(date +%H%M%S)-$$
    if [ "$nchanged" -gt 0 ]; then
      save_patch "$INBOX/$id/limit-$stamp.patch" "$tmp_new"
      restore_worktree "$tmp_new"
    fi
    log "limit on $id → back to queue, retry in ${LIMIT_RETRY}s"
    mv "$RUN/$name.prompt.md" "$Q/"
    mv "$out" "$INBOX/$id/codex-limit-$stamp.log"
    pause "$LIMIT_RETRY"
    continue
  fi

  if [ "$rc" -ne 0 ]; then
    if [ "$nchanged" -gt 0 ]; then fail_with_patch "failed $(date '+%F %T') codex exited rc=$rc ${dur}s"
    else finish_job failed "$name" "failed $(date '+%F %T') codex exited rc=$rc ${dur}s"; fi
    pause "$UI_JOB_GAP"; continue
  fi
  if [ "$nchanged" -eq 0 ]; then
    finish_job failed "$name" "failed $(date '+%F %T') no changes ${dur}s"
    pause "$UI_JOB_GAP"; continue
  fi
  outside=""
  while IFS= read -r p; do
    case "$p" in shared/ui/*|assets/img/*) ;; *) outside="$outside $p";; esac
  done < "$tmp_all"
  if [ -n "$outside" ]; then
    fail_with_patch "failed $(date '+%F %T') touched outside allowed paths:$outside"
    pause "$UI_JOB_GAP"; continue
  fi

  check=skipped
  if [ "$UI_CHECK_CMD" != skip ]; then
    log "check start $id: $UI_CHECK_CMD"
    ( cd "$WT" && run_with_timeout "$UI_CHECK_TIMEOUT" bash -c "$UI_CHECK_CMD" ) \
      > "$INBOX/$id/check.log" 2>&1 < /dev/null
    crc=$?
    if [ "$crc" -ne 0 ]; then
      fail_with_patch "failed $(date '+%F %T') check failed rc=$crc ($UI_CHECK_CMD), last lines:" "$INBOX/$id/check.log"
      pause "$UI_JOB_GAP"; continue
    fi
    check=ok
  fi

  # 커밋 → push. 재시도(두 번째 시도부터)는 브랜치 이름에 -r<시도>를 붙여 이전 브랜치를 덮지 않는다.
  n=$((attempts + 1))
  branch="codex/ui/$id"
  [ "$n" -gt 1 ] && branch="codex/ui/$id-r$n"
  summary=$(summary_of "$RUN/$name.prompt.md")
  [ -n "$summary" ] || summary="UI 작업"
  paths=""
  for p in shared/ui assets/img; do [ -e "$WT/$p" ] && paths="$paths $p"; done
  # shellcheck disable=SC2086
  if ! wt_git checkout -q -b "$branch" >> "$LOG_DIR/ui-git-last.out" 2>&1 ||
    ! wt_git add -A -- $paths >> "$LOG_DIR/ui-git-last.out" 2>&1 ||
    ! wt_git commit -q -m "[codex-ui] $id: $summary" >> "$LOG_DIR/ui-git-last.out" 2>&1; then
    wt_git checkout -q --detach "$base" 2>/dev/null
    wt_git branch -D "$branch" >/dev/null 2>&1
    fail_with_patch "failed $(date '+%F %T') could not commit on $branch (see scripts/log/ui-git-last.out)"
    pause "$UI_JOB_GAP"; continue
  fi
  sha=$(wt_git rev-parse --short HEAD)
  wt_git show --stat --format='%H %s' HEAD > "$INBOX/$id/DIFFSTAT"
  pushed=0
  for try in 1 2; do
    if wt_git push -q -u "$SYNC_REMOTE" "$branch" >> "$LOG_DIR/ui-git-last.out" 2>&1; then pushed=1; break; fi
    [ "$try" = 1 ] && sleep 5
  done
  # 메인 클론과 같은 커밋 기준으로 되돌린다 (worktree는 분리된 HEAD로). 로컬 브랜치는 남긴다.
  wt_git checkout -q --detach "$base" >> "$LOG_DIR/ui-git-last.out" 2>&1
  list_changes "$tmp_all" "$tmp_new"
  if [ -s "$tmp_all" ]; then
    leftover="$LOG_DIR/ui-leftover-$(date +%Y%m%d-%H%M%S).patch"
    save_patch "$leftover" "$tmp_new"; restore_worktree "$tmp_new"
    log "check left files outside the commit; saved to $leftover and restored"
  fi
  if [ "$pushed" = 1 ]; then
    finish_job done "$name" "done $(date '+%F %T') branch=$branch commit=$sha check=$check ${dur}s"
  else
    finish_job failed "$name" "failed $(date '+%F %T') push failed branch=$branch commit=$sha (local branch kept)"
  fi
  pause "$UI_JOB_GAP"
done
log "worker stop"
