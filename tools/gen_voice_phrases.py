#!/usr/bin/env python
"""Generates mod/src/main/resources/assets/reiatsu_test/voice/phrases.json from design/VOICE_PHRASES.md.

Run from anywhere: python -I tools/gen_voice_phrases.py   (python 3.9+, no dependencies)

Sources, in this order:
  1. md section 1 (command table) and 1.N variant tables: every phrase of every command (origin C/T/G/M/X).
  2. md section 2 ASR tables: every row with tier A (the fuzzy matcher cannot reach it alone) becomes an alias (origin A).
  3. EXTRA_ALIASES below: ASR forms that came out of the phase 5 tests and field expectations (origin A), each with the
     reason; edit here, not in the json.
  4. GATED_ALIASES: the "bank eye" family. They are valid only when the player is in SHIKAI with the matching item in
     hand AND the reiatsu bar is full (see the decision in LOG.md, Phase 5), so the json marks them requires=full_reiatsu.

The md stays the human documentation; this script is the only writer of phrases.json.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MD = ROOT / "design" / "VOICE_PHRASES.md"
OUT = ROOT / "mod" / "src" / "main" / "resources" / "assets" / "reiatsu_test" / "voice" / "phrases.json"

FILLERS = ["please", "now", "okay", "ok", "пожалуйста", "давай", "ну", "сейчас", "お願い"]

# Commands that may fire from a stable interim result (VOICE_PHRASES 5.4 early-OK set). Never: bankai releases,
# scatter, hakuteiken, absolute zero, senkei (expensive or irreversible).
INTERIM_SAFE = {
    "rukia.shikai.release", "rukia.shikai.tsukishiro", "rukia.shikai.hakuren", "rukia.shikai.shirafune",
    "byakuya.shikai.release", "byakuya.shikai.mode_attack", "byakuya.shikai.mode_barrier", "common.seal",
}

# id -> list of (text, weak). Reasons are in the comments; every entry is an ASR rendering seen or expected in the
# fixture (tests/voice/phrases_fixture.json) that Jaro-Winkler cannot reach from the canonical forms.
EXTRA_ALIASES = {
    # md 2.13 lists "sen kei" as reachable by fuzzy matching (tier F); the matcher refuses to assemble a key of 6 letters
    # or less from two ordinary words (it would turn "banka i" into "bankai"), so the split form is an explicit alias.
    "byakuya.bankai.senkei": [("sen kei", False)],
}

# Context-gated ASR forms of "bankai" (decision: treat "bank eye" as bankai ONLY in SHIKAI + full reiatsu + item).
GATED_ALIASES = {
    "rukia.bankai.release": [
        ("bank eye", True), ("bankeye", True), ("bank i", True), ("bank ai", True), ("bank eye hakka no togame", False),
        ("bank i hakka no togame", False),
    ],
    "byakuya.bankai.release": [
        ("bank eye", True), ("bankeye", True), ("bank i", True), ("bank ai", True),
        ("bank eye senbonzakura kageyoshi", False), ("bank i senbonzakura kageyoshi", False),
    ],
}


def cells(line):
    inner = line.strip()
    if not (inner.startswith("|") and inner.endswith("|")):
        return None
    return [c.strip() for c in inner[1:-1].split("|")]


def strip_ticks(s):
    return s.strip().strip("`").strip()


def parse():
    lines = MD.read_text(encoding="utf-8").splitlines()
    commands = {}  # id -> dict, ordered
    section = None  # ("1"|"2", id)
    in_s1_table = False
    for line in lines:
        m = re.match(r"^### (1|2)\.(\d+) `([a-z_.]+)`", line)
        if m:
            section = (m.group(1), m.group(3))
            continue
        if line.startswith("## "):
            section = None
            in_s1_table = line.startswith("## 1. Command list")
            continue
        c = cells(line)
        if not c:
            continue
        if in_s1_table and section is None and len(c) == 6 and c[0].startswith("`"):
            cid = strip_ticks(c[0])
            states = [s.strip() for s in c[1].split("/")]
            item = {"Rukia's zanpakuto": "rukia", "Byakuya's zanpakuto": "byakuya", "either zanpakuto": "any"}[c[2]]
            say = re.sub(r"^\(mod-defined\)\s*", "", c[3])
            say = re.sub(r"\s*/\s*mod:.*$", "", say)
            say = re.sub(r"\s*\(same word as shikai\)", "", say)
            commands[cid] = {
                "id": cid, "states": states, "item": item, "interimSafe": cid in INTERIM_SAFE,
                "optional": cid.endswith(".senkei"), "say": say, "variants": [],
                "weak_all": c[4].lower() == "yes",
            }
            continue
        if section and section[0] == "1" and len(c) == 4 and re.fullmatch(r"`[CTGMXA]`", c[2]):
            phrase = c[1]
            weak = "(weak: whole utterance)" in phrase or commands[section[1]]["weak_all"]
            phrase = phrase.replace("(weak: whole utterance)", "").strip()
            commands[section[1]]["variants"].append({"text": phrase, "origin": strip_ticks(c[2]), "weak": weak})
            continue
        if section and section[0] == "2" and len(c) == 4 and c[3] == "A":
            cmd = commands[section[1]]
            cmd["variants"].append({"text": c[1], "origin": "A", "weak": cmd["weak_all"]})
    return commands


def add_unique(cmd, text, origin, weak, requires=None):
    for v in cmd["variants"]:
        if v["text"].lower() == text.lower() and bool(v.get("requires")) == bool(requires):
            return
    v = {"text": text, "origin": origin, "weak": weak}
    if requires:
        v["requires"] = requires
    cmd["variants"].append(v)


def fmt_variant(v):
    parts = ['"text": %s' % json.dumps(v["text"], ensure_ascii=False), '"origin": "%s"' % v["origin"]]
    if v["weak"]:
        parts.append('"weak": true')
    if v.get("requires"):
        parts.append('"requires": %s' % json.dumps(v["requires"]))
    return "{" + ", ".join(parts) + "}"


def main():
    commands = parse()
    for cid, extras in EXTRA_ALIASES.items():
        for text, weak in extras:
            add_unique(commands[cid], text, "A", weak)
    for cid, extras in GATED_ALIASES.items():
        for text, weak in extras:
            add_unique(commands[cid], text, "A", weak, ["full_reiatsu"])
    out = ['{', '  "version": 1,',
           '  "_comment": "Generated by tools/gen_voice_phrases.py from design/VOICE_PHRASES.md. Do not edit by hand.",',
           '  "fillers": %s,' % json.dumps(FILLERS, ensure_ascii=False), '  "commands": [']
    for i, cmd in enumerate(commands.values()):
        out.append('    {')
        out.append('      "id": "%s", "states": %s, "item": "%s",' % (cmd["id"], json.dumps(cmd["states"]), cmd["item"]))
        out.append('      "interimSafe": %s, "optional": %s, "say": %s,' % (
            str(cmd["interimSafe"]).lower(), str(cmd["optional"]).lower(), json.dumps(cmd["say"], ensure_ascii=False)))
        out.append('      "variants": [')
        out.append(",\n".join("        " + fmt_variant(v) for v in cmd["variants"]))
        out.append('      ]')
        out.append('    }' + ("," if i < len(commands) - 1 else ""))
    out.append('  ]')
    out.append('}')
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(out) + "\n", encoding="utf-8", newline="\n")
    total = sum(len(c["variants"]) for c in commands.values())
    print("wrote %s: %d commands, %d variants" % (OUT, len(commands), total))


if __name__ == "__main__":
    sys.exit(main())
