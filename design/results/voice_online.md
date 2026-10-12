# Online voice (vox-arcana scheme), 2026-10-12

Goal: every player on any server (dedicated, LAN, VDS, single player) controls the zanpakuto by voice through the own browser.
Not committed. Not run in game (rules: no runClient / runServer); everything below is unit tested, in-game parts are marked UNVERIFIED.

## Scheme

    browser page (own PC) -> POST /voice -> client bridge 127.0.0.1:47821 -> voice_text (C2S) -> server
                                                                    <- voice_feedback (S2C) <-

Speech recognition stays in the browser (Web Speech API); only text reaches the game server. Phrase matching, gating by state /
reiatsu / Rage / sword in hand, interim safety, debounce, global gap, rate limit and length limits are all on the SERVER, per
player. The command runs for `context.player()` of the packet through the same `ZanpakutoManager.performTransition / performCast /
performKido` as the keys. The old branch "owner of the integrated server" and the server-side HTTP bridge are removed: single player
uses the same client bridge and packets.

## Files

New (all in `mod/src/main/java/dev/minebleach/reiatsutest/` unless noted):
- `core/voice/VoiceWire` (limits: protocol 1, text 200, alts 3, decode bounds), `VoiceRequest`, `VoiceFeedback` (+ `Verdict`),
  `VoiceIngest` (server logic with `Host` interface, per player processor / rate window / last language; no Minecraft imports).
- `net/VoiceTextC2S` (`reiatsu_test:voice_text`), `net/VoiceFeedbackS2C` (`reiatsu_test:voice_feedback`), registered in `ModNetworking`.
- `voice/ClientVoiceBridge` (the `VoiceBackend` of the client bridge; `Link` interface for the game side, no Minecraft types),
  `voice/VoicePage` (page bytes).
- `client/.../client/voice/VoiceBridgeClient` (client source set): starts / stops the bridge on JOIN / DISCONNECT, `Link` to the game,
  S2C receiver, chat link, client command `/voicelink`.
- Tests: `core/voice/VoiceIngestTest` (13), `net/VoicePacketCodecTest` (10), `voice/ClientVoiceBridgeTest` (16).

Changed:
- `server/VoiceControl`: rewritten to packet receiver + `VoiceIngest.Host` (dispatch code kept); dedicated server no longer says
  "only single player". Per-player state is dropped on DISCONNECT. Logs: `[voice] <player> "text" (lang, final=..) key=.. via .. -> cmd conf => RESULT state=..`,
  `[voice] <player> heard "..." (lang) key=.. alts=[..] -> outcome` (INFO, finals only), debounce at DEBUG.
- `voice/VoiceHudState`: `pendingCommand` + `fromResultCode`; class doc. `client/hud/HudModel.requestFromVoice`: uses the pending command first.
- `client/ClientOptions`: `voice.client.enabled`, `voice.client.port` (and optional `portSearch`) in `config/reiatsu_test_client.json`;
  a missing `voice` section is added to an existing file, other keys untouched.
- `client/ReiatsuTestClient` (init), `client/dev/Phase5Harness` (uses `VoiceBridgeClient` for port / url), `server/ReiatsuCommand`
  (`/reiatsu voice` now reports the server side), lang en_us / ru_ru (new keys `voice.client_off`, `voice.server_disabled`, `voice.server_missing`; `voice.status`, `voice.off` reworded).
- Docs: `voice-bridge/README.md` (section "Online: scheme as in vox-arcana"), `design/VOICE_BRIDGE_NOTES.md` (section 9), `README.md`.
- `voice-bridge/index.html`: NOT changed.

## Protocol

`voice_text` C2S: varint protocol, varint reqId, string text, bool final, string lang, varlong utt+1, varint n + n alt strings.
`voice_feedback` S2C: varint reqId, byte verdict, strings commandId / heard / reason / state, byte ResultCode (-1 none), varint confidence permille.
Verdicts: OK 0, NO_MATCH 1, GATED_OUT 2, DENIED 3, IGNORED 4, PENDING 5, REJECTED 6. Reasons: processor outcome names
(`no_match`, `gated_out`, `debounced`, `interim_ignored`, `empty`, ...), `fired`, and for REJECTED: `disabled`, `rate_limited`,
`too_long`, `bad_protocol`, `no_session`, `internal`; client side only: `no_server_support`.

