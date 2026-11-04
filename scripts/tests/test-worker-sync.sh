#!/usr/bin/env bash
# scripts/worker-sync.sh: 임시 bare 원격과 클론 두 개(맥 워커 쪽, 클라우드 쪽)로 한 번씩 돌려 본다.
. "$(dirname "$0")/lib.sh"
TMP=$(mktemp -d "${TMPDIR:-/tmp}/realcash-sync.XXXXXX")
trap 'rm -rf "$TMP"' EXIT

git init -q --bare "$TMP/remote.git"
git -C "$TMP/remote.git" symbolic-ref HEAD refs/heads/main
git init -q "$TMP/seed"
(
  cd "$TMP/seed" || exit 1
  git checkout -q -b main
  mkdir -p assets/jobs/queue assets/jobs/running assets/inbox codex/ui/queue codex/ui/running codex/ui/inbox
  : > assets/jobs/queue/.gitkeep; : > assets/jobs/running/.gitkeep; : > assets/inbox/.gitkeep
  : > codex/ui/queue/.gitkeep; : > codex/ui/running/.gitkeep; : > codex/ui/inbox/.gitkeep
  cp "$SRC/assets/.gitignore" assets/.gitignore
  cp "$SRC/codex/.gitignore" codex/.gitignore
  printf '*.log\n' > .gitignore   # 루트 규칙이 Codex 로그를 막지 않는지도 같이 본다
  echo readme > README.md
  git add -A && git commit -qm init && git remote add origin "$TMP/remote.git" && git push -q origin main
) || { bad "seed repo"; finish; exit 1; }

git clone -q "$TMP/remote.git" "$TMP/mac"
git clone -q "$TMP/remote.git" "$TMP/cloud"
mkdir -p "$TMP/mac/scripts/lib"
cp "$SRC/scripts/lib/common.sh" "$TMP/mac/scripts/lib/"
cp "$SRC/scripts/worker-sync.sh" "$TMP/mac/scripts/"
printf 'log/\nSTOP\n' > "$TMP/mac/scripts/.gitignore"

sync_once() { (cd "$TMP/mac" && SYNC_ONCE=1 bash scripts/worker-sync.sh); }
remote_files() { git -C "$TMP/remote.git" ls-tree -r --name-only main; }

# 1) 클라우드가 의뢰서를 올리면 맥이 받는다
(cd "$TMP/cloud" && echo 'prompt' > assets/jobs/queue/00-test.a.prompt.md && git add -A && git commit -qm "queue" && git push -q origin main)
sync_once
check "의뢰서를 받아 옴" [ -f "$TMP/mac/assets/jobs/queue/00-test.a.prompt.md" ]
check "scripts/는 커밋하지 않음" eval '! remote_files | grep -q "^scripts/"'

# 2) 생성 중(running/에 의뢰서)이면 커밋하지 않고 받기만 한다
mv "$TMP/mac/assets/jobs/queue/00-test.a.prompt.md" "$TMP/mac/assets/jobs/running/"
mkdir -p "$TMP/mac/assets/inbox/test.a"
printf 'half' > "$TMP/mac/assets/inbox/test.a/test.a-1.png"
(cd "$TMP/cloud" && echo 'review' > assets/inbox/.note && git add -A && git commit -qm "note" && git push -q origin main)
before=$(git -C "$TMP/remote.git" rev-parse main)
sync_once
check "생성 중에는 원격에 새 커밋 없음" [ "$(git -C "$TMP/remote.git" rev-parse main)" = "$before" ]
check "생성 중에도 받기는 함" [ -f "$TMP/mac/assets/inbox/.note" ]

