#!/usr/bin/env bash
# 워커 멈추기 (이미지·동기화·UI): STOP 파일을 만들면 워커가 현재 작업을 마치고 끝난다. --now 를 주면 즉시 종료.
cd "$(dirname "$0")/.." || exit 1
mkdir -p scripts/log
touch scripts/STOP
echo "STOP 파일을 만들었어요. 진행 중인 이미지·UI 작업이 끝나면 멈춰요."
if [ "${1:-}" = "--now" ]; then
  # pid 파일의 프로세스가 정말 이 저장소의 워커인지(명령줄의 절대 경로) 확인한 뒤
  # 그 프로세스와 자손(codex 등)만 종료한다. 재사용된 pid의 다른 프로세스는 건드리지 않는다.
  python3 - <<'PY'
import os
from pathlib import Path
import signal
import subprocess
import time

root = Path.cwd()


def processes():
    """pid -> (ppid, state, start time). Start time guards against pid reuse."""
    out = subprocess.run(["ps", "-axo", "pid=,ppid=,stat=,lstart="],
                         capture_output=True, text=True, check=True).stdout
    table = {}
    for line in out.splitlines():
        fields = line.split(None, 3)
        if len(fields) == 4:
            table[int(fields[0])] = (int(fields[1]), fields[2], fields[3])
    return table


def alive(targets):
    current = processes()
    return {p: birth for p, birth in targets.items()
            if p in current and current[p][2] == birth and "Z" not in current[p][1]}


table = processes()
targets = {}
for worker, script in (("codex", "worker-codex.sh"), ("sync", "worker-sync.sh"), ("ui", "worker-codex-ui.sh")):
    path = root / ("scripts/log/%s.pid" % worker)
    try:
        pid = int(path.read_text().strip())
    except (OSError, ValueError):
        continue
    command = subprocess.run(["ps", "-p", str(pid), "-o", "args="],
                             capture_output=True, text=True).stdout
    if str(root / "scripts" / script) not in command:
        if command.strip():
            print("PID owner differs; leaving it alone:", pid)
        continue
    family = {pid}
    while True:
        more = {p for p, row in table.items() if row[0] in family}
        if more.issubset(family):
            break
        family.update(more)
    targets.update({p: table[p][2] for p in family if p in table})

for pid in list(targets):
    try:
        os.kill(pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
deadline = time.monotonic() + 20
while targets and time.monotonic() < deadline:
    targets = alive(targets)
    if targets:
        time.sleep(0.2)
if targets:
    for pid in targets:
        try:
            os.kill(pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
    time.sleep(0.2)
    remaining = list(alive(targets))
    if remaining:
        raise SystemExit("Workers still stopping; STOP remains set: %s" % remaining)
print("이 저장소 워커의 종료를 확인했습니다.")
PY
  [ "$?" -eq 0 ] || exit 1
fi
echo "다시 시작: bash scripts/start.sh"
