# Voice v3: first live session (2026-10-11) and the choice of engine

Source: `mod/run/logs/latest.log` of the live test (Chrome, real microphone, ru-RU and en-US). The strings marked "log" are
real. Game, Blender and `.mcp.json` untouched.

## What the log showed

- Rukia works: `Bankai`, `absolute zero`, `Suki Shiro`, `хакурен`, `ширафуна`, `my sudano shirayuki` all fired.
- Byakuya ru-RU: the user said "Chire, Senbonzakura"; Chrome wrote `ширая Сен бонзакура`, `что сэмбонзакура`, `Сэм бонзакура`,
  `щиро сен-бонзакура`, `шире`, `чьере`, `сэмбо на` (cut off). Keys: `siraya sen bonsakura`, `cto senbonsakura`,
  `sem bonsakura`, `siro sen bonsakura`, `sire`, `cere`. The z/s fold already works (`senbonzakura` and `senbonsakura` are
  one key), so the cause was NOT z/s. Two causes: (1) the rule of VOICE_PHRASES 3.2 "Senbonzakura alone must NOT trigger",
  so the name never fired without a correct "chire"; (2) `chire` comes out as sire/siro/cere, which are different keys
  (the normaliser keeps ch and sh apart on purpose: golden keys) and 4-letter keys are exact-only.
- `чьере` and `банкой` fired only through alternative 1 (`чире`, `банкай`): the top hypothesis was wrong, the second right.
  The alternatives rescue works as designed.

## A. Engine choice (research)

Sources: MDN (SpeechRecognition.phrases, processLocally, SpeechRecognitionPhrase), Chrome 139 release notes, blink-dev
"Intent to Ship: On-device Web Speech API", Vosk GitHub and wrappers, Whisper community benchmarks. What the sources did
not confirm is marked UNVERIFIED.

| | 1. Web Speech + biasing (`phrases`, `processLocally`) | 2. Vosk with grammar | 3. whisper.cpp / faster-whisper + initial_prompt | 4. Hybrid: Web Speech + phonetic matcher (now) |
|---|---|---|---|---|
| Short foreign commands | Biasing is in the spec draft (phrase + boost 0..10), MDN marks it Experimental. UNVERIFIED whether the user's Chrome ships it and whether the cloud engine honours it | Grammar mode limits output to a word list plus `[unk]`; a word must exist in the model lexicon, Japanese names are out-of-vocabulary in the Russian model (UNVERIFIED how to add them, needs a custom lexicon) | The prompt biases the spelling of names; short utterances are known for hallucination on silence or noise, needs VAD | Already built: 305 variants, loose key, alternatives, 202/202 and 270 dirty |
| Latency | Cloud: end-of-speech wait plus network (not measured); on-device (Chrome 139+): no network | Streaming, local | Chunked, not truly streaming; tiny/base about 0.3-0.5 s per 3 s of audio on CPU (community number, UNVERIFIED) | Same as Web Speech; matcher itself 1-12 ms in the log |
| Install for the player | None; on-device needs a language pack download that Chrome asks for | Java/JNI or Python sidecar, 50 MB+ model per language, microphone capture in the mod | Native binary or Python, model 75 MB to 1.5 GB, microphone capture, fast CPU or GPU | None |
| Licence | Browser API | Vosk code Apache-2.0, most models Apache-2.0 | whisper.cpp / Whisper MIT | none new |
| Risks | API may be absent or change (experimental); cloud engine sends audio to Google (true today already) | Small ru model quality unmeasured, no ja; grammar forces the nearest word unless `[unk]` is used, which costs recall | False commands from hallucination (worse than misses), heavy for players | Ceiling is what Chrome writes; each new mishearing needs an alias |

Recommendation: option 4 now, with option 1 added as a feature-detected step (no install, no new dependency).
Reason: false positives are worse than misses, and the phonetic matcher with state gating is the layer that guarantees
that; swapping the engine (2, 3) adds an install for the player and a new false-positive source (grammar forcing, Whisper
hallucination), while the log shows the recogniser is already close and the failures were a rule and missing aliases.
Option 3 is the fallback if the measured hit rate of a real session stays under about 90 percent after step 2.

