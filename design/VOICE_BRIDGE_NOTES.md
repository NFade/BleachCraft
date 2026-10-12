# VOICE BRIDGE NOTES (T1, 2026-10-11)

What was taken from `IzumiSatoshi/vox-arcana`, what was not, how the page behaves per language, what was measured, and the
extension points that are only **designed** here (no code). Companion docs: `docs/VOICE_QA.md` (manual checklist),
`voice-bridge/README.md` (use), `design/VOICE_PHRASES.md` (the phrase contract, unchanged).

## 1. vox-arcana: ideas used, ideas left, licence

Looked at read-only in a clone outside the repo (`public/js/voice.js`, `voice-lab.html`, `logs/2026-09-2*.md`, README). Licence MIT
(Copyright 2026 IzumiSatoshi). **No code was copied**: the page's session controller, stats and lab are written from scratch
for this page (different state machine: continuous listening plus an optional hold-to-talk mode). Ideas only, so no obligation
beyond a courtesy line in `LICENSES.md`.

| Idea | Used here | Notes |
|---|---|---|
| Prepared session: recognition runs on a muted Web Audio track, the press opens the gain | yes, **hold to talk + "Pre-warm"**, opt-in | Their own later log reports that on a real microphone the track path returned no words for the user and they went back to ordinary `start()` kept running between casts. We therefore keep it opt-in with automatic fallback (see 3) and also implement the plain "prepared but ignoring results until pressed" variant |
| Replace a session only after `end`/`abort`, 1 s cap | yes | `retire()` / `completeDrain()`; unit-tested incl. an engine that never sends `end` |
| Fallback chain on errors | yes | track -> plain microphone; on-device -> cloud; transient -> backoff; permission/language -> stop with a message |
| Local recognition as a preference, never a download | yes, checkbox, default off | `SpeechRecognition.available({langs, processLocally:true})`, only `available` is used, `install()` is never called |
| Metrics, last 30, no words/audio | yes | sessions and utterances, see 4 |
| Lab page with synthetic fixtures | yes | Lab card in `index.html` + `tools/gen_voice_fixtures.ps1` + `tools/voice_lab_e2e.mjs` |
| Volume feedback from the envelope (attack 25 ms, release 120 ms) | yes | mic glow and a level bar; also the source of "voice onset/end" timestamps for the metrics |
| LLM / embedding spell interpretation | **no** | MASTER_PROMPT section 10: the matcher stays deterministic Java |

## 2. What changed

- `voice-bridge/index.html`: session controller (`createRecognizer`, no DOM, tested in Node), hold-to-talk with prepared session,
  race protection, fallbacks, volume meter and glow, latency card, lab card (capabilities, 3 s microphone measurement, WAV fixture
  runner). Wire protocol to the mod unchanged (`POST /voice`, `GET /status?mic=`), so no change in `VoiceHttp`, the matcher or
  the phrase data. The default behaviour is still "always listening", with faster restarts (0 ms instead of 250 ms after a
  normal end; backoff only after failures) and no overlapping sessions.
- `mod/src/test/.../VoiceLabServer` + `gradlew voiceLab`: the real `VoiceHttp` and matcher without Minecraft (ungated mode).
- `tests/voice/session_core.test.mjs` (26 tests), `VoicePageTest` (JUnit page lint).
- `tools/gen_voice_fixtures.ps1` (Windows TTS to 16 kHz mono WAV, git-ignored output), `tools/voice_lab_e2e.mjs` (drives a real Chrome).

## 3. Failure handling (page side)

| Situation | Behaviour |
|---|---|
| Language/mode/option switch while listening | old session aborted, new one starts only after its `end` (1 s cap); stale results/errors of the old session are dropped |
| Natural `end` (Chrome ends continuous sessions) | restart at once; a session that dies before any audio counts as a failure |
| `network`, `audio-capture`, others | banner, backoff 250, 500, 1000 ... 4000 ms; after 6 failures in a row stop with a message (Start again) |
| `not-allowed`, `service-not-allowed`, `language-not-supported` | stop with a message (no endless retry) |
| On-device error (`language-not-supported`, `service-not-allowed`, `network`) | that language is marked failed for the page lifetime, restart on the cloud service, notice shown |
| `start(track)` missing / throws / error on the track path | track path disabled, restart on the plain microphone, notice shown |
| Pre-warmed press heard sound but returned no text | track path disabled, notice shown (their "silent failure" lesson) |
| No final within 1.5 s after release | the last interim is delivered as the final (flag `promoted` in the metrics); the matcher's final threshold still applies |
| Game unreachable / POST error | "no reply" in the log; nothing is queued (a stale command must never fire late) |
| Microphone signal absent for 8 s | hint under the meter |

## 4. Metrics (page memory only, last 30, numbers only)

