#!/usr/bin/env python3
"""Drive the Phase 6 live console (gradlew runPhase6 -Phold=console) from a script file.

usage: python -I tools/fp_console.py <mod_dir> <cmd_file_or_-> [timeout_s]
Writes the commands to <mod_dir>/run/p6_cmd.txt and waits until run/p6_done.txt carries the stamp of that file.
"""
import os
import sys
import time

mod = sys.argv[1]
src = sys.argv[2]
timeout = float(sys.argv[3]) if len(sys.argv) > 3 else 300
text = sys.stdin.read() if src == "-" else open(src, encoding="utf-8").read()
cmd = os.path.join(mod, "run", "p6_cmd.txt")
done = os.path.join(mod, "run", "p6_done.txt")
open(cmd, "w", encoding="utf-8", newline="\n").write(text)
stamp = str(int(os.path.getmtime(cmd) * 1000))
t0 = time.time()
while time.time() - t0 < timeout:
    time.sleep(1.0)
    try:
        d = open(done, encoding="utf-8").read().strip()
    except OSError:
        continue
    # the game stores the file time in millis (java); mtime ints can differ by rounding, so compare loosely
    if d and abs(int(d) - int(stamp)) < 1500:
        print("done", d)
        sys.exit(0)
print("TIMEOUT waiting for the game")
sys.exit(1)