Decisions to know:
- A disabled server (`voice.enabled = false`) runs nothing and looks at nothing, but answers `REJECTED / disabled` so the player
  gets a one-time chat hint instead of silence.
- Rate limit counts every request of a player (also rejected ones) in a sliding second; the bridge itself limits to 20/s.
- Interim results are forwarded (the server ignores them unless `interimEnabled`), because the page waits for each reply.
- `PENDING` is a pre-notice before the command runs: `action_result` (seq -1) arrives before the final verdict and the HUD needs the command.
- The page reply waits at most 1.5 s for the server (`server_busy`; a late answer still updates the HUD).

## Checks run

- `./gradlew test`: green (80 test classes, new ones above; CorePurityTest, VoiceHttpTest, VoicePageTest, VoiceProcessorTest, MatcherFixtureTest, Kido / KidoJa / Live / Dirty fixtures unchanged and green).
- `./gradlew compileClientJava`: ok. `node --test tests/voice/*.test.mjs`: 44 / 44.

## Checklist for the game (two clients + dedicated server) - UNVERIFIED

1. Dedicated server with the mod, `voice.enabled` true. Join with client A and client B (B via runClient2 or another PC). Each chat shows
   `Voice bridge ready: http://127.0.0.1:4782x/` (two clients on one PC: 47821 and 47822). Server log: `online voice ready ... dedicated server`.
2. A opens its link in Chrome / Edge, Start, mic. Page badge: `game: connected (A)`, state badge, chips with the phrases for A's sword.
3. A draws the sword and says the release phrase: A goes to SHIKAI, B is untouched. HUD of A: phrase toast with a check mark; server log has `A "..."`.
4. B (other sword, e.g. Byakuya) says the same phrase at the same time as A: only the owner of the matching sword acts; B sees `gated_out`.
5. Both say different commands within the same second: both run (debounce / gap are per player).
6. Denied cases for A: bankai below a full bar and Rage under 80: red toast on A only; cooldown phrase: cooldown toast.
7. Spam test: send more than `maxRequestsPerSecond` texts in a second (page "Type a phrase"): `rate_limited`, no command for the spammer, B unaffected.
8. Set `voice.enabled=false` on the server and restart: A says anything: one chat hint "Voice control is disabled on this server", nothing runs.
9. Leave and rejoin: the chat prints the link again, the same page keeps working after a refresh; `/voicelink` repeats the link.
10. Single player: the same link and flow (the integrated server uses the same packets). HUD mic icon: grey `not connected` until the page polls, then listening / hearing.
11. Client without the bridge (`voice.client.enabled=false`): chat says it is off, no port opened (check `netstat`).
12. PTT key, language switch, interim off: as before; the page needs no reload when the server restarts (status turns to `open a world` and back).

## Known limits / UNVERIFIED

- Not run in game: Fabric `canSend` / handshake for the new channels, the `JOIN` chat message timing, `/voicelink` client command, HUD on a remote client.
- `/status` chips are computed on the client from synced state (ms stale at most, `Rage` not considered, same as before); the decision is the server one.
- Latency adds the network round trip; the pre-existing local measurements (`latencyMs`) now include it, `browserToAppliedMs` was dropped.
- A server without the mod cannot take voice: the page shows `game: open a world` (status `reason: server_without_voice`; page text not changed).
- The client config has no `allowNullOrigin` switch; the bridge uses the default (true, as before). The hosts / origin checks of `VoiceHttp` are unchanged.
- Dev harness `Phase5Harness` was only adapted to compile (port from `VoiceBridgeClient`); it was not run.
- Languages offered by the page come from the client defaults (`ja-JP`, `en-US`, `ru-RU`), not from the server config.
