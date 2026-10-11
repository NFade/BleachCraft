# STATUS (2026-10-10)

Fabric 1.21.1 mod `reiatsu_test` (Bleach: Rukia, Byakuya, 3 states each). Details: `LOG.md`, design contracts in `design/`.

## Done (on `main`)
- Phase 0-2: environment (JDK 21, Gradle, Blender 5.2.2 MCP), research, art bible v3, Gates A/B, ADR, OBJ item rendering spike (path B1 approved).
- Phase 3: all 6 models built in Blender, textured, verified, exported (`blender/export/`): rukia_sealed, rukia_shikai, rukia_bankai, byakuya_sealed, byakuya_shikai, byakuya_bankai, plus separate saya (scabbard) objects for both sealed models. Gate C review done and its edits C1-C4 applied (`design/GATE_C.md`).
- Phase 4: state machine (sealed -> shikai -> bankai), reiatsu, items, attachments, network, server abilities, temp blocks, HUD (functional, placeholder look), commands. Tests green.
- Step B/B2: real models in the game for both items (all states), per-object model manifests, ribbon chain animation, translucent ice blade, Pillow GUI icons, third-person curve fix (ry=180 roll), first-person arm mixin (option `show_first_person_hand`).
- Step B3: first person pose (sword 2.4x, near vertical, flat toward camera, vanilla arm), SEALED = scabbard in hand, draw animation 0.4 s along the sori arc + sheathe, `DrawEvents` hook for the release flash (see LOG "Step B3").
- Phase 5: voice. `voice-bridge/index.html` (Web Speech API), loopback HTTP server in the mod, fuzzy matcher with state/item gating. Fixture: 202/202 positives, 0/153 false positives, bridge latency about 1 ms in game.
- Design for Phase 6 (`design/VFX_STORYBOARD.md`, final, Opus): all effects, quality tiers, HUD/UI spec, implementation order, test plan.

## B4 branch b4-steps2-3-wip (2026-10-10)
- Step 1 (first person pose, yawed 180 degrees on the user request), step 3 (BASE state, J key, right click) and step 2 (client side scabbard in the left hand, right hand draws along the sori arc, hip scabbard in third person) are committed locally, not merged, not pushed. 212 tests, runPhase4 89/89, runPhase5 62/62. Details: LOG B4 step 2. Steps 4 (bankai) and 5 (shunpo) not started.
- B4 polish (branch b4-polish, WIP, local): sword raised and hand lowered, scabbard leaves the first person screen after the draw, right forearm rolled in from the right edge; tests green, runPhase4 NOT clean (B2-B4 failed with another game open), runPhase5 not run. LOG: "B4 polish: stop point".

## Phase 6 step 2 closed (branch `phase6-fx`, local commit, not pushed)
- Release 2.1, auras 2.2 (Rukia bankai interim, Byakuya heartbeat), seal 2.3, `DrawFx` mapping of the B4 states, `FxTune` hot reload (`mod/run/fx_override.json`) and the live harness console (`runPhase6 -Phold=console`, `run/p6_cmd.txt`) are done; 41 screenshots in `blender/renders/p6/p6_02_*`. Build 217 tests, runPhase4 89/89, runPhase5 62/62 on the merged branch, runPhase6 release set 49/49. Details and the UNVERIFIED list: `LOG.md` "Phase 6 step 2 (closed)". Next: step 3 (Byakuya swarm), batch the code, one `runPhase6`.

## Phase 7 prep (branch `srvdocs`, local commits, not pushed)
- Task 1 done: `python -I tools/server_smoke.py` = dedicated server smoke without a game window (43 checks PASS, two protocol-level bots, restart persistence). Details and the manual second-client list: `LOG.md` "Phase 7: dedicated server smoke".
- Task 2 done: `README.md` (English plus short Russian section: requirements, build/install, controls, state flow, voice, config, known issues, credits) and a Credits section in `LICENSES.md`.
## B4 steps 4 and 5 done (branch `b4game`, local commits, not pushed, 2026-10-11)
- Step 4: bankai costs no reiatsu, 45 s timer then back to SHIKAI, free bankai abilities with cooldowns (keys U I O), shikai abilities (Z H B) also inside bankai, all areas scaled and in `BalanceConfig` (Absolute zero radius 12, Scatter 12/14, Hakuteiken burst 12, shikai x1.5-2), 60 s bankai re-entry lock. Step 5: shunpo on Y (server teleport 9 blocks along the look direction, wall and landing checks, 2.5 s cooldown, 5.0 reiatsu, SHIKAI and BANKAI), 5 translucent afterimages plus trail and sound. 253 tests. Details, numbers, UNVERIFIED: `LOG.md` "B4 step 4" and "B4 step 5"; rules in `design/STATE_MACHINE.md` section 10.

