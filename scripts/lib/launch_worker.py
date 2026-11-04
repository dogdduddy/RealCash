#!/usr/bin/env python3
"""Detach a worker from the launching shell's process group and terminal.

Usage: python3 scripts/lib/launch_worker.py codex|sync|ui
Writes scripts/log/<worker>.pid and appends output to scripts/log/worker-<worker>.nohup.
"""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

root = Path(__file__).resolve().parents[2]
worker = sys.argv[1] if len(sys.argv) > 1 else ""
SCRIPTS = {"codex": "worker-codex.sh", "sync": "worker-sync.sh", "ui": "worker-codex-ui.sh"}
if worker not in SCRIPTS:
    raise SystemExit("unknown worker (codex|sync|ui)")
log = root / "scripts/log"
log.mkdir(parents=True, exist_ok=True)
command = ["bash", str(root / "scripts" / SCRIPTS[worker])]
if shutil.which("caffeinate"):
    command = ["caffeinate", "-i", "-s", *command]
with (log / ("worker-%s.nohup" % worker)).open("ab") as output:
    child = subprocess.Popen(command, cwd=root, stdin=subprocess.DEVNULL,
                             stdout=output, stderr=subprocess.STDOUT,
                             start_new_session=True, close_fds=True, env=os.environ.copy())
time.sleep(0.3)
if child.poll() is not None:
    raise SystemExit("worker failed to stay running: %s rc=%s" % (worker, child.returncode))
(log / (worker + ".pid")).write_text(str(child.pid) + "\n")
print("%s 워커 시작 (pid %s)" % (worker, child.pid))
