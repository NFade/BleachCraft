# Uryū: handoff. Read this first. NEWEST STATE: see design/results/URYU_PROGRESS.md (2026-10-15 evening: stages S0-S5 done = 60 percent, S6a partial WIP, poses merged into branch `uryu-fx-wip`, results notes uryu_ufx2/4/5/6 and uryu_poses exist). Work stage by stage with haiku for notes and sonnet for code (user rule). The text below is the older 2026-10-14 snapshot.

# (older) 2026-10-14 evening snapshot

The agents were stopped on purpose because the usage limit was running out. Nothing was lost: everything is committed or saved in a WIP commit and pushed. NOTHING from the visual effects stage was ever run in game (no harness run yet), and the WIP branch may not compile.

## 1. Where everything is
| What | Worktree (D:\MineBleach\.claude\worktrees\...) | Branch | State |
|---|---|---|---|
| Uryu gameplay + HUD + double jump + Rukia aim + names/no-kido + U-FX 0 (scaffold) + U-FX 1 (Blender models) | `gin-uryu` | `gin-uryu` (fa0ea65, pushed) | builds green, 1287+ tests, playable |
| Effects WIP of U-FX 2, 4, 5, 6 (uncommitted work saved as one WIP commit) | `gin-uryu` | `uryu-fx-wip` (pushed) | MAY NOT COMPILE, never run |
| Poses (U-FX 11), steps 1-3 committed | `uryu-poses` | `uryu-poses` (6989071, pushed) | not built after the last commit, tests for the last step missing |
| R11 merge (sounds, crystal ice wall, bankai blade v2 + our work) | `r11-merged` | `r11-merged` (e9b9d7f, pushed) | build green 1333 tests; PR not opened (no `gh` installed): https://github.com/NFade/BleachCraft/compare/gin-uryu...r11-merged?expand=1 |
Gin (design/GIN.md, models, server code) is PAUSED by the user; do not touch.
The dev game is launched from the `gin-uryu` worktree: `cd mod && ./gradlew runClient` (env JAVA_HOME=C:/Program Files/Eclipse Adoptium/jdk-21.0.12.101-hotspot, GRADLE_USER_HOME=D:/gradle-home), lock file `D:\gamelock\owner`, window goes to the second monitor (dev_monitor in D:\gradle-home\gradle.properties). Only one game at a time; ask the user to close theirs first. A game may still be running from the last launch.
An old stash entry `uryu-poses-wip-ufx11` (cbbd89f) holds the first, superseded pose WIP; it can be dropped (`git stash drop` after finding it by message).

## 2. What the user decided (binding)
- Uryu names: BASE "Heilig Bogen", SHIKAI "Blut Arterie", BANKAI "Quincy: Vollständig"; NO kido for Uryu; no reiatsu/Rage draining anywhere.
- Shunpo replaces Hirenkyaku (+2 blocks, Quincy look); shikai B = Seele Schneider melee (lunge + 3 slashes); bankai Z = wing barrage; bankai H = Licht Regen 9x9 arrow rain; bankai B = Sternenstaub (0.8 s charge, needs Focus 3); shikai Z = homing arrow; every arrow homing; Focus builds standing or sneak-walking, reset by moving/jumping/being pushed.
- Rage: 3.0 per melee hit (mobs too, no per-target cap), 5.0 per parry, drain after 30 s (defaults changed in RageConfig; the friend must edit his local config/reiatsu_test.json `rage` section: dealtTenthsPerHp 1000, maxDealtGainPerHitTenths 30, mobMultiplierPercent 100, targetCapPer10sTenths 10000, parryGainTenths 50, decayDelayTicks 600).
- Double jump for all characters in SHIKAI/BANKAI (config `doubleJump`); kido cells bottom-left, shunpo cell left-centre.
- Effects must be LARGE and canon-faithful, built from reference images (refs/uryu, git-ignored, exists only in the gin-uryu worktree).
- Storyboard open questions answered: Blut Arterie reuses Blut Vene veins (brighter, denser), bow stays in the RIGHT hand (option `uryu.bowHand` exists), Sternenstaub 0.8 s charge, Licht Regen cast-start event 78, wings permanently on in the bankai state, bracers/boots/chest emblem as meshes, Hakuteiken clash visual only, halo-free.
- Launch the game on the second monitor whenever needed.