# 3) 끝나면 assets/inbox, assets/jobs만 커밋해 올린다. running/과 다른 경로는 안 올린다.
mkdir -p "$TMP/mac/assets/jobs/done"
mv "$TMP/mac/assets/jobs/running/00-test.a.prompt.md" "$TMP/mac/assets/jobs/done/"
echo 'done 2026-01-01 images=1 rc=0 10s' > "$TMP/mac/assets/inbox/test.a/STATUS"
echo 'codex output' > "$TMP/mac/assets/inbox/test.a/codex.log"
echo 'unrelated' > "$TMP/mac/README.md"
echo 'stray' > "$TMP/mac/assets/jobs/running/01-test.b.prompt.md.tmp"
echo 'x' > "$TMP/mac/other.txt"
sync_once
files=$(remote_files)
check "결과 PNG·STATUS를 올림" eval 'echo "$files" | grep -q "^assets/inbox/test.a/STATUS$" && echo "$files" | grep -q "^assets/inbox/test.a/test.a-1.png$"'
check "codex.log도 올림 (루트 *.log 규칙 무시)" eval 'echo "$files" | grep -q "^assets/inbox/test.a/codex.log$"'
check "done/으로 옮긴 의뢰서를 올림" eval 'echo "$files" | grep -q "^assets/jobs/done/00-test.a.prompt.md$"'
check "queue에서 지운 것도 반영" eval '! echo "$files" | grep -q "^assets/jobs/queue/00-test.a.prompt.md$"'
check "running/의 파일은 안 올림" eval '! echo "$files" | grep -q "^assets/jobs/running/0"'
check "다른 경로는 안 올림" eval '! echo "$files" | grep -q "^other.txt$" && [ "$(git -C "$TMP/remote.git" show main:README.md)" = readme ]'
check "커밋 메시지" [ "$(git -C "$TMP/remote.git" log -1 --format=%s main)" = "[sync] 이미지 작업 결과" ]
check "README 수정은 작업 트리에 그대로" [ "$(cat "$TMP/mac/README.md")" = unrelated ]

# 3b) UI 작업 중(codex/ui/running/에 의뢰서)이면 커밋하지 않고, 끝나면 codex/ui 기록을 올린다
echo 'ui prompt' > "$TMP/mac/codex/ui/running/01-ui.x.prompt.md"
mkdir -p "$TMP/mac/codex/ui/inbox/ui.x"
echo 'half' > "$TMP/mac/codex/ui/inbox/ui.x/codex.log"
echo 'img late' > "$TMP/mac/assets/inbox/test.a/late.txt"
before=$(git -C "$TMP/remote.git" rev-parse main)
sync_once
check "UI 작업 중에는 원격에 새 커밋 없음 (이미지 결과도 대기)" [ "$(git -C "$TMP/remote.git" rev-parse main)" = "$before" ]
mkdir -p "$TMP/mac/codex/ui/done"
mv "$TMP/mac/codex/ui/running/01-ui.x.prompt.md" "$TMP/mac/codex/ui/done/"
echo 'done 2026-01-01 branch=codex/ui/ui.x commit=abc check=ok 5s' > "$TMP/mac/codex/ui/inbox/ui.x/STATUS"
sync_once
files=$(remote_files)
check "UI STATUS·codex.log를 올림" eval 'echo "$files" | grep -q "^codex/ui/inbox/ui.x/STATUS$" && echo "$files" | grep -q "^codex/ui/inbox/ui.x/codex.log$"'
check "UI done/ 의뢰서를 올림" eval 'echo "$files" | grep -q "^codex/ui/done/01-ui.x.prompt.md$"'
check "UI running/은 안 올림" eval '! echo "$files" | grep -q "^codex/ui/running/0"'
check "기다리던 이미지 결과도 함께 올림" eval 'echo "$files" | grep -q "^assets/inbox/test.a/late.txt$"'

# 4) SYNC_BRANCH와 다른 브랜치면 아무것도 안 한다
git -C "$TMP/mac" checkout -q -b feature
echo 'new' > "$TMP/mac/assets/inbox/test.a/extra"
before=$(git -C "$TMP/remote.git" rev-parse main)
sync_once
check "다른 브랜치면 실패 코드" [ "$?" -ne 0 ]
check "다른 브랜치면 커밋하지 않음" [ -z "$(git -C "$TMP/mac" log main..feature --oneline)" ] && [ "$(git -C "$TMP/remote.git" rev-parse main)" = "$before" ]
check "건너뛴 이유를 기록" grep -q 'not on main (feature); skip' "$TMP/mac/scripts/log/runner-$(date +%F).log"

# 5) SYNC_BRANCH로 다른 브랜치를 쓸 수 있다
git -C "$TMP/mac" push -q origin feature
(cd "$TMP/mac" && SYNC_ONCE=1 SYNC_BRANCH=feature bash scripts/worker-sync.sh)
check "SYNC_BRANCH=feature 로 올림" eval 'git -C "$TMP/remote.git" ls-tree -r --name-only feature | grep -q "^assets/inbox/test.a/extra$"'
finish