## Stop point 2026-10-10 (details and next actions: `HANDOFF_PROMPT.md`)
- `main`: B4 steps 1-3 and `b4-polish` merged (pose, scabbard left hand, BASE state, scabbard leaves the first-person screen after the draw). 214 tests; runPhase4/5 not rerun on this exact commit.
- `phase6-fx`: Phase 6 steps 0, 1 and 2 done (see the section above). Steps 3-9 not started.
- Not started: B4 step 4 (bankai timer and bigger radii) and step 5 (shunpo).

## In progress (branches, unfinished)
- `phase6-fx` (WIP commit, may not build; branch point is older than step B3, rebase or merge `main` first): Phase 6 steps 0-2 (FX scaffolding: config/tiers, particle types, glow batch, ScreenFx, anchor entity, texture generators; then new HUD; then release/aura/seal effects).
- Step B3 (first-person pose, scabbard, draw/sheathe animation) is DONE and merged into `main` (open: user visual review of tilt/scale via `first_person_scale_multiplier` / display json, Byakuya hilt small, no Fabulous check).

## To do
1. Phase 6 listens to `DrawEvents.EVENT` (DRAW_START / DRAW_RELEASE = release flash, SHEATHE_START / SHEATHE_END); the model swap happens in the DRAW_RELEASE tick.
2. Phase 6 steps 3-9 per `design/VFX_STORYBOARD.md` section 10: Byakuya swarm and bankai rows/storm, Rukia shikai and bankai (absolute zero, freeze_desat), Hakuteiken, polish (FOV kick, speed lines, custom sounds optional). Load tests: 1000 blades / 3000 petals.
3. Rukia costume set (deferred), Senkei (deferred, disabled).
4. Phase 7: dedicated server and second-client smoke test, balance play test, real microphone test for voice, README, `gradlew build` jar, Gate D (last Opus call: 1 left).
5. Known UNVERIFIED: mixin refmap in a production launcher, jdk.httpserver in the production runtime (socket fallback exists), Fabulous graphics with the translucent blade and glow layers, camera shake hook, Byakuya hand poses on a real player (tuned on armor stand).

## Notes
- Branches pushed to origin on 2026-10-10: `b4-fixes`, `b4-steps2-3-wip`. `main` is stable (step B3, 192 tests green) and was NOT changed by B4.
- Last update: `main` builds, 192 tests green (`mod/gradlew build`). Pushed to https://github.com/NFade/BleachCraft.
- Handoff tip for a new account: read this file and `LOG.md` tail, `git fetch`, then continue with To do item 1 (phase 6 steps 3-9). Local dev_monitor is per machine: put `dev_monitor=x,y` into `%GRADLE_USER_HOME%\gradle.properties` to override the repo value.
- Opus calls used: 5 of 6 (Gate A, ADR, Gate B, Gate C, VFX design). One left for Gate D.
- `refs/` (private reference images) is gitignored; re-download with `tools/fetch_refs.py`.
- Dev windows open on the second monitor (`dev_monitor` in `mod/gradle.properties`).
- Gradle commands need `JAVA_HOME` (Temurin 21) and `GRADLE_USER_HOME=D:\gradle-home`.
- T2-T4 done on branch `tp3b` (local, not merged/pushed): hip scabbard checked, smooth hip-to-hand blend, third-person katana swing (LOG 2026-10-11 "T2-T4 finished"; sheets in `blender/renders/tp3/`). Previous known defects (hip-to-hand jump, no swing seen from outside) are closed there.