## 3. The plan (design/URYU_VFX_STORYBOARD.md section 5 is the contract; design/URYU_PLAN.md the history)
| Step | State |
|---|---|
| U-FX 0 scaffolding + server additions S-U1..S-U4 | DONE (fa0ea65): atlas, particles, shells, `Phase7Uryu` harness + `runPhase7`, Sternenstaub charge, Focus packets, BIND/END packets, effect 78 |
| U-FX 1 Blender models | DONE (23b5133): `blender/export/uryu_fx/`, notes design/results/uryu_fx_models.md. MISSING by design of 4.1: chest emblem and Zeichen glyph meshes (add later in Blender, scene is open: blender/uryu_fx_models.blend; the user also saved a copy as D:\MineBleach\New Folder\Untitled.blend) |
| U-FX 2 bow forming, draw layers, arrow, Focus feedback | PARTIAL, in `uryu-fx-wip`: pure core written (UryuBowForm, UryuBowGlow, UryuDrawLight, UryuArrowLook, UryuFocusLook, UryuEase + tests); was editing ObjItemBakedModel/ObjItemUnbakedModel/uryu_cross.json (bow forming branch + glow multiplier) and UryuAuraFx Focus part. NOT done: UryuArrowRenderer with the new mesh/trail, UryuBowFx, harness scenarios p7_02_*, results note |
| U-FX 3 shunpo look, bankai end/seal | NOT STARTED |
| U-FX 4 Sprenger + bind visuals | PARTIAL, in `uryu-fx-wip`: pure plans (SprengerFxPlan, BindFxPlan + tests), UryuBindFx, UryuSprengerFx and UryuSeeleRenderer edits, FxDecals edit. NOT done: finished pentagon-outline render (five spokes, no star until the explosion), BIND/END handler insert, harness scenarios p7_04_*, results note |
| U-FX 5 Seele Schneider | PARTIAL, in `uryu-fx-wip`: SeeleFxMath (+test), UryuSeeleFx, Phase7Seele harness. NOT done: verify it is complete (the agent was hooking Phase7Uryu.steps when stopped), results note |
| U-FX 6 Blut Arterie + veins | PARTIAL, in `uryu-fx-wip`: UryuBlutTiming, UryuBlutFx, UryuQuincyFeature, UryuHand, UryuFlakes, vein skin textures (tools/gen_uryu_textures.py + `textures/fx/uryu_vein*`), FirstPersonHand + DrawFx + FxClient edits, UryuVeinSkinTest, Phase7Blut harness. Was finishing the second render site of the first person overlay. NOT done: results note, check slim/classic skins |
| U-FX 7+8 wings (release, state, barrage) + Z arrow | NOT STARTED (they share UryuWingFx/UryuQuincyFeature/UryuArrowRenderer: one agent for both) |
| U-FX 9 Licht Regen | NOT STARTED (after 8: needle branch in UryuArrowRenderer) |
| U-FX 10 Sternenstaub | NOT STARTED |
| U-FX 11 poses | steps 1-3 DONE on branch `uryu-poses`: tools/gen_uryu_poses.py + test, design/anim/uryu_poses.bbmodel, 10 player_animations JSON (uryu_idle/draw/release/release_z/seele/blut/vollstandig/sprenger/licht_regen/sternenstaub), core/combat (UryuAnims, UryuPoseTrack, UryuBowFlow, UryuTpPose, UryuPoseMath, UryuGestures), client UryuBowMotion + UryuTpLayer + UryuBowPose + FpCast/FpMotion hooks, option `uryu.bowHand`. NOT done: tests for the client-side wiring (the agent was writing them), `./gradlew build` after the last commit, design/results/uryu_poses.md, contact sheets (optional) |
| U-FX 12 polish (tiers, Reduce Motion, photosensitivity, FPS matrix, sounds) | NOT STARTED |
Rough completion of the whole effects block: about 30 percent.

## 4. How to resume (suggested order for tomorrow)
1. In the `gin-uryu` worktree: `git status` (it is on branch `uryu-fx-wip`), `./gradlew build`; fix compile errors first (several files were mid-edit by four agents at once). Run the full tests.
2. Poses: in the `uryu-poses` worktree run `./gradlew build`, finish the missing tests and the results note, then merge `uryu-poses` into the effects branch (expected conflicts: none in code, maybe CastGestures/NewCharacterGestures).
3. Finish U-FX 2, 4, 5, 6 (one agent each is fine; they own disjoint files, listed in the storyboard row; give each its section of the storyboard and tell them what is already written: the pure `core/fx/Uryu*`/`*Plan` classes and their tests).
4. Then U-FX 7+8, then 9, 10, 3, 12.
5. Ask the user to close the game; run `./gradlew runPhase7 -Phold=<scenario>` (harness scenarios exist per step in client/dev/Phase7Uryu, Phase7Blut, Phase7Seele; the storyboard section 6 names the screenshots p7_*.png; the harness saves into mod/run/screenshots), LOOK at the screenshots, compare with refs/uryu/*, and iterate. This visual loop is the main thing still missing.
6. Merge to `gin-uryu` when green; keep `r11-merged` separate until the user/friend open or merge the PR.
Rules for agents: they share one working tree, so give them disjoint files, `git add <own files>` only, no `./gradlew run*`, never touch the Rukia/Byakuya code, and keep shared-file edits to inserts.

## 5. Known issues and risks
- Bow in the hand: only scaled (1.8 first person, 1.4 third); pose and bow hand are in the poses branch, unverified in game.
- Rukia Tsukishiro does NOT take the end-of-cast aim (it is a circle at her feet); the user was told, no reply yet.
- The 5.5 m bankai blade and crystal ice wall (in r11-merged) are unverified in game.
- The friend works on `finish-r7`; check `git fetch` and merge it into our branches from time to time (it was conflict-free at the last check, but R11 changes arrived through `r11-integration`).
