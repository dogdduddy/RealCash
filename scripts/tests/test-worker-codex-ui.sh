#!/usr/bin/env bash
# scripts/worker-codex-ui.sh: 임시 bare 원격 + 맥 클론에서 가짜 codex로 한 바퀴씩(UI_ONCE=1) 돌려 본다.
. "$(dirname "$0")/lib.sh"
TMP=$(mktemp -d "${TMPDIR:-/tmp}/realcash-ui.XXXXXX")
trap 'rm -rf "$TMP"' EXIT

git init -q --bare "$TMP/remote.git"
git -C "$TMP/remote.git" symbolic-ref HEAD refs/heads/main
git init -q "$TMP/seed"
(
  cd "$TMP/seed" || exit 1
  git checkout -q -b main
  mkdir -p shared/ui/src shared/domain docs/handoff scripts/lib assets/img assets/jobs/queue assets/jobs/running \
    codex/ui/queue codex/ui/running codex/ui/inbox codex/ui/hold
  echo 'fun screen() = "지갑"' > shared/ui/src/Wallet.kt
  echo 'domain' > shared/domain/Model.kt
  echo 'include(":shared:ui")' > settings.gradle.kts
  : > assets/img/.gitkeep; : > assets/jobs/queue/.gitkeep; : > assets/jobs/running/.gitkeep
  : > codex/ui/queue/.gitkeep; : > codex/ui/running/.gitkeep; : > codex/ui/inbox/.gitkeep
  cp "$SRC/codex/.gitignore" codex/.gitignore
  cp "$SRC/scripts/.gitignore" scripts/.gitignore
  cp "$SRC/docs/handoff/CODEX-UI-TICK.md" docs/handoff/
  cp "$SRC/scripts/lib/common.sh" scripts/lib/
  cp "$SRC/scripts/worker-codex-ui.sh" scripts/
  printf 'build/\n*.log\n' > .gitignore
  git add -A && git commit -qm init && git remote add origin "$TMP/remote.git" && git push -q origin main
) || { bad "seed repo"; finish; exit 1; }
git clone -q "$TMP/remote.git" "$TMP/mac"
MAC="$TMP/mac"
WT="$MAC/scripts/log/ui-worktree"

# 가짜 codex: -C 폴더로 들어가 시나리오 파일대로 행동한다. 호출은 calls 파일에 남긴다.
mkdir -p "$TMP/bin"
cat > "$TMP/bin/codex" <<'FAKE'
#!/usr/bin/env bash
dir=.
while [ $# -gt 0 ]; do
  case "$1" in -C) dir=$2; shift 2;; *) prompt=$1; shift;; esac
done
echo "call" >> "$FAKE_CALLS"
case "$prompt" in *"Codex UI 작업 상시 지시"*) ;; *) echo "standing prompt missing"; exit 9;; esac
cd "$dir" || exit 8
case "$(cat "$FAKE_SCENARIO")" in
  inside)
    echo 'fun screen() = "지갑 홈"' > shared/ui/src/Wallet.kt
    echo 'fun bill() = 10000' > shared/ui/src/Bill.kt
    mkdir -p build && echo junk > build/out.txt   # 무시되는 산출물은 문제 아님
    echo "changed shared/ui";;
  outside)
    echo 'fun screen() = "x"' > shared/ui/src/Wallet.kt
    echo 'evil' > docs/evil.md
    echo 'broken' >> settings.gradle.kts
    echo 'changed' > shared/domain/Model.kt;;
  commit)
    echo 'fun c() = 1' > shared/ui/src/C.kt
    git add -A >/dev/null 2>&1 && git commit -qm "codex commit" >/dev/null 2>&1;;
  none) echo "nothing to do";;
  limit) echo "You've hit your usage limit. Try again in 20 minutes."; exit 1;;
esac
exit 0
FAKE
chmod +x "$TMP/bin/codex"
export FAKE_CALLS="$TMP/calls" FAKE_SCENARIO="$TMP/scenario"

run_ui() { # 환경 변수를 앞에 붙여 한 바퀴
  (cd "$MAC" && env PATH="$TMP/bin:$PATH" UI_ONCE=1 UI_CHECK_CMD="${CHECK:-true}" UI_MAX_UNREVIEWED=9 "$@" bash scripts/worker-codex-ui.sh)
}
job() { # job NN id [헤더]
  printf '%s[요청]\n- %s 화면 다듬기\n[완료 기준] 테스트 통과\n' "${3:-}" "$2" > "$MAC/codex/ui/queue/$1-$2.prompt.md"
}
status_of() { head -n 1 "$MAC/codex/ui/inbox/$1/STATUS" 2>/dev/null; }
calls() { [ -f "$FAKE_CALLS" ] && wc -l < "$FAKE_CALLS" | tr -d ' ' || echo 0; }
wt_clean() { [ -z "$(git -C "$WT" status --porcelain --untracked-files=all)" ]; }

