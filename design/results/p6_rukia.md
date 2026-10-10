# Phase 6 Rukia side (steps 6 and 7), code only

Branch `p6-rukia`, worktree `p6rukia2`. Nothing was run in the game (no runPhase4/5/6, no runClient): every visual number is the
storyboard value or a reasoned estimate, tunable live (see "Tuning"). One run for all scenarios is still to be done.

## What was reviewed and changed (against VFX_STORYBOARD sections 3, 5, 10)

Server numbers after the b4 balance pass: Tsukishiro radius 7 (pillar 24.5), Hakuren 20 long x 7 wide (half width 3.5), 8 steps of 2.5
blocks (25 b/s), Shirafune reach 14, Absolute zero radius 12, pause 1.3 s (ENCASED at tick 40, HIT at tick 66), bankai settle lock 44 ticks.

| Area | Result |
|---|---|
| `RukiaFxParams` (core) | Hakuren event gets `params[6] = halfWidth` (server HALF_WIDTH; older events keep six numbers). New pure helpers `radiusScale` / `areaScale` and the reference radii (Tsukishiro 4, Absolute zero 10, Hakuren length 12) the storyboard numbers were written for. `RukiaServerFx.params` fills the width from the spec. Tests: `RukiaFxParamsTest` (hakuren width, scaling). |
| Tsukishiro | Everything scales with the server radius R: wind-up steps (5 per block), pillar R x H from params, crystal count (2.5 per block, 8 to 20) and size (x sqrt(R/4)), a tall spike (crystal d) every fourth rim crystal, particle counts by area law (clamped 0.5 to 2.2). Added: counter rotating inner sigil, ring pulse at the ring completion, light ring at the pillar foot and on the rim (crust), burst ring at the shatter. |
| Hakuren | Fan width = server half width (was a fixed 4); crystal total about 30 whatever the step count (`perStep = 30 / steps`, 3 to 6) so `maxCrystals` is not blown by 8 steps; rows interlock every other step; a ground ripple ring per step; flares scale with the width. **Fixed:** the crystals shatter 3 s after each step (the server's temp blocks last 60 ticks from the step, so 4.0 to 4.7 s, not a fixed 3.0). Crystals dropped by the `maxCrystals` cap no longer shatter ghosts (`FxMeshPass.shatter` guard). |
| Shirafune | Reach from params (14), orb count along the blade scales with the used length, shed particles with length, contact ring, tunables. Harness camera is now level (the ray is the look: the old 30 degree pitch put the end of the blade on the ground 3 blocks away) and the target stands on the ray. |
| Ice shells (ENCASED/FROZEN) | grow time per shell (`Shell.grow`: 0.25 / 0.4, 0.12 when an Absolute zero timeline runs = "snap on"), 8 glints at creation (spec 5.3), shatter shards 8 (12 for Absolute zero) at 5 (8) b/s, capped by `maxShardMeshes / shells`. All alphas/scales tunable. |
| Absolute zero | **Scales to the server radius from `params[3]`**: wave and frost decal reach R, wall ring sides follow R, mist and motes by area law, hanging shards fill the disc of radius R, crystals between 0.3 R and 0.9 R (never within 3 of the caster, count 8 x R/10 up to 14, size x sqrt(R/10)), time-stop ring 1.4 R, shatter ring 1.2 R, shatter burst counts by area law. Pause from `params[4]`. Timing: stop at 2.0, cracks at 3.0, shatter at stop + pause. |
| `freeze_desat` | Reviewed: post pass `PostEffectProcessor` at `WorldRenderEvents.LAST` + grey quad fallback (`FreezeDesat.forceFallback`, scenario `07_abszero_fallback_t2000`). The desat level at 3.0 s must be 0.45 (harness check). |
| Release (id 2) | Goes only through `RukiaBankaiFx.release` (verified in `FxEvents`: `RukiaFx.onEvent` runs before the generic release branch). Implosion, 32 high pillar, mist cap at y+18, mist column, snow spiral, shock ring r 12, narrowing at 1.2 to 2.0, settle at 2.0 (settle lock = 44 ticks = 2.2 s on the server, `BalanceConfig.settleTicks`). Tunables for radii, speeds, flash, shake. |
| Passive 5.2 | Frost decal following her, 6 release + 8 rim crystals (same seed on every client, rim crystals chime when grown), glints, mobs inside get a frost decal, feet motes, glints **and a breath puff every 2 s**; **new:** cold hum (resonate 0.6 / 0.15 every 4 s), snow steps (every 2 to 4 s), owner frost edge 0.06 and +0.06 while a hostile is inside. Snowfall disc, breathing sigil, ground mist, glints and cold breath are `AuraFx.rukiaBankaiExtras` (phase 6 step 2, unchanged). |
| Feature renderer (sheen, ribbons) | Fixed the inverse transform (the player renderer also scales by 0.9375 between the flip and the translate, verified with `javap -c`), whole renderer wrapped in a one time logged try/catch (a render error must not crash the client), ribbon/sheen numbers tunable. |
| Harness | Names per section 10: `p6_06_<ability>_t{...}`, `p6_07_rbankai_t{0,120,300,600,900,2000}` (was t10), `p6_07_passive_noon/midnight`, `p6_07_abszero_t{400,1000,2000,3000,3350,4500}`; targets sized for the new radii (zombies out to 6.5 / 18.5 / 5.5 / 10.8 blocks), zombies follow the caster's yaw in the side views; new hard checks: client radius / width / steps / reach / pause equal the `BalanceConfig` spec, desat 0.45 at t3000, below 0.45 at t3350, plus a `SHOT` log line with the live counts for every screenshot. |

## Scenario commands (not run)

From `mod/`, env `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot`, `GRADLE_USER_HOME=D:\gradle-home`:

```
./gradlew runPhase6 -Phold=rk6        # step 6: Tsukishiro, Hakuren, Shirafune (p6_06_*)
./gradlew runPhase6 -Phold=rk7        # step 7: bankai release, passive, Absolute zero + fallback (p6_07_*)
./gradlew runPhase6 -Phold=rkperf     # F0, F6 (absolute zero, 32 zombies), F7 (three abilities in 2 s): PERF lines in the log
./gradlew runPhase6 -Phold=rk6,rk7,rkperf     # all of it in one run
./gradlew runPhase6 -Phold=console     # live: write lines to run/p6_cmd.txt, tune run/fx_override.json meanwhile
```

Console lines (`run/p6_cmd.txt`, one per line): `rk <tsuki|hak|shira|abs|rb|passive> <ms> <name> [night] [front|up|down|mid|side]`,
`rkperf <f0|f6|f7>`, `rkfps <n>`. Example: `rk abs 3000 07_abszero_t3000`, `rk rb 300 07_rbankai_t300 night up`.
Screenshots go to `run/screenshots/p6_<name>.png`. Checks are logged as `CHECK PASS/FAIL`, the end as `RESULT ALL PASS`.

Screenshots (all noon unless `night`): step 6: `06_tsukishiro_t{200,500,1100,2000,2300}`, `_front_t1100`, `_up_t1100`, `_night_t1100`, `_night_t2300`;
`06_hakuren_t{300,1000,1250,1500,3000}`, `_night_t1250`, `_side_t1250`; `06_shirafune_t{300,500,600,1000}`, `_night_t700`, `_front_t800`.
Step 7: `07_rbankai_t{0,120,300,600,900,2000}`, `_back_t300`, `_night_t300`, `_night_t900`; `07_passive_{noon,midnight,front_noon,down_noon}`;
`07_abszero_t{400,1000,2000,3000,3350,4500}`, `_back_t2000`, `_night_t2000`, `_night_t3350`, `_fallback_t2000`.

## Tuning (`mod/run/fx_override.json`, hot reload every 30 frames, no restart)

Global (every Rukia effect): `rk.count` (particle counts), `rk.glow` (glow peaks), `rk.size` (glow and particle sizes), `glowIntensity` (FxConfig).
Per effect (default in the code; all numbers, the `col*` ones are `#RRGGBB` strings):

- `tsuki.`: `windSteps windTime moteSize speck colMote sigil sigilInner colSigil ringPulse decal decalReveal riseTime core outer coreTopFade outerTopFade colCore colOuter footRing flash crystalsPerBlock crystalScale crustRing vignette crystalShards burstSpeed burstRing shake hitstopMs`
- `hak.`: `scale crystalsTotal revertAfter colRing puncture bigScale smallScale grow ripple mist flare shake crystalShards`
- `shira.`: `growTime glowWidth glow colGlow coreWidth core colCore orb tipScale flare shake tipShards`
- `rb.` (release): `pillarHeight ringRadius coreRadius outerRadius implodeRadius sparkSpeed core outer baseFlare shardSize capHeight capSize capGlow column mist flake flash shake`
- `az.`: `stopAt waveTime wallHeight wall colWall decal mist flash0 shake0 vignette0 vignette1 vignetteDark desat1 desat2 grade crystals crystalScale hitstopMs flash1 shake1`
- `rbankai.` (passive, sheen, ribbons): `decalRadius decal crystalScale releaseCrystalRadius rimCrystalRadius relScaleA relScaleB rimScaleA rimScaleB mobRadius vignette vignetteHostile humPitch humVolume snowVolume chimePitch chimeVolume sheen sheenScale colSheen ribbonStart ribbonCurve ribbonWidth ribbonGlow ribbonSpread ribbonRootY ribbonRootBack ribbonSegLen ribbonSway ribbonOut`
- `shell.`: `alphaEncased alphaFrozen scaleXZ scaleY emissive grow growAz shards shardsAz speed speedAz`; `shard.`: `scale alpha emissive`; `crystal.`: `alpha emissive colEmissive`

## UNVERIFIED (nothing ran)

1. Everything visual: intensities and sizes (`tsuki.core 0.2`, `tsuki.outer 0.10`, `rb.core 0.8`, `az.wall 0.5`, `rbankai.sheen 1.9`, crystal scales 1.8 to 5.2) are
   storyboard values or estimates; the crystal / ribbon meshes are small (crystal a 0.15, b 0.30, c 0.60, d 1.20 blocks high at scale 1, ribbon segment 0.35 x 0.07), scales were chosen for the mesh sizes.
2. `PostEffectProcessor` for `freeze_desat` (program under `assets/minecraft/shaders/program/reiatsu_test_freeze_desat.*`, `FxDepth` stash/unstash around it) never ran in this branch;
   the harness check "real post effect, not the fallback" decides.
3. `RenderLayer.getEnergySwirl` sheen and the ribbon transform (feature renderer frame: verified by `javap -c` that `LivingEntityRenderer` does flip, `scale` (0.9375 for players), then `translate(0, -1.501, 0)`; the exact look of the S-loops is not).
4. Hakuren crystals shatter at step + 3.0 s: assumes the server's `TempBlocks.place(..., now + 60)` (AbilityExecutor, 3 s per step); tunable with `hak.revertAfter`.
5. Yarn names used and verified by `javap` this session: `Entity#getEyePos`, `getRotationVector()`, `Model#render(MatrixStack, VertexConsumer, int, int, int)`, `PlayerEntityRenderer#scale`. All other APIs were already compiled in the merged branch.
6. The step counts for Hakuren per-step crystals (`30 / steps`) were chosen to stay under `maxCrystals` (40 High, 28 Medium, 16 Low); at Medium/Low the cap drops the oldest crystals.
7. Not done (other steps): FOV_KICK (step 9), custom sounds (vanilla layering used), the Rukia costume set.