Implementation plan (not done, needs the user's go):
1. Done now: the "heard" log line prints the alternatives; collect one more session.
2. `voice-bridge/index.html`: if `'phrases' in SpeechRecognition.prototype`, set `recognition.phrases` to the command names
   (chire, senbonzakura, bankai, kageyoshi, hakuteiken, senkei, shirayuki, tsukishiro, hakuren, shirafune, hakka no togame)
   with boost 5..8 per language, keep `maxAlternatives = 3`, show "biasing on/off" on the page. Optionally try
   `processLocally = true` after `SpeechRecognition.available()` for the language, fall back to cloud.
3. Compare hit rates (heard lines) with and without biasing on the same 30 phrases per language; keep it only if better.
4. Only if still weak: prototype whisper.cpp tiny/base as a separate local bridge with VAD and the same JSON protocol.

## B. Changes (current engine)

- `tools/gen_voice_phrases.py` -> `phrases.json` (265 to 305 variants), reasons in the script:
  - `Senbonzakura` alone is a WEAK variant of `byakuya.shikai.release` (whole utterance, fillers ignored). It works in BASE
    with Byakuya's sword only. "I like senbonzakura music", `senbonzakura kageyoshi` in BASE, SHIKAI, BANKAI, another
    sword, interim results: nothing fires.
  - Lone weak garbles of chire for the shikai release (BASE) and the petal storm (BANKAI): `shire/sire`, `шире`, `щире`,
    `чьере`, `чере`.
  - Garble + name combos (`щиро сен-бонзакура`, `что сэмбонзакура`, `ширая сен бонзакура`, `shiro senbonzakura`, ...).
  - NOT added on purpose: lone `siro/shiro/щиро` (key = the Minecraft word "silo"), `cherry/cheery/chili`, `тире`, lone
    `send bonds a cura`.
  - Kageyoshi: `cagey yoshi`, `кагаеси`, `кага еси`. Hakuteiken: `хаку тайкен`, `hacku tay ken`. Senkei: `sen key`,
    `сен кей`. Bankai: `банкой` (weak, with the "bank eye" protection: SHIKAI + sword + full reiatsu).
  - Normaliser untouched: z to s was already folded; merging ch/sh would change the golden keys.
- `server/VoiceControl.java`: the "heard" log line prints the alternatives themselves, not their count.
- `design/VOICE_PHRASES.md`: rules 1.7, 3.1(3), 3.2, matrix 3.3 (5 rows), note 3.4.
- Tests: `LiveSessionFixtureTest` (6) with `tests/voice/live_fixture_2026_10_11.json` (49 positives, 48 neutrals; the 33
  distinct heard strings of the log plus siblings); `MustNotFireTest` and `GatingTest` adapted (the name left the "alone"
  list).

## Checks

- All 33 distinct "heard" strings of the log in all 25 gate contexts: only the 5 intended ones fire (ширая/что/Сэм/шире/щиро
  + name), nothing else.
- Random sentences, 3 seeds x 28 000 (command-like vocabulary plus 1-3 character mutations of every old variant), old vs new
  phrase table, 25 contexts: 536 new firings, all from the new variants and their own mutations; 22 firings lost, all
  artificial sentences where a new variant now ties with the old command and the margin rule blocks both (for example
  `Hakuteiken shiro Senbonzakura` in BANKAI). Safe direction.
- `gradlew test`: 380 tests, 0 failures. Old fixture 202/202, dirty 270/270 and 81 neutrals, 3000 everyday sentences.

## Known price and what only a live microphone can show

- A one-word `шире` / `sire` / `Senbonzakura` while holding the drawn Byakuya sword in BASE now releases the shikai; the same
  garbles in BANKAI fire the petal storm.
- `сэмбо на` (the recogniser cut the word) is not matched.
- Check by voice: ru-RU "Senbonzakura" alone, "Чире", "Чире Сенбонзакура", 10 times each in BASE; "Банкай" and "Кагэёси" in
  SHIKAI; "Хакутэйкэн" in BANKAI; en-US the same. Then read the `heard ... alts=[...]` lines (they now show the other
  hypotheses) and add what repeats to `EXTRA_ALIASES` and to `tests/voice/live_fixture_2026_10_11.json`.
