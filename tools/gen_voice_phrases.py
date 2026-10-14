#!/usr/bin/env python
"""Generates mod/src/main/resources/assets/reiatsu_test/voice/phrases.json from design/VOICE_PHRASES.md.

Run from anywhere: python -I tools/gen_voice_phrases.py   (python 3.9+, no dependencies)

Sources, in this order:
  1. md section 1 (command table) and 1.N variant tables: every phrase of every command (origin C/T/G/M/X).
  2. md section 2 ASR tables: every row with tier A (the fuzzy matcher cannot reach it alone) becomes an alias (origin A).
  3. EXTRA_ALIASES below: ASR forms that came out of the phase 5 tests and field expectations (origin A), each with the
     reason; edit here, not in the json.
  4. GATED_ALIASES: the "bank eye" family. They are valid only when the player is in SHIKAI with the matching item in
     hand AND a bankai release is possible (enough Rage; the reiatsu bar no longer matters, mech2 task 3), so the json marks them requires=bankai_ready.

The md stays the human documentation; this script is the only writer of phrases.json.
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MD = ROOT / "design" / "VOICE_PHRASES.md"
OUT = ROOT / "mod" / "src" / "main" / "resources" / "assets" / "reiatsu_test" / "voice" / "phrases.json"

# Fillers are ignored by the whole-utterance rule of weak variants ("uh, seal"). G3 added hesitations and lead-in words
# that real speech has; none of them is a command word, and they never help a non-weak variant to match.
FILLERS = ["please", "now", "okay", "ok", "пожалуйста", "давай", "ну", "сейчас", "お願い",
           "uh", "um", "er", "ah", "oh", "hmm", "so", "yeah", "then",
           "эм", "ага", "так", "вот", "ладно", "тогда"]

# Commands that may fire from a stable interim result (VOICE_PHRASES 5.4 early-OK set). Never: bankai releases,
# scatter, hakuteiken, senkei (expensive or irreversible).
INTERIM_SAFE = {
    "rukia.shikai.release", "rukia.shikai.tsukishiro", "rukia.shikai.hakuren", "rukia.shikai.shirafune",
    "byakuya.shikai.release", "byakuya.shikai.mode_attack", "byakuya.shikai.mode_barrier", "common.seal",
}

# --- 2026-10-11 live session (design/results/voice_v3.md). Byakuya's names, as Chrome ru-RU / en-US actually wrote them.
# The lone sword name is the shikai release in BASE (it was a trap before: "Senbonzakura" alone was ignored). It is WEAK,
# i.e. only the whole utterance counts (fillers ignored), so "I like senbonzakura music" never fires it. The z/s, m/n and
# word-split variants ("sen bonsakura", "сэм бонзакура", "сэмбонзакура") all share one key and need no alias.
SENBON_ALONE = [("senbonzakura", True), ("сэнбонзакура", True), ("千本桜", True)]
# "chire" heard as other words: the keys are sire (shire, шире, щире) and cere (чьере, чере). Nonsense or very rare as a whole
# utterance; weak (whole utterance only), and valid only in BASE (release) or BANKAI (scatter).
# Deliberately NOT lone: cherry / cheery / chili (key cery, ciri), тире, and shiro / щиро (key siro, the same as the
# Minecraft word "silo"); those two only count glued to the name (CHIRE_LEAD_COMBOS).
CHIRE_LONE_WEAK = [("shire", True), ("шире", True), ("щире", True), ("чьере", True), ("чере", True)]
# the same mis-hearings glued to the name ("щиро сен-бонзакура", "что сэмбонзакура", "ширая сен бонзакура"); the lead may
# be any of the garbles because the name itself is the evidence (strong variant, fuzzy per token as usual).
CHIRE_LEAD_COMBOS = [(lead + " senbonzakura", False) for lead in
                     ("shire", "shiro", "шире", "щире", "щиро", "чьере", "ширая", "что", "sheeray")]

# id -> list of (text, weak). Reasons are in the comments; every entry is an ASR rendering seen or expected in the
# fixture (tests/voice/phrases_fixture.json) that Jaro-Winkler cannot reach from the canonical forms.
EXTRA_ALIASES = {
    # md 2.13 lists "sen kei" as reachable by fuzzy matching (tier F); the matcher refuses to assemble a key of 6 letters
    # or less from two ordinary words (it would turn "banka i" into "bankai"), so the split form is an explicit alias.
    #
    # --- G3 (live speech): forms a recogniser plausibly gives for the same sounds (UNVERIFIED without a live
    # microphone). Each one is a nonsense phrase in ordinary speech; the loose key (LooseKey) covers long names beyond
    # this list. Never add a spelling whose key equals a gated "bank eye" family key ("bankei", "bankeye" -> "banke").
    #
    # the key of Shirayuki is only 8 letters, so a three-way split ("she ra yuki") is out of fuzzy reach
    "rukia.shikai.release": [("she ra yuki", False), ("shi ra yuki", False), ("sherry yuki", False),
                             ("мае сода но шира юки", False), ("широ юки", True)],
    # "bankai" is 6 letters (exact-only), so the vowel slips of real recognisers are explicit aliases
    "rukia.bankai.release": [("bankay", False), ("bunkay", False), ("bonkay", False), ("bang kai", False),
                             ("bung kai", False)],
    # Kageyoshi: the ey fold shortens its key to 6 letters ("kagesi", exact-only); the spaced spellings keep eight
    # letters and so one slip of tolerance
    "byakuya.bankai.release": [("kage yoshi", False), ("kaga yoshi", False), ("kagayoshi", False),
                               ("cagey yoshi", False), ("кагаеси", False), ("кага еси", False),
                               ("bankay", False), ("bunkay", False), ("bonkay", False), ("bang kai", False),
                               ("bung kai", False)],
    "byakuya.shikai.release": [("sherry senbonzakura", False), ("chiray senbonzakura", False),
                               ("чере сенбонзакура", False)] + SENBON_ALONE + CHIRE_LONE_WEAK + CHIRE_LEAD_COMBOS,
    "byakuya.shikai.mode_attack": [("attacked mode", False), ("режим атака", False)],
    "byakuya.shikai.mode_barrier": [("barrier modo", False), ("bury her mode", False),
                                    ("bow gyo mode", False), ("режим барьер", False), ("режим купол", False)],
    "byakuya.bankai.scatter": [("sherry senbonzakura", False), ("chiray senbonzakura", False),
                               ("чере сенбонзакура", False), ("лепестковая буря", False),
                               ("буря из лепестков", False), ("шторм из лепестков", False), ("petal storms", False)]
                              + CHIRE_LONE_WEAK + CHIRE_LEAD_COMBOS,
    # live log 2026-10-11 and expected siblings: ASR splits "hakuteiken" in two or three, or hears "taken"
    "byakuya.bankai.hakuteiken": [("haku tei ken", False), ("hakutei ken", False), ("хаку тейкен", False),
                                  ("хаку тайкен", False), ("hacku tay ken", False)],
    # senkei is optional and 6 letters long: exact-only, so the common mis-hearings are listed ("sankei"/"sankey" is
    # left out on purpose: its key "sanke" is also the plain English word)
    "byakuya.bankai.senkei": [("sen kei", False), ("sen kai", False), ("senkai", False),
                              ("senkay", False), ("sankay", False), ("сенкай", False),
                              ("sen key", False), ("сен кей", False)],
    # seal: only the whole utterance counts (weak), so natural objects of the verb are variants of their own
    "common.seal": [("seal your sword", True), ("seal my sword", True), ("seal the blade", True),
                    ("seal the zanpakuto", True), ("запечатай катану", True), ("запечатать катану", True),
                    ("запечатай свой меч", True)],
}

# --- B5 kido (design/results/kido.md). The three spells are valid in every state with any sword or none, so a false positive costs
# reiatsu and can happen while the player talks; the ASR spellings that are several ordinary words ("by a cry") are therefore
# WEAK (whole utterance only). Single made-up words ("бякурая") are strong. The 6 letter key of Shakkaho (sakaho) is exact-only,
# so its split and vowel-slip renderings are explicit. UNVERIFIED with a real microphone (expected renderings, not recordings).
EXTRA_ALIASES.update({
    "kido.byakurai": [("by a cry", True), ("be a cry", True), ("beer a cry", True), ("bye a cry", True), ("бекурай", False),
                      ("бякурая", False), ("бья курай", False), ("хадо ён", False), ("hado yon", False)],
    "kido.shakkaho": [("sha ka ho", True), ("shaka ho", True), ("shack a ho", True), ("сяка хо", True), ("шака хо", True),
                      ("сякаго", False), ("сякохо", False), ("shockaho", False)],
    "kido.sai": [("бокудо один", False), ("bokudo ichi sai", False)],
})

# --- 2026-10-11 live session, ja-JP kido (design/results/voice_v4.md). The user said 破道の四 白雷 and Chrome returned "は", "波動"
# (a homophone of 破道, empty key before v4) and "くらい" (the first syllable びゃ lost); nothing for the other two spells. Surface
# forms below are what ja-JP Chrome plausibly writes for the same sounds: kanji homophones (the Normalizer KANJI table maps them
# to the key; 借家法 is the legal term read しゃっかほう), kana in both scripts, digits instead of kanji numerals, and the spell
# name glued to hado / bakudo. Keys of same-sound kana collapse to one key, so only distinct spellings are listed. Everything is
# UNVERIFIED against a recording except the two "くらい" / "波動" observations. The lone "くらい" is WEAK (whole utterance only; it
# also means "about" / "dark", so inside a sentence it never fires). Lone 破道 / 波動 / はどう never fire (Hado alone is ambiguous).
JA_KIDO = {
    "kido.byakurai": [
        ("白雷", False), ("百雷", False), ("白来", False), ("百来", False), ("薄雷", False), ("白らい", False), ("白ライ", False),
        ("びゃく雷", False), ("ビャクライ", False), ("ひゃくらい", False), ("ヒャクライ", False), ("ひゃく雷", False),
        ("びゃくらい", False), ("くらい", True),
        ("破道の四", False), ("破道の4", False), ("波動の四", False), ("波動の4", False), ("覇道の四", False), ("破道四", False),
        ("破道の四 白雷", False), ("破道の4 白雷", False), ("波動の四 白雷", False), ("波動の4 白雷", False), ("波動の4 百雷", False),
        ("破道の四 びゃくらい", False), ("波動の4 びゃくらい", False), ("波動 4", False), ("破道 4", False),
        ("はどうの4", False), ("ハドウの4", False), ("ハドウノヨン", False), ("ハドウノヨン ビャクライ", False),
        ("はどうのよん", False), ("はどう よん", False), ("ハドウ 4", False), ("ハドウ4", False), ("ハドウ4 ビャクライ", False),
        ("hadou no yon", False), ("hadō no yon byakurai", False),
        ("hadou yon", False), ("hado no yon byakuraï", False), ("byakurai", False), ("bya ku rai", False), ("hyakurai", False),
    ],
    "kido.shakkaho": [
        ("赤火砲", False), ("借家法", False), ("借家砲", False), ("赤火法", False), ("赤火方", False), ("赤化法", False),
        ("射火砲", False), ("石火砲", False), ("しゃっか砲", False), ("シャッカ砲", False), ("赤火ほう", False),
        ("しゃっかほう", False), ("シャッカホウ", False), ("しゃかほう", False), ("シャカホウ", False), ("シャッカホー", False),
        ("破道の三十一", False), ("破道の31", False), ("波動の三十一", False), ("波動の31", False), ("破道三十一", False),
        ("破道の三十一 赤火砲", False), ("破道の31 赤火砲", False), ("波動の三十一 赤火砲", False), ("波動の31 借家法", False),
        ("破道の三十一 しゃっかほう", False), ("波動の31 しゃっかほう", False), ("波動 31", False), ("破道 31", False),
        ("はどうの31", False), ("ハドウノサンジュウイチ", False), ("ハドウノサンジュウイチ シャッカホウ", False),
        ("はどうのさんじゅういち", False), ("はどう さんじゅういち", False), ("ハドウ31", False), ("ハドウ31 シャッカホウ", False),
        ("hadou no sanjuuichi shakkahou", False), ("shakkahou", False),
        ("sakkaho", False), ("shakkaho", False),
    ],
    "kido.sai": [
        ("塞", True), ("さい", True), ("サイ", True),
        ("縛道の一", False), ("縛道の1", False), ("爆道の一", False), ("縛道一", False),
        ("縛道の一 塞", False), ("縛道の1 塞", False), ("爆道の1 塞", False), ("縛道の一 さい", False), ("縛道の1 サイ", False),
        ("ばくどう", True), ("バクドウ", True), ("縛道", True), ("ばくどう さい", False), ("バクドウ サイ", False),
        ("ばくどうのいち", False), ("バクドウノイチ", False), ("バクドウノイチ サイ", False), ("ばくどう いち さい", False),
        ("bakudou no ichi sai", False), ("bakudou", True),
    ],
}
for _cid, _items in JA_KIDO.items():
    EXTRA_ALIASES[_cid] = EXTRA_ALIASES.get(_cid, []) + _items

# --- Gin Ichimaru and Uryu Ishida (branch gin-uryu, registration skeleton). Renderings a recogniser plausibly gives for the same sounds,
# kana forms and spaced spellings. UNVERIFIED with a real microphone; the names are rare words, so none of them is weak.
NEW_CHARACTER_ALIASES = {
    "gin.shikai.release": [("i korose shinso", False), ("ikoro se shinso", False), ("イコロセ シンソウ", False), ("いころせ しんそう", False),
                           ("икоросе синсо", False), ("ikorose shinsou", False)],
    "gin.shikai.ikorose": [("i korose", False), ("ikoro se", False), ("イコロセ", False), ("いころせ", False), ("икоросе", False),
                           ("ikorose shinsou", False), ("ikorosey", False)],
    "gin.shikai.hyapponzashi": [("hyappon zashi", False), ("hi appon zashi", False), ("ヒャッポンザシ", False), ("ひゃっぽんざし", False),
                                ("хьяппон заси", False), ("hiyapponzashi", False), ("hyapon zashi", False)],
    "gin.shikai.snake_hook": [("snake hook", False), ("the snake hook", False), ("スネークフック", False), ("すねーくふっく", False),
                              ("змеиный крюк", False), ("snake hooks", False)],
    "gin.bankai.release": [("bankai kamishini no yari", False), ("kami shini no yari", False), ("kamishini no yari", False),
                           ("カミシニノヤリ", False), ("かみしにのやり", False), ("камисини но яри", False), ("банкай камисини но яри", False)],
    "gin.bankai.butou_renjin": [("buto renjin", False), ("butou renjin", False), ("ブトウ レンジン", False), ("ぶとう れんじん", False),
                                ("бутоу рэндзин", False), ("boo toe renjin", False), ("buto ren jin", False)],
    "gin.bankai.korose": [("korose kamishini no yari", False), ("ko rose", False), ("コロセ", False), ("ころせ", False), ("коросэ", False),
                          ("коросе", False), ("korose kamishini", False)],
    "gin.bankai.hakufuku": [("haku fuku", False), ("hakufuku", False), ("ハクフク", False), ("はくふく", False), ("хакуфуку", False),
                            ("hakufuko", False), ("haku fuko", False), ("hakufoku", False)],
    "uryu.shikai.release": [("blood arterie", False), ("blut arteri", False), ("blood arteri", False), ("ブルート アルテリー", False),
                            ("блут артери", False), ("blut arterie", False), ("blood artery", False)],
    "uryu.shikai.heilig_pfeil": [("heilig pfeil", False), ("highlig pfeil", False), ("heiliger pfeil", False), ("hylig fyle", False),
                                 ("ハイリッヒ プファイル", False), ("хайлиг пфайль", False), ("heilig pfile", False)],
    "uryu.shikai.sprenger": [("sprenger", False), ("sprengar", False), ("shprenger", False), ("スプレンガー", False), ("шпренгер", False),
                             ("спренгер", False), ("sprengerr", False), ("shprengar", False)],
    "uryu.shikai.seele_schneider": [("seele schneider", False), ("sele schneider", False), ("zeele schneider", False), ("zeile schneider", False),
                                    ("seal schneider", False), ("ゼーレ シュナイダー", False), ("зеле шнайдер", False), ("зеле шнайдэр", False),
                                    ("seele schnider", False), ("sea la shnider", False)],
    "uryu.bankai.release": [("quincy vollstandig", False), ("quincy volstandig", False), ("quincy follstandig", False),
                            ("クインシー フォルシュテンディッヒ", False), ("квинси фольштендиг", False), ("quincy full standing", False),
                            ("volstandig", False)],
    "uryu.bankai.wing_barrage": [("heiligen fliegen", False), ("heilige fliegen", False), ("hylighen fleegen", False), ("heiligen flügen", False),
                                 ("wing barrage", False), ("wings barrage", False), ("ハイリゲン フリーゲン", False), ("хайлиген флиген", False),
                                 ("хайлиген флиген", False), ("залп крыльев", False), ("залп крыла", False)],
    "uryu.bankai.licht_regen": [("licht regen", False), ("lichtregen", False), ("light rain", False), ("lickt regen", False), ("lik regen", False),
                                ("liquid regen", False), ("リヒト レーゲン", False), ("лихт реген", False), ("лихтреген", False), ("лихт регэн", False),
                                ("дождь света", False), ("свет дождь", False)],
    "uryu.bankai.sternenstaub": [("sternenstaub", False), ("sternen staub", False), ("stern enstaub", False), ("シュテルネンシュタウプ", False),
                                 ("штернэнштауб", False), ("sternen stop", False), ("stern en staub", False), ("shternenshtaub", False)],
}
for _cid, _items in NEW_CHARACTER_ALIASES.items():
    EXTRA_ALIASES[_cid] = EXTRA_ALIASES.get(_cid, []) + _items

# Context-gated ASR forms of "bankai" (decision: treat "bank eye" as bankai ONLY in SHIKAI + full reiatsu + item).
GATED_ALIASES = {
    "rukia.bankai.release": [
        ("bank eye", True), ("bankeye", True), ("bank i", True), ("bank ai", True), ("bank eye hakka no togame", False),
        ("bank i hakka no togame", False), ("bankie", True), ("banki", True),
    ],
    "byakuya.bankai.release": [
        ("bank eye", True), ("bankeye", True), ("bank i", True), ("bank ai", True), ("bankie", True), ("banki", True),
        ("bank eye senbonzakura kageyoshi", False), ("bank i senbonzakura kageyoshi", False),
    ],
}
# live log 2026-10-11: the ru-RU recogniser's top hypothesis for "банкай" was "банкой" (the instrumental of "банка", a jar);
# same protection as the bank eye family (SHIKAI + item + full bar, whole utterance only)
GATED_ALIASES["rukia.bankai.release"].append(("банкой", True))
GATED_ALIASES["byakuya.bankai.release"].append(("банкой", True))
# the same family for the bankai releases of Gin and Uryu: SHIKAI + item + a possible release, whole utterance only
for _cid in ("gin.bankai.release", "uryu.bankai.release"):
    GATED_ALIASES[_cid] = [("bank eye", True), ("bankeye", True), ("bank i", True), ("bank ai", True), ("bankie", True), ("banki", True),
                           ("банкой", True)]


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
            item = {"Rukia's zanpakuto": "rukia", "Byakuya's zanpakuto": "byakuya", "either zanpakuto": "any",
                    "any sword or none": "any", "Gin's Shinso": "gin", "Uryu's Quincy cross": "uryu"}[c[2]]
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
            add_unique(commands[cid], text, "A", weak, ["bankai_ready"])
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
