#!/usr/bin/env bash
# 워커 스크립트 점검: 문법 검사 + 셸 테스트. 사용: bash scripts/tests/run.sh
# (Codex 워커의 동작 테스트는 tools/loop의 vitest: cd tools/loop && npm test)
cd "$(dirname "$0")/../.." || exit 1
rc=0
for f in scripts/*.sh scripts/lib/*.sh scripts/tests/*.sh; do
  if bash -n "$f"; then echo "ok   bash -n $f"; else echo "FAIL bash -n $f"; rc=1; fi
done
if python3 -c "import ast, sys; ast.parse(open(sys.argv[1]).read())" scripts/lib/launch_worker.py; then echo "ok   python syntax launch_worker.py"; else echo "FAIL python syntax launch_worker.py"; rc=1; fi
for t in scripts/tests/test-*.sh; do
  bash "$t" || rc=1
done
[ "$rc" -eq 0 ] && echo "ALL OK" || echo "SOME FAILED"
exit $rc