# 1) shared/ui 안 수정 → 브랜치 push, STATUS done, DIFFSTAT, worktree 깨끗, 메인 클론은 main 그대로
echo inside > "$TMP/scenario"
job 01 ui.wallet
CHECK='test -f shared/ui/src/Bill.kt' run_ui
st=$(status_of ui.wallet)
check "1 STATUS done ($st)" eval 'case "$st" in "done "*"branch=codex/ui/ui.wallet commit="*" check=ok "*) true;; *) false;; esac'
check "1 원격에 codex/ui/ui.wallet 브랜치" eval 'git -C "$TMP/remote.git" rev-parse -q --verify refs/heads/codex/ui/ui.wallet >/dev/null'
check "1 커밋 메시지는 [요청] 다음 줄" [ "$(git -C "$TMP/remote.git" log -1 --format=%s codex/ui/ui.wallet)" = "[codex-ui] ui.wallet: ui.wallet 화면 다듬기" ]
check "1 커밋에 새 파일 포함" eval 'git -C "$TMP/remote.git" ls-tree -r --name-only codex/ui/ui.wallet | grep -q "^shared/ui/src/Bill.kt$"'
check "1 빌드 산출물은 커밋 안 함" eval '! git -C "$TMP/remote.git" ls-tree -r --name-only codex/ui/ui.wallet | grep -q "^build/"'
check "1 DIFFSTAT에 변경 파일" grep -q 'shared/ui/src/Bill.kt' "$MAC/codex/ui/inbox/ui.wallet/DIFFSTAT"
check "1 ATTEMPTS=1" [ "$(cat "$MAC/codex/ui/inbox/ui.wallet/ATTEMPTS")" = 1 ]
check "1 의뢰서는 done/" [ -f "$MAC/codex/ui/done/01-ui.wallet.prompt.md" ]
check "1 worktree 깨끗" wt_clean
check "1 메인 클론은 main, 작업 트리에 코드 변경 없음" eval '[ "$(git -C "$MAC" symbolic-ref --short HEAD)" = main ] && [ -z "$(git -C "$MAC" status --porcelain -- shared settings.gradle.kts)" ]'
check "1 원격 main은 그대로" [ "$(git -C "$TMP/remote.git" log -1 --format=%s main)" = init ]

# 2) 허용 경로 밖 수정 → 실패, 패치 보관, 전부 되돌림
echo outside > "$TMP/scenario"
job 02 ui.outside
run_ui
st=$(status_of ui.outside)
check "2 STATUS 실패 이유 ($st)" eval 'case "$st" in "failed "*"touched outside allowed paths:"*docs/evil.md*settings.gradle.kts*shared/domain/Model.kt*) true;; *) false;; esac'
check "2 failed.patch에 바깥 변경 포함" grep -q 'docs/evil.md' "$MAC/codex/ui/inbox/ui.outside/failed.patch"
check "2 worktree 깨끗 (새 파일도 지움)" eval 'wt_clean && [ ! -e "$WT/docs/evil.md" ]'
check "2 원격 브랜치 없음" eval '! git -C "$TMP/remote.git" rev-parse -q --verify refs/heads/codex/ui/ui.outside >/dev/null'
check "2 의뢰서는 failed/" [ -f "$MAC/codex/ui/failed/02-ui.outside.prompt.md" ]

# 3) 변경 없음 → 실패
echo none > "$TMP/scenario"
job 03 ui.none
run_ui
check "3 no changes" eval 'case "$(status_of ui.none)" in "failed "*"no changes"*) true;; *) false;; esac'

# 4) 검사 실패 → 실패, 패치 보관, 출력 끝부분이 STATUS에
echo inside > "$TMP/scenario"
job 04 ui.check
CHECK='echo boom-check-output; exit 3' run_ui
check "4 check failed rc=3" eval 'case "$(status_of ui.check)" in "failed "*"check failed rc=3"*) true;; *) false;; esac'
check "4 검사 출력이 STATUS에" grep -q 'boom-check-output' "$MAC/codex/ui/inbox/ui.check/STATUS"
check "4 failed.patch 보관" grep -q 'Bill.kt' "$MAC/codex/ui/inbox/ui.check/failed.patch"
check "4 worktree 깨끗" wt_clean
check "4 원격 브랜치 없음" eval '! git -C "$TMP/remote.git" rev-parse -q --verify refs/heads/codex/ui/ui.check >/dev/null'

