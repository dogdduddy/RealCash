#!/usr/bin/env bash
# scripts/lib/common.sh: 한도 문구 감지, 타임아웃, STOP을 보는 nap
. "$(dirname "$0")/lib.sh"
TMP=$(mktemp -d "${TMPDIR:-/tmp}/realcash-common.XXXXXX")
trap 'rm -rf "$TMP"' EXIT
mkdir -p "$TMP/scripts/lib"
cp "$SRC/scripts/lib/common.sh" "$TMP/scripts/lib/"
WORKER=test
. "$TMP/scripts/lib/common.sh"

check "ROOT은 scripts/lib의 두 단계 위" [ "$ROOT" = "$(cd "$TMP" && pwd)" ]
check "로그 폴더는 scripts/log" [ -d "$TMP/scripts/log" ]

printf 'working...\nYou have hit your usage limit. Try again in 20 minutes.\n' > "$TMP/limit.log"
check "사용 한도 문구를 감지" looks_like_limit "$TMP/limit.log"
printf 'working...\nimage saved\n' > "$TMP/fine.log"
check "평범한 출력은 한도로 보지 않음" eval '! looks_like_limit "$TMP/fine.log"'
check "없는 파일은 한도로 보지 않음" eval '! looks_like_limit "$TMP/missing.log"'

run_with_timeout 5 bash -c 'exit 7'
check "타임아웃 안에 끝나면 원래 종료 코드" [ "$?" -eq 7 ]
start=$(date +%s)
run_with_timeout 1 sleep 30
rc=$?
elapsed=$(( $(date +%s) - start ))
check "타임아웃이면 TERM으로 끊김 (rc=$rc, ${elapsed}s)" [ "$rc" -ne 0 ] && [ "$elapsed" -lt 10 ]

NAP_CHUNK=1
touch "$STOP_FILE"
start=$(date +%s)
nap 20
check "STOP 파일이 있으면 nap이 바로 끝남" [ $(( $(date +%s) - start )) -lt 3 ]
check "stopped는 scripts/STOP을 본다" stopped
rm -f "$STOP_FILE"
check "STOP을 지우면 stopped가 거짓" eval '! stopped'

log "hello"
check "log는 scripts/log/runner-날짜.log에 쓴다" grep -q '\[test\] hello' "$TMP/scripts/log/runner-$(date +%F).log"
finish
