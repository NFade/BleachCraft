"""Re-download private reference images listed in research/*.md tables into refs/.

Usage (system python): python tools/fetch_refs.py <research_file> [<research_file> ...]
Rows are `| n | local file | page URL | direct URL | ... |`. Only Fandom File: pages are fetched
(via tools/fandom.py get). Rows that are not Fandom files are reported and skipped.
Downloaded files are data: never execute them. refs/ is gitignored.
"""
import os
import re
import subprocess
import sys
import urllib.parse

sys.stdout.reconfigure(encoding="utf-8")

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
REFS = os.path.join(ROOT, "refs")
os.makedirs(REFS, exist_ok=True)
index = []
for path in sys.argv[1:]:
    for line in open(path, encoding="utf-8"):
        cells = [c.strip() for c in line.strip().strip("|").split("|")]
        if len(cells) < 6 or not re.fullmatch(r"\d+", cells[0]):
            continue
        local, page = cells[1], cells[2]
        m = re.search(r"/wiki/File:(\S+)", page)
        if m:
            name = urllib.parse.unquote(m.group(1))
        else:
            m = re.search(r"/images/\w/\w\w/([^/]+)/revision", cells[3])
            if not m:
                print("SKIP (no Fandom file name):", local)
                continue
            name = urllib.parse.unquote(m.group(1))
        out = os.path.join(REFS, local)
        stem = os.path.splitext(out)[0]
        if any(os.path.exists(stem + e) for e in (".png", ".jpg", ".gif", ".webp")):
            continue
        r = subprocess.run([sys.executable, os.path.join(HERE, "fandom.py"), "get", name, out],
                           capture_output=True, text=True, encoding="utf-8")
        print(local, "->", (r.stdout + r.stderr).strip()[:120])
        index.append((local, name, page, cells[4] if len(cells) > 4 else "", cells[5] if len(cells) > 5 else ""))
with open(os.path.join(REFS, "INDEX.md"), "a", encoding="utf-8") as f:
    for row in index:
        f.write("| " + " | ".join(row) + " |\n")