Sessions: `startToAudioMs`, `gapMs` (dead time since the previous session ended), `prepared`, `track`, `local`, `lang`.
Utterances: `pressToReadyMs`, `readyOnPress`, `soundToTextMs`, `endToFinalMs`, `endToStableMs`, `textToFinalMs`, `rttMs`, `gameMs`,
`endToReplyMs`, `engine`, `track`, `promoted`, `lang`, `mode`. "Voice end" is the last sample above a level threshold of the
meter's own stream (so the meter must be on for those rows). `VoiceStats()` in the console and **Copy JSON** export them.

## 5. Measured (synthetic speech, real Chrome 155, dev machine, 2026-10-11)

See `LOG.md` "T1 voice bridge" for the tables. Short version:
- the muted-track prepared session is listening before the press: press to ready **0 ms in 5 of 5 rounds** (plain microphone start to ready: 235 to 240 ms);
- the recogniser's end-of-speech wait is **0.5 to 1.0 s** (final text after the last sound), the whole phrase is usually already in the interim text **about 0.3 s** after the last sound;
- page to game and back 3 to 7 ms, game side about 1 ms: "final text to effect" is far below the 400 ms budget; "voice end to effect" is dominated by the recogniser.

**Interim path (not enabled, a decision for later).** `voice.interimEnabled` already exists (needs 0.97 confidence on two
consecutive interims of one utterance, only early-OK commands). The numbers above say it would save about 0.6 s per command.
The risk is partial phrases ("Chire" before "Senbonzakura") which the matcher's weak-variant rule already handles for finals;
turning it on needs a real-voice false-positive run first (docs/VOICE_QA.md step 8 with the interim switch on).

## 6. ja-JP, en-US, ru-RU

Checked on the dev machine (Windows 11, Chrome 155) with the page's Lab:
- `SpeechRecognition.available({processLocally:true})` returns `downloadable` for all three languages (no pack installed); the page then uses the cloud service. A pack is never downloaded by the page.
- en-US: the cloud engine returns Latin text; Japanese names are heard as English words ("sure send Bon Secours" for a Western voice reading "Chire Senbonzakura"): the tier-A ASR aliases of VOICE_PHRASES section 2 exist for exactly this. A human saying the names will differ: needs the real-voice checklist.
- ru-RU: the engine returns Cyrillic for Russian words ("режим атаки", "Абсолютный ноль", "запечатать" matched) and may return Latin for loan words (it returned "bankai" in Latin for "банкай"); the normaliser folds Cyrillic, kana and Latin to one key, so both reach the same command.
- ja-JP: **not run**. No Japanese Windows voice is installed (only Zira en-US and Irina ru-RU), so no synthetic audio could be generated. The engine is expected to return kanji/kana, handled by the normaliser's known-kanji table; whether the cloud service accepts `ja-JP` from a non-Japanese Chrome is untested. Put your own ja-JP WAVs into the Lab (name them `ja-JP_*.wav`) or follow `docs/VOICE_QA.md` step 7.
- Recognition language must match the spoken language; the page's three buttons pick `lang`, all three spellings are accepted by the matcher whatever the setting.

Synthetic speech is **not** evidence of recognition quality for real people: a Western TTS voice reading romaji is a poor proxy for a
Japanese phrase. It is evidence for the plumbing (engine -> page -> HTTP -> matcher), the latencies and the fallbacks, and for
plain-word phrases (attack mode, barrier mode, absolute zero, seal; the Russian equivalents), which did match end to end.

## 7. Kido-ready extension points (DESIGN ONLY, nothing implemented; see `design/PLAN_2026-10-11.md` B5)

A command group usable without shikai (hado/bakudo spoken in any state, with or without a sword) fits the existing pieces:

1. **Phrase data**: a kido command is an ordinary command in `phrases.json` with `"states": ["SEALED","BASE","SHIKAI","BANKAI"]` and `"item": "none"`. `PhraseBook.Command#validIn` already treats `item == NONE` as "any sword or none" (that is how `common.seal` works) and checks only the state list, so **no matcher change is needed**. Add an optional `"group": "kido"` field for the status list and docs; `tools/gen_voice_phrases.py` and the md-sync test get the new section from `VOICE_PHRASES.md` (sections 1 to 4 like every command).
2. **Phrase style**: kido names are short ("Sho", "Shakkaho", "Byakurai", "Sai"), exactly the words ASR confuses with English ("show", "sigh", "by a cry"). Plan for: whole-utterance-only weak variants for the bare name, strong variants for the numbered form ("Hado no ichi, Sho", "Hado san juichi, Shakkaho"), and the three-language aliases from `research/`. Short keys (6 letters or fewer) are already never assembled from two tokens by the matcher. A neutral-phrase batch of everyday sentences with those syllables goes into section 4 of the md before any phrase is added (0 false positives is the gate).
3. **Server dispatch**: `VoiceControl#dispatch` maps command id to `ZanpakutoManager.performTransition/performCast`; a kido command needs its own cast path that does not require a machine state beyond "alive" (cost reiatsu, cooldown, effect), shared with a future key binding exactly like the voice and key paths are shared today.
4. **Status list**: `/status` currently lists the commands valid in the player's context and shows "hold a zanpakuto in your main hand" when none are; with kido commands valid everywhere the list becomes non-empty with no sword, so the page needs no change (optionally group chips by `group`).
5. **Chants**: a kido chant ("Hado no ichi ... Sho") is a prefix plus a name; the matcher's prefix rule (VOICE_PHRASES 3.2: "must not fire" prefixes) and the interim path (5 above) are the places to look at first; a long chant is another reason to measure the interim path with real voices.
6. **Page**: nothing kido-specific is needed; the page only moves text.

