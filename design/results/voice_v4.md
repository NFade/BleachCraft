# Voice v4 (2026-10-11): ja-JP kido, bridge page watchdog, always-on voice indicator

Input: live test of the v3 build (kido, Rage, BC). Game not run by this work; everything that needs a microphone or the game
screen is marked UNVERIFIED and listed in section 5.

## 1. What the logs say (latest.log of the session 22:27-22:48, `[voice]` lines)

Session totals: 22 voice lines, ja-JP 16, ru-RU 6, en-US 0. No HTTP 429, no `rate`, no rejected origin anywhere in `latest.log`, `debug.log`
or the older `debug-*.gz` (the only "RATE_LIMIT" hits are the unrelated per-ability cast limiter). `maxRequestsPerSecond=20`,
`globalGapMs`, `debounceMs` never produced a `DEBOUNCED` line for a real utterance. So the server side did not lose anything.

Japanese kido, the only kido attempts in ja-JP (user said 破道の四 白雷):

| time | Chrome returned | server result | why |
|---|---|---|---|
| 22:31:56-22:32:02 | `くらい` (3 interims + final) | `no_match` key `kurai` | the first syllable びゃ was lost; `くらい` is a real word |
| 22:32:05 | `は` | interim | pause split the utterance |
| 22:32:07, 22:32:08 | `波動` (final, twice) | `empty`, DEBUG only, not even in the INFO log | 波動 is the common kanji for はどう; the key table only knew 破道, unknown kanji are dropped, so the key was empty. Hado alone must not fire anyway |