## STOP POINT 2026-10-10 (evening), parallel work paused on the user's request
Four worktree branches were running in parallel, all stopped and pushed. None is merged into `main` yet. Base of all four: `phase6-fx` at `9137f97` (= `main` B4 steps 1-3 + polish + Phase 6 steps 0-2 closed). Open order to continue (details in each branch's LOG.md "STOP POINT" section):

| Branch | State | Next action |
|---|---|---|
| `phase6-fx` (`303c1b8`) | Phase 6 steps 3-4 (Byakuya swarm, bankai rows) WIP, may not build, screenshots not reviewed | `gradlew build test`; finish steps 3-4 per `design/VFX_STORYBOARD.md` section 10; then step 5 storm and step 8 Hakuteiken |
| `p6rukia` (`60471d4`) | Phase 6 steps 6-7 (Rukia shikai, bankai, Absolute zero) just started: core classes/tests only | continue steps 6-7; take radii from the server event params / BalanceConfig |
| `b4game` (`cade3a7`) | B4 step 4 (bankai 45 s timer, no reiatsu drain, return to SHIKAI, larger radii in `BalanceConfig`) + step 5 (shunpo) in progress, existing tests were being updated | make it build; finish per `design/FIXES_B4.md`; defaults: shikai abilities cost reiatsu in bankai, shunpo shared, free key (verify vs vanilla) |
| `srvdocs` (`f52f169`) | DONE and committed: dedicated server smoke test (`tools/server_smoke.py`), README (EN+RU), `docs/QA_CHECKLIST.md`, LICENSES credits | review, merge first (no conflicts expected with effects code) |

Merge order recommendation: `srvdocs` -> `b4game` -> `phase6-fx` -> `p6rukia` (expect conflicts only in LOG.md/STATUS.md and shared FX scaffolding; resolve keeping both sides). Then Phase 6 steps 5, 8, 9, then third-person defects (hip-to-hand jump, swing animation seen from outside), then Phase 7 QA and Gate D (last Opus call).
Rules for parallel agents (kept): one game window at a time via `bash /d/MineBleach-locks/game_lock.sh acquire|release <name>` (lock dir is outside the repo, on the machine of whoever runs; on another machine create an equivalent), batch code first then one harness run, 16 GB RAM means at most ~4 agents.

## STOP POINT 2026-10-11 (user request: stop all, commit, push)
All agents stopped, game closed, lock released. Uncommitted work saved as WIP commits on each branch (each branch LOG.md has a "STOP POINT" section). Nothing merged into `main` except the notes.

| Branch | Last commit | State | Next |
|---|---|---|---|
| `srvdocs` | f52f169 | DONE: dedicated-server smoke test, README (EN+RU), QA checklist, credits | review and merge first |
| `b4game` | 0cf6c9e | WIP: bankai 45 s timer, shunpo; tests being updated | build, finish B4 steps 4-5 |
| `phase6-fx` | 4cc0c4c | WIP: Byakuya swarm + bankai rows (steps 3-4) | build, finish steps 3-4, then storm (5), Hakuteiken (8) |
| `p6rukia` | d41d9e8 | WIP: Rukia shikai + bankai + Absolute zero (steps 6-7) | build, finish steps 6-7 |
| `tp3` | f306d70 | WIP: third-person scabbard, draw transition, swing animation (T2-T4) | build, finish T2-T4 per design/TASKS_BACKLOG.md |

Merge order: srvdocs -> b4game -> phase6-fx -> p6rukia -> tp3 (LOG.md / STATUS.md conflicts: keep both sides). Voice task T1 (vox-arcana ideas) is not started, see design/TASKS_BACKLOG.md.

## 2026-10-11: tp3b
T2-T4 finished on `tp3b` (from main): build+test green (320), runPhase4 151 checks, runPhase5 62/62. Merge `tp3b` into main instead of the WIP `tp3` commit. Open: second real client, Fabulous, production refmap check (see UNVERIFIED in LOG).

## 2026-10-11: T1 voice bridge (branch `voice2`, local, not merged/pushed)
Done: voice page with prepared hold-to-talk session (optional muted-track pre-warm), race-safe session replacement, fallbacks, volume meter, last-30 latency card, Lab card; `gradlew voiceLab`, `tools/gen_voice_fixtures.ps1`, `tools/voice_lab_e2e.mjs`, 26 Node tests, `VoicePageTest`. Build 324 tests green, fixtures 202/202 and 0/153 unchanged, no game run (no mod code changed). Real Chrome with synthetic speech: press to ready 0 ms (plain mic start 235 to 240 ms), recogniser end-of-speech wait about 0.7 to 1.0 s, page to game 3 to 7 ms. Open: real microphone and ja-JP are manual (`docs/VOICE_QA.md`); details and UNVERIFIED in `LOG.md` "T1 voice bridge", design notes `design/VOICE_BRIDGE_NOTES.md` (incl. kido extension notes).