## 8. Not done / ideas for later

- `maxAlternatives > 1`: the engine returns several candidates per final; sending them and matching each (best margin wins) would lift recall on misheard names without any new phrase. Needs a protocol field, a matcher loop and a fixture run showing 0 false positives.
- Real-microphone run (docs/VOICE_QA.md) and a ja-JP run: manual.
- Always-listening mode fed by synthetic audio: the page uses the real microphone through `rec.start()` there, which a headless harness cannot feed (Chrome's fake audio device reached the page's meter but the recogniser returned nothing); only the hold-to-talk track path and the Lab fixtures are driven end to end.

## 9. Online: scheme as in vox-arcana (2026-10-12)

Until now the bridge ran in the integrated server only and acted for "the world owner". Now it is the vox-arcana scheme: the Web
Speech API runs in the browser of each player, the game server only receives text.

**Path.** page -> `POST /voice` on the CLIENT bridge (127.0.0.1:47821, `ClientVoiceBridge` behind `VoiceHttp`, started by
`client/voice/VoiceBridgeClient` on join of any server, stopped on disconnect) -> `VoiceTextC2S` (`voice_text`) -> server
`server/VoiceControl` -> `core/voice/VoiceIngest` (per-player `VoiceProcessor`, gate from the own state machine of the player) -> the
same `ZanpakutoManager.perform*` the keys use -> `VoiceFeedbackS2C` (`voice_feedback`) -> `ClientVoiceBridge#onFeedback` (HUD
via `VoiceHudState`, reply to the page). One path for single player, LAN and dedicated; the old "owner of the integrated server"
branch and the server-side HTTP bridge are gone (`VoiceControl` is only the packet receiver and the `Host` of `VoiceIngest`).

**Why the logic stays on the server.** The client sends text only, so it cannot fire a command that the phrase matcher and the
gates do not accept: no command id, no state, no "matched" flag crosses the wire from the client. A hand-made client can say any
phrase, nothing more (it could do that with a microphone as well). Limits enforced by the server: text <= 200 characters
(`too_long`), <= 3 alternatives, empty text ignored, `maxRequestsPerSecond` per player (`rate_limited`, a sliding second, rejected
requests count), protocol version (`bad_protocol`), `voice.enabled` (`disabled`), player without session (`no_session`). The decoder
reads a little more than the text limit (1024 characters) so a long text is answered and is no reason to drop the connection; a text
beyond that, or more than 8 alternatives, fails to decode (the vanilla reaction to a malformed packet).

**Wire.** `voice_text`: varint protocol, varint reqId, string text (<=1024 on decode), bool final, string lang (<=16), varlong utt+1,
varint n + n strings. `voice_feedback`: varint reqId, byte verdict, strings commandId / heard / reason / state, byte ResultCode (-1 =
none), varint confidence permille. Verdict codes: OK 0, NO_MATCH 1, GATED_OUT 2, DENIED 3, IGNORED 4, PENDING 5, REJECTED 6
(append only). `PENDING` is sent before a command runs so that `HudModel.requestFromVoice` knows which command the next
`action_result` (seq -1) belongs to; the final verdict follows. `heard` is the normalised key, the HUD shows the text the page sent.

**Page compatibility.** The bridge answers with the same JSON as before (`matched, command, confidence, reason, text, result,
accepted, latencyMs, state`; `/status`: `world, player, state, held, say`, new: `serverVoice`, `last`, `reason: "server_without_voice"`),
so `index.html` is unchanged. A request waits up to 1.5 s for the answer of the server (`server_busy` otherwise; a late answer still
updates the HUD). `latencyMs` is now measured by the bridge (page post to answer), `browserToAppliedMs` is gone (clock of another PC).

**Status.** `/status` is built on the client thread from the synced attachments (state, sword in hand, reiatsu full) and the
bundled phrase book, so the "what to say" chips follow the gate of the server; the authoritative decision is still the server one.

**Tests.** `VoiceIngestTest` (two players, per-player state and rate limit, disabled, limits, alternatives, interim),
`VoicePacketCodecTest`, `ClientVoiceBridgeTest` (fake link, plus two bridges over real loopback HTTP to one in-process ingest,
guards, start / stop, port search). UNVERIFIED in game: the Fabric channel handshake (`canSend`), the chat link on join, the HUD
on a second client; see `design/results/voice_online.md`.