Nothing at all was logged for 赤火砲 or 縛道 in this session (the user's report says they did not work either). Root causes:
1. Chrome ja-JP does not write the "official" kanji (破道, 白雷, 赤火砲). It writes the most frequent homophone (波動, 百雷, 借家法
   for しゃっかほう, ...) or loses syllables. Each of those was an empty key or an unknown word.
2. A final the normaliser reduced to nothing (`EMPTY`) was invisible: no INFO log line, no HUD toast. v4 logs it
   (`heard "波動" (ja-JP) key='' ... -> empty`) and shows it in the HUD. This is also why the problem was hard to see.
3. Other ja-JP observations (not kido): 袖の白雪 came out as `筆の白雪`, `前袖の白雪` (matched via fuzzy), `白雪` alone was empty,
   `卍解` was first written `挽回` (already in the table), `月白` fine.

ru-RU: `XADO 4 bakurai` (Chrome wrote a Latin "XADO" for ru-RU) fired kido.byakurai at 0.963; `до 1` fired kido.sai via
alternative 1; `всяко` x3 for Сякахо (no match, key `vsako`; alternatives `всякухо`, `вся кухо`, `всякую хо` are close to Сякухо =
`sakuho` but the matcher needs exactly `sakuho`/`sakaho` for a 6 letter key). Left as is (see 4, ru-RU).

## 2. Kido in Japanese (what changed)

- `core/voice/Normalizer` KANJI table: homophones of the three spells and of the category words. Hado: 波動, 覇道, 破堂, 破導, 歯道
  (plus 破道); Bakudo: 爆道, 爆動, 縛堂; Byakurai: 百雷, 白来, 百来, 薄雷, 白蕾, 白らい, 百らい, びゃく雷, ひゃく雷; Shakkaho: 借家法, 借家砲,
  赤火法, 赤火方, 赤火報, 赤化砲, 赤化法, 射火砲, 石火砲, 尺火砲, しゃっか砲, しゃか砲, 赤火ほう, 赤火ほ, 赤か砲.
- Number after the category word, any script: `{破道,波動,覇道,破堂,破導,歯道,はどう}{の,}{四,4}` is `hado (no) yon`, `...{三十一,31}` is
  `hado (no) sanjuichi`, `{縛道,爆道,爆動,縛堂,ばくどう}{の,}{一,1}` is `bakudo (no) ichi`. So `波動の4 借家法` has the same key as the
  documented `Hado no sanjuichi, Shakkaho`. The three Japanese rows of VOICE_PHRASES 1.15-1.17 changed their printed key
  (`hado no yon byakurai`, `hado no sansuici sakaho`, `bakudo no ici sai`; the numeral used to be dropped), `NormalizerTest` checks this.
- katakana is turned to hiragana BEFORE the kanji table (so シャッカ砲 meets しゃっか砲).
- `くらい`: a weak (whole utterance only) alias of kido.byakurai, the one thing the live log proved. A kanji right before or after
  くらい turns it into ぐらい first (`百くらい`, `半分くらい`, `くらい部屋` can not become byakurai).
- `tools/gen_voice_phrases.py`: `JA_KIDO` block (108 spellings: kanji homophones, hiragana, katakana, digits, romaji with
  long vowels, glued and spaced hado + number + name, weak lone `塞 さい サイ ばくどう バクドウ 縛道`). `phrases.json`: 486 variants (was 390).
  Rejected on purpose, found by the old tests: variants `hado no 4`, `hado no 31`, `bakudo no 1`, `バクドウ1` (the matcher's
  "dropped no" and one-slip rules make `hado no` fire byakurai and `bakudo` inside a sentence fire sai).
- Not mapped: one-kanji common words for さい (再, 最, 才, 歳): `最近` would become `sai`. A lone `さい` / `サイ` / `塞` works.
- Tests: new fixture `tests/voice/kido_ja_fixture.json` (71 positives, 61 neutrals) and `KidoJaVoiceFixtureTest` (6 tests, incl. the
  key checks, "Hado alone never fires in any script", "くらい is weak"). Older fixtures (202, dirty, live, kido) are untouched and still
  green; `KidoVoiceFixtureTest` re-runs their neutrals against the new variants.
- Server: finals with an empty key are now logged at INFO and shown in the HUD; a debounced duplicate final no longer overwrites the
  HUD result of the first one (the Web Speech API repeats finals, the toast used to flip to "no command").

## 3. Why the page hangs ("voice stops being read", worse on ru-RU) and what was done

Read from the controller (`createRecognizer` in `voice-bridge/index.html`); nothing in the game log points to the server.

1. No watchdog. Chrome's cloud recogniser can stall without `onerror` and without `onend` (service hiccup, network flap). The old
   code then waits forever: `session` stays set, `startSession` returns at once, nothing restarts it. Fixed: every second the live session is
   compared with its last recogniser event (`onstart`, `onaudiostart`, sound/speech edges, `onresult`, `onerror`, `onnomatch`).
   No event for 25 s (`idle`), no audio 8 s after a start (`start-timeout`, 45 s for the very first start because of the permission
   prompt), or "loud microphone but silent recogniser for 7 s" (`deaf`, needs the volume meter) replaces the session. At most one
   restart per 3 s, never under a held press. Also an "orphan" guard: wanted but nothing running or scheduled for 2.5 s.
2. Give-up after 6 failures. Six `network` / `audio-capture` errors in a row (about 8 s of back-off) set `want=false` and showed a banner
   the player never sees in the game window. Fixed: the page runs with `keepAlive`, it keeps retrying (250 ms ... 4 s, then every 10 s) and says so.
   The old 6-and-stop contract stays the default of the controller (unit test unchanged).
3. Back-off reset by `onaudiostart`. A service that opens, gives audio, then answers `network` / ends within a second reset `failStreak`
   every time, so the retry stayed at 0-250 ms: 4 sessions a second against Google's endpoint, which is a good way to get throttled
   and then fail for minutes (suspected ru-RU / long-session instability; UNVERIFIED). Fixed: a session that dies young (< 2.5 s)
   without any result counts into `quickStreak` and grows the delay from the second one; a result clears it. A normal "no-speech" end after
   several seconds still restarts at once.
4. Background tab. The player is in the game, the page is in a hidden or covered tab. Chrome slows timers of such tabs (1/s, and
   1/min after about 5 minutes for pages without audio or WebRTC), so retries, the 1 s drain cap, the 2.5 s `/status` poll (the HUD went
   "off" after 6 s) all stretched. Fixed: a tiny blob Web Worker ticks every second (CSP got `worker-src blob:`), the page calls `rz.tick()`
   from it; `tick()` runs a late retry and a late drain by hand and polls the game. Falls back to `setInterval` if the worker is refused.
   UNVERIFIED in a real hidden tab.
5. Language switch race: checked, the retire/drain/queued logic is sound (the existing race tests cover it); `tick()` additionally finishes a
   drain whose timer was throttled.

New on the page: status line (Listening / Hearing you / Starting / Reconnecting / Restarting recognition (reason) / Problem (last
error)), restart counter with the last reason, and a per-language health line in Settings (sessions, results, restarts, short sessions,
error codes; numbers only, also `__voiceDebug.health()`). The mic icon turns amber while restarting and `/status?mic=restarting` makes the
game HUD show "connected, not listening".

ru-RU: the log shows no ru-specific error code; ru-RU sessions worked (several finals). What is visible: Chrome writes Latin for
Japanese-sounding words in ru-RU (`XADO 4 bakurai`) and many short interims. The health line will show if ru-RU sessions are short or
error-prone; hypothesis to check with it: more `network` / short sessions on ru-RU than on ja-JP/en-US (then it is the service, not the page).

Tests: 11 new node tests in `tests/voice/session_core.test.mjs` (now 37): idle stall, events keep alive, start-timeout (first and later),
deaf, restart gap and held press, late timers via `tick()`, quick-death back-off growth, normal end stays instant, keepAlive slow retry and
recovery, per-language health without words, stop cancels the watchdog. `VoicePageTest` +1 (status line, `rz.tick()`, `keepAlive`, `worker-src blob:`).

## 4. HUD indicator

- `PlateHud.drawVoiceRow` is gone; new `client/hud/VoiceRowHud`, drawn from `ReiatsuHud.render` right after the plate and independent of
  it. Before: it lived inside `PlateHud.draw`, which returns when the plate is hidden (SEALED with an empty hand), and it returned when no
  page had ever been in touch. Now: shown whenever `VoiceHudState.indicatorWanted()` (bridge listening in this JVM, set by `VoiceControl`, or a
  page has been in touch), in every state, with or without a sword.
- Icon: same 7 frame sheet, no new atlas. Page in touch within 6 s: idle / listening (green pulse) / hearing (accent, animated) / error
  (red). Not connected: the crossed-out microphone (frame 0), dimmed to 55 %.
- Next to it one text line: last heard phrase with its verdict (green check accepted, dash no match / not available, red cross
  denied), full brightness for 8 s and then dimmer until the next phrase; no phrase yet: "Voice: listening" etc.; not connected: "Voice: bridge
  not connected"; error: "Voice: microphone or service error". With the rage bar on screen the line moves 16 px down. The old phrase toast (interim
  line, final line, verdict, bankai flash) is unchanged and replaces the line while it shows (2.5 s). `voiceHud=false` in the client config still hides everything.