# 5) 이미지 의뢰가 있으면 기다린다 (codex 호출 없음)
echo inside > "$TMP/scenario"
job 05 ui.wait
echo 'img' > "$MAC/assets/jobs/queue/00-img.a.prompt.md"
before=$(calls)
run_ui
check "5 이미지 대기 중 codex 호출 없음" [ "$(calls)" = "$before" ]
check "5 UI 의뢰서는 queue에 그대로" [ -f "$MAC/codex/ui/queue/05-ui.wait.prompt.md" ]
check "5 대기 이유 기록" grep -q 'images first' "$MAC/scripts/log/runner-$(date +%F).log"
UI_WAIT_FOR_IMAGES=0 run_ui
check "5 UI_WAIT_FOR_IMAGES=0이면 진행" eval 'case "$(status_of ui.wait)" in "done "*) true;; *) false;; esac'
rm -f "$MAC/assets/jobs/queue/00-img.a.prompt.md"

# 6) 사용 한도 → 큐로 되돌림, 시도로 안 셈
echo limit > "$TMP/scenario"
job 06 ui.limit
LIMIT_RETRY=1 run_ui
check "6 의뢰서가 queue로 돌아옴" [ -f "$MAC/codex/ui/queue/06-ui.limit.prompt.md" ]
check "6 ATTEMPTS·STATUS 없음" eval '[ ! -e "$MAC/codex/ui/inbox/ui.limit/ATTEMPTS" ] && [ ! -e "$MAC/codex/ui/inbox/ui.limit/STATUS" ]'
check "6 한도 로그 보관" eval 'ls "$MAC"/codex/ui/inbox/ui.limit/codex-limit-*.log >/dev/null 2>&1'
mkdir -p "$MAC/codex/ui/hold" && mv "$MAC/codex/ui/queue/06-ui.limit.prompt.md" "$MAC/codex/ui/hold/"

# 7) 선행 조건: 이미지 id의 REVIEWED PASS를 기다린다
echo inside > "$TMP/scenario"
job 07 ui.dep '# requires-review: bill.w10000.a
'
before=$(calls)
run_ui
check "7 선행 검수 전에는 실행 안 함" eval '[ "$(calls)" = "$before" ] && [ -f "$MAC/codex/ui/queue/07-ui.dep.prompt.md" ]'
mkdir -p "$MAC/assets/inbox/bill.w10000.a" && echo 'PASS draft' > "$MAC/assets/inbox/bill.w10000.a/REVIEWED"
run_ui
check "7 이미지 검수 PASS 뒤 실행" eval 'case "$(status_of ui.dep)" in "done "*) true;; *) false;; esac'

# 8) 재시도는 -r<시도> 브랜치, Codex가 몰래 커밋해도 같은 검사를 받는다
echo commit > "$TMP/scenario"
mkdir -p "$MAC/codex/ui/inbox/ui.retry" && echo 1 > "$MAC/codex/ui/inbox/ui.retry/ATTEMPTS"
job 08 ui.retry
run_ui
check "8 두 번째 시도는 codex/ui/ui.retry-r2" eval 'case "$(status_of ui.retry)" in *"branch=codex/ui/ui.retry-r2 "*) true;; *) false;; esac'
check "8 Codex 커밋 대신 워커 커밋 하나" [ "$(git -C "$TMP/remote.git" log -1 --format=%s codex/ui/ui.retry-r2)" = "[codex-ui] ui.retry: ui.retry 화면 다듬기" ]
check "8 그 커밋의 부모는 원격 main" [ "$(git -C "$TMP/remote.git" rev-parse codex/ui/ui.retry-r2^)" = "$(git -C "$TMP/remote.git" rev-parse main)" ]

# 9) 이름 규칙·중복은 blocked + 이유
echo 'x' > "$MAC/codex/ui/queue/9-bad.prompt.md"
job 09 ui.wallet     # 1)의 결과가 아직 검수 전
run_ui
check "9 이름 규칙 위반 blocked" grep -q 'invalid request name' "$MAC/codex/ui/blocked/9-bad.prompt.md.reason"
check "9 기존 결과 blocked" grep -q 'existing result' "$MAC/codex/ui/blocked/09-ui.wallet.prompt.md.reason"

# 10) 메인 클론이 동기화 브랜치가 아니면 기다린다
echo inside > "$TMP/scenario"
job 10 ui.branch
git -C "$MAC" checkout -q -b other
before=$(calls)
run_ui
check "10 다른 브랜치면 실행 안 함" eval '[ "$(calls)" = "$before" ] && [ -f "$MAC/codex/ui/queue/10-ui.branch.prompt.md" ]'
git -C "$MAC" checkout -q main
finish
