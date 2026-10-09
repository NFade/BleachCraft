# STATUS (2026-10-09)

Fabric 1.21.1 mod `reiatsu_test` (Bleach: Rukia, Byakuya, 3 states each). Details: `LOG.md`, design contracts in `design/`.

## Done (on `main`)
- Phase 0-2: environment (JDK 21, Gradle, Blender 5.2.2 MCP), research, art bible v3, Gates A/B, ADR, OBJ item rendering spike (path B1 approved).
- Phase 3: all 6 models built in Blender, textured, verified, exported (`blender/export/`): rukia_sealed, rukia_shikai, rukia_bankai, byakuya_sealed, byakuya_shikai, byakuya_bankai, plus separate saya (scabbard) objects for both sealed models. Gate C review done and its edits C1-C4 applied (`design/GATE_C.md`).
- Phase 4: state machine (sealed -> shikai -> bankai), reiatsu, items, attachments, network, server abilities, temp blocks, HUD (functional, placeholder look), commands. Tests green.
- Step B/B2: real models in the game for both items (all states), per-object model manifests, ribbon chain animation, translucent ice blade, Pillow GUI icons, third-person curve fix (ry=180 roll), first-person arm mixin (option `show_first_person_hand`).
- Step B3: first person pose (sword 2.4x, near vertical, flat toward camera, vanilla arm), SEALED = scabbard in hand, draw animation 0.4 s along the sori arc + sheathe, `DrawEvents` hook for the release flash (see LOG "Step B3").
- Phase 5: voice. `voice-bridge/index.html` (Web Speech API), loopback HTTP server in the mod, fuzzy matcher with state/item gating. Fixture: 202/202 positives, 0/153 false positives, bridge latency about 1 ms in game.
- Design for Phase 6 (`design/VFX_STORYBOARD.md`, final, Opus): all effects, quality tiers, HUD/UI spec, implementation order, test plan.

## In progress (branches, unfinished)
- `worktree-agent-a0b75de1ae9866b58` (step B3, done, awaiting merge into `main`; also contains the WIP commit of `step-b-rukia-models`): first-person pose, scabbard hold, draw/sheathe animation. Open: user visual review (tilt/scale), Byakuya hilt small, no Fabulous check.
- `phase6-fx` (WIP commit, may not build): Phase 6 steps 0-2 (FX scaffolding: config/tiers, particle types, glow batch, ScreenFx, anchor entity, texture generators; then new HUD; then release/aura/seal effects).
- `phase5-voice`: already merged into `main` (kept for history).

## To do
1. Review and merge step B3 into `main`; phase 6 listens to `DrawEvents.EVENT` (DRAW_RELEASE = release flash).
2. Phase 6 steps 3-9 per `design/VFX_STORYBOARD.md` section 10: Byakuya swarm and bankai rows/storm, Rukia shikai and bankai (absolute zero, freeze_desat), Hakuteiken, polish (FOV kick, speed lines, custom sounds optional). Load tests: 1000 blades / 3000 petals.
3. Rukia costume set (deferred), Senkei (deferred, disabled).
4. Phase 7: dedicated server and second-client smoke test, balance play test, real microphone test for voice, README, `gradlew build` jar, Gate D (last Opus call: 1 left).
5. Known UNVERIFIED: mixin refmap in a production launcher, jdk.httpserver in the production runtime (socket fallback exists), Fabulous graphics with the translucent blade and glow layers, camera shake hook, Byakuya hand poses on a real player (tuned on armor stand).

## Notes
- Opus calls used: 5 of 6 (Gate A, ADR, Gate B, Gate C, VFX design). One left for Gate D.
- `refs/` (private reference images) is gitignored; re-download with `tools/fetch_refs.py`.
- Dev windows open on the second monitor (`dev_monitor` in `mod/gradle.properties`).
- Gradle commands need `JAVA_HOME` (Temurin 21) and `GRADLE_USER_HOME=D:\gradle-home`.
