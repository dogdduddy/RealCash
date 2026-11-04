# 셸 테스트 공통 (bash 3.2 호환). 각 테스트 파일이 source 한다.
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FAILS=0
PASSES=0
ok()   { PASSES=$((PASSES + 1)); printf 'ok   %s\n' "$*"; }
bad()  { FAILS=$((FAILS + 1)); printf 'FAIL %s\n' "$*"; }
check() { # check "설명" 명령...
  local what=$1; shift
  if "$@"; then ok "$what"; else bad "$what"; fi
}
finish() {
  printf '%s: %d passed, %d failed\n' "$(basename "$0")" "$PASSES" "$FAILS"
  [ "$FAILS" -eq 0 ]
}
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.invalid
export GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.invalid
export GIT_CONFIG_NOSYSTEM=1
# 사용자 전역 설정(서명, push 협상 등)이 임시 저장소 테스트에 끼어들지 않게
export GIT_CONFIG_GLOBAL=/dev/null
