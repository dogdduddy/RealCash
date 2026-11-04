#!/usr/bin/env bash
# 동기화 워커: 주기적으로 이미지 작업 결과(assets/inbox, assets/jobs)를 커밋해 GitHub에 올리고,
# 클라우드 Claude 세션이 올린 의뢰서·검수 결과를 받아온다. 이미지 경로 밖의 파일은 커밋하지 않는다.
# 실행: bash scripts/start.sh (직접 실행하지 말 것). 설명: docs/03-gpt-image-pipeline.md
#
# 환경 변수
#   SYNC_INTERVAL  주기(초), 기본 300
#   SYNC_REMOTE    원격 이름, 기본 origin
#   SYNC_BRANCH    주고받을 브랜치, 기본 main. 이 클론이 그 브랜치에 체크아웃되어 있어야 한다.
WORKER=sync
. "$(dirname "$0")/lib/common.sh"
cd "$ROOT" || exit 1

SYNC_INTERVAL=${SYNC_INTERVAL:-300}
SYNC_REMOTE=${SYNC_REMOTE:-origin}
SYNC_BRANCH=${SYNC_BRANCH:-main}
case "$SYNC_INTERVAL" in ''|*[!0-9]*) log "invalid interval: $SYNC_INTERVAL"; exit 1;; esac

lock="$LOG_DIR/sync-worker.lock"
if ! mkdir "$lock" 2>/dev/null; then
  owner=$(cat "$lock/pid" 2>/dev/null)
  if [ -n "$owner" ] && kill -0 "$owner" 2>/dev/null; then log "worker already running pid=$owner"; exit 0; fi
  rm -f "$lock/pid"
  rmdir "$lock" 2>/dev/null && mkdir "$lock" 2>/dev/null || exit 1
fi
echo "$$" > "$lock/pid"
cleanup() { rm -f "$lock/pid"; rmdir "$lock" 2>/dev/null; }
trap cleanup EXIT
trap 'exit 0' TERM INT

job_running() { ls "$ROOT"/assets/jobs/running/*.prompt.md >/dev/null 2>&1; }

sync_once() {
  local branch git_dir
  branch=$(git symbolic-ref --short HEAD 2>/dev/null)
  if [ "$branch" != "$SYNC_BRANCH" ]; then log "not on $SYNC_BRANCH ($branch); skip"; return 1; fi
  git_dir=$(git rev-parse --git-dir 2>/dev/null) || { log "not a git repository; skip"; return 1; }
  if [ -d "$git_dir/rebase-merge" ] || [ -d "$git_dir/rebase-apply" ] || [ -f "$git_dir/MERGE_HEAD" ]; then
    log "rebase/merge in progress; skip"; return 1
  fi
  # 생성 중인 결과는 반쪽으로 올리지 않는다. 받기만 한다.
  if ! job_running; then
    git add -A -- assets/inbox assets/jobs
    if ! git diff --cached --quiet -- assets/inbox assets/jobs; then
      git commit -q -m "[sync] 이미지 작업 결과" -- assets/inbox assets/jobs || { log "commit failed"; return 1; }
      log "committed image results"
    fi
  fi
  if ! git pull -q --rebase --autostash "$SYNC_REMOTE" "$SYNC_BRANCH" >> "$LOG_DIR/sync-last.out" 2>&1; then
    git rebase --abort >/dev/null 2>&1
    log "pull failed (see scripts/log/sync-last.out)"
    notify "동기화 실패: git pull — scripts/log/sync-last.out"
    return 1
  fi
  if [ -n "$(git rev-list "$SYNC_REMOTE/$SYNC_BRANCH..HEAD" 2>/dev/null)" ]; then
    if git push -q "$SYNC_REMOTE" "HEAD:$SYNC_BRANCH" >> "$LOG_DIR/sync-last.out" 2>&1; then
      log "pushed"
    else
      log "push failed (see scripts/log/sync-last.out)"; return 1
    fi
  fi
}

# 테스트용: SYNC_ONCE=1이면 한 번만 돌고 끝난다.
if [ "${SYNC_ONCE:-0}" = 1 ]; then sync_once; exit $?; fi

log "worker start interval=${SYNC_INTERVAL}s remote=$SYNC_REMOTE branch=$SYNC_BRANCH"
while ! stopped; do
  sync_once
  nap "$SYNC_INTERVAL"
done
log "worker stop"