- Keys (en/ru): `hud.reiatsu_test.voice.state.{off,idle,listening,hearing,error}`.
- `VoiceHudState`: `bridgeUp` flag, `indicatorWanted()`, `effectiveMic(wall)` with `CONTACT_TIMEOUT_MS = 6000`; test added to `VoiceHudStateTest`.
- Not done: the legacy HUD (`hud=false` in the client config) has no voice row at all (it never had one).

## 5. To check with a live microphone / in the game (all UNVERIFIED)

1. ja-JP: say `破道の四 白雷`, `破道の三十一 赤火砲`, `縛道の一 塞` and the short forms `白雷`, `赤火砲`, `さい`. Look at the INFO line
   `heard "..." (ja-JP) key=...`: any new spelling Chrome produces goes into `JA_KIDO` / the KANJI table. Is `くらい` for `白雷` repeatable?
2. Page: leave it 5+ minutes in a covered tab while playing; speak; the status line should never stay on "Listening" with the restart counter
   climbing without effect. Switch the network off for 20 s: status "Reconnecting/Problem", then recovery without pressing Start.
3. Read the health line for ru-RU after a session; compare short sessions and error codes with ja-JP.
4. HUD: SEALED, empty hand, page closed: dim crossed-out microphone and "bridge not connected"; open and start the page: icon
   goes to listening within 3 s; say anything: phrase and verdict stay next to the icon after the toast fades; F1 hides it.
5. Rage bar visible together with the voice line: placement (16 px down) is a guess.
