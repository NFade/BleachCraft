# Voice bridge

`index.html` is a single page (no build step, no external scripts, fonts or CDNs) that listens to your microphone with
the browser's Web Speech API (`webkitSpeechRecognition`) and posts what it hears to the game on this computer:

    POST http://127.0.0.1:<port>/voice      {"text": "...", "final": true, "lang": "en-US", "utt": 12, "t": 1760000000000}
    GET  http://127.0.0.1:<port>/status     state, held sword, the phrases that work right now

The bridge is part of the mod and runs on the client, on the PC of the player (`dev.minebleach.reiatsutest.voice`, loopback
only). It sends the text to the game server the player is connected to; the SERVER matches it against `design/VOICE_PHRASES.md`
(`mod/src/main/resources/assets/reiatsu_test/voice/phrases.json`) and, when a command fires, goes through the same server path
as the key bindings. See "Online: scheme as in vox-arcana" below.

## Open it

1. Start the game and join any world or server (single player, LAN or a dedicated server). The chat shows a link,
   by default `http://127.0.0.1:47821/`. `/voicelink` prints it again.
2. Open that link in **Chrome or Edge** (Firefox has no speech recognition). The mod serves the page itself, so the
   microphone permission is remembered and there is no cross-origin trouble.
3. Press **Start**, allow the microphone, pick the language (日本語 / English / Русский) and speak.

Opening `voice-bridge/index.html` from disk also works (`file://`); then the page talks to `127.0.0.1:47821`, or to the port
in the address: `index.html?port=47822`. The browser asks for the microphone permission on every visit in that case.
`?lang=ru-RU` picks the language, `?autostart=1` starts listening at once.

## Modes and settings (Settings card on the page)

- **Always listening** (default): one continuous recognition session, restarted at once when the browser ends it (backoff only
  after errors). Replacing a session (language switch, option change) waits for the old one to end, at most 1 s.
- **Hold to talk**: hold the button (or the PTT key, Space by default; the "Key" button next to it re-binds it, stored in `localStorage` `reiatsu.pttKey`); results only count while held plus a 0.5 s tail.
  A session is kept ready in between, so the press costs no start-up time. Option **Pre-warm** runs that session on a muted
  Web Audio track and the press only opens its volume; if the browser or the device does not cooperate the page falls back
  to the plain microphone by itself and says so.
- **Volume meter**: level bar and glow on the microphone circle; it also supplies the timing of your voice for the metrics.
  It opens a second read-only microphone stream; switch it off if recognition misbehaves on your device.
- **On-device recognition**: only when the browser reports the language pack as already installed. Nothing is ever downloaded.
- **Latency** card: last 30 measurements, numbers only. **Copy JSON**, or `VoiceStats()` in the console.
- **Lab** card: browser capability checks, a 3 s microphone measurement, and a runner for synthetic WAV fixtures
  (`tools/gen_voice_fixtures.ps1`). Query switches: `?mode=ptt`, `?prewarm=1`, `?local=1`, `?meter=0`, `?lab=1`.

Without the game: `cd mod && gradlew voiceLab` serves this page with the real bridge and matcher on `http://127.0.0.1:47831/`
(no state rules, nothing fires). Real microphone checklist: `docs/VOICE_QA.md`. Design notes: `design/VOICE_BRIDGE_NOTES.md`.

## Notes

- Chrome and Edge send the audio to their vendor's cloud for recognition: you need internet access and should be aware of
  that. The page itself only ever talks to `127.0.0.1`.
- The page restarts the recogniser by itself when the browser ends a session, as long as the button says **Stop**.
- Final results trigger commands. Interim (grey, italic) text is only shown; the mod ignores it unless
  `voice.interimEnabled` is switched on in `config/reiatsu_test.json`.
- The "Type a phrase" box sends text as if it had been spoken, for testing without a microphone.
- Speech recognition needs the right language: Japanese phrases under `en-US` are recognised poorly, so switch the
  language to match what you say. All three languages' spellings are understood whatever the setting.

## Online: scheme as in vox-arcana

Same idea as [vox-arcana](https://github.com/IzumiSatoshi/vox-arcana): speech is recognised by the Web Speech API in the browser of
EACH player; the game server never hears audio and does not recognise speech, it only gets text.

    browser page (player PC)  --POST text-->  client bridge 127.0.0.1:47821  --voice_text packet-->  server
    browser page             <--JSON reply--  client bridge                  <--voice_feedback--      server

- The client starts the bridge when it joins ANY server and stops it when it leaves. It serves this page and takes the
  endpoints described above (`POST /voice`, `GET /status`, `GET /`, same fields, `alts` included), so the page is unchanged.
- The bridge sends `voice_text` (C2S): `protocol`, `reqId`, `text` (at most 200 characters), `final`, `lang`, `utt`, up to 3 `alts`.
  The server answers with `voice_feedback` (S2C): verdict (`OK`, `NO_MATCH`, `GATED_OUT`, `DENIED`, `IGNORED`, `REJECTED`, plus a
  short `PENDING` before the command runs), command id, reason, result code, state. The HUD, the toast and the page reply come from it.
- Everything that decides stays on the server (`core/voice`): phrase matching, gating by state / reiatsu / Rage / sword in hand,
  interim safety, debounce, global gap, a rate limit per player (`voice.maxRequestsPerSecond`), the text length and alternative
  limits. A modified client can only say a phrase; the command runs for the player who sent the packet and obeys every rule.
- `/status` is computed on the client from the synced state (`state`, `held`, `say` list), plus `serverVoice` (false after the
  server refused voice) and `last` (last phrase, verdict, command, reason).
- Server `voice.enabled = false` in `config/reiatsu_test.json`: the server answers `REJECTED / disabled` and runs nothing; the
  client prints a one-time hint in the chat.

**Steps for a player**

1. Install the mod on the client (and on the server). Join the server.
2. Click the link `Voice bridge ready: http://127.0.0.1:47821/` in the chat (or type `/voicelink`) and open it in **Chrome or Edge**.
3. Press **Start**, allow the microphone, choose the language. For Hold to talk use the PTT key (Space by default).
4. Hold a zanpakuto in the main hand and speak. The HUD shows the phrase and the result; the page shows the same.

Two game clients on the same PC: the second one takes the next port (47822) and its chat link says so.

**Client config** (`config/reiatsu_test_client.json`): `"voice": {"client": {"enabled": true, "port": 47821}}`, optional
`"portSearch": 4`. The thresholds and limits stay in `config/reiatsu_test.json` on the server.

## Config (`config/reiatsu_test.json` on the server, section `voice`)

`enabled`, `port` (47821, the next 4 ports are tried when it is busy), `threshold` (0.86), `margin` (0.03),
`interimEnabled` (false), `interimThreshold` (0.97), `debounceMs` (1500), `globalGapMs` (300), `consumedTextMs` (1500),
`allowNullOrigin` (true: accept a page opened from `file://`), `maxBodyBytes` (4096), `maxRequestsPerSecond` (20),
`languages`, `defaultLanguage`, `servePage` (true). The file is written with defaults the first time the game starts. On a server
only `enabled`, the thresholds (`threshold`, `margin`, `interim*`), `debounceMs`, `globalGapMs`, `consumedTextMs` and
`maxRequestsPerSecond` apply (per player); port, origin and page settings belong to the client bridge (`port` / `portSearch`
come from `reiatsu_test_client.json`, the others are the defaults).
