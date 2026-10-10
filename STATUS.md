# STATUS (2026-10-10, pause)

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

## Stop point 2026-10-10 (details and next actions: `HANDOFF_PROMPT.md`)
- `main`: B4 steps 1-3 and `b4-polish` merged (pose, scabbard left hand, BASE state, scabbard leaves the first-person screen after the draw). 214 tests; runPhase4/5 not rerun on this exact commit.
- `phase6-fx`: Phase 6 step 0 (scaffolding) and step 1 (HUD) done; step 2 (release/auras/seal) written in WIP commit `c84265f`, its screenshots must be regenerated (see LOG on that branch). Steps 3-9 not started.
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
