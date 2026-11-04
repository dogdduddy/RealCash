#!/usr/bin/env bash
# 이미지 워커 상태 한눈에 보기
cd "$(dirname "$0")/.." || exit 1
L=scripts/log
for w in codex sync; do
  pidf="$L/$w.pid"
  if [ -f "$pidf" ] && kill -0 "$(cat "$pidf")" 2>/dev/null; then echo "● $w 워커 실행 중 (pid $(cat "$pidf"))"; else echo "○ $w 워커 멈춤"; fi
done
[ -f scripts/STOP ] && echo "(STOP 파일 있음 — 현재 작업 후 멈춤)"
echo "동기화 대상: $(cat "$L/sync-branch" 2>/dev/null || echo "origin/main (기본)") / 지금 체크아웃: $(git symbolic-ref --short HEAD 2>/dev/null)"
echo
echo "== 이미지 의뢰 =="
for d in queue running done failed blocked hold; do
  printf '%-8s %s\n' "$d" "$(ls assets/jobs/$d/*.prompt.md 2>/dev/null | wc -l | tr -d ' ')"
done
n=0
for s in assets/inbox/*/STATUS; do
  [ -f "$s" ] || continue
  [ -f "${s%/STATUS}/REVIEWED" ] || n=$((n + 1))
done
echo "미검수   $n (${IMAGE_MAX_UNREVIEWED:-4}개면 워커가 대기)"
for r in assets/jobs/blocked/*.reason; do
  [ -f "$r" ] || continue
  echo "  blocked: $(basename "$r" .reason) — $(cat "$r")"
done
echo
echo "== 최근 기록 =="
tail -n 12 "$L/runner-$(date +%F).log" 2>/dev/null || echo "(오늘 기록 없음)"
