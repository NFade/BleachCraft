# Phase 6, Byakuya side: steps 3, 4, 5, 8 (branch `p6-byakuya`)

Status 2026-10-11: code complete, `gradlew build test` green (318 tests, 29 new), **nothing was run in the game** (no runPhase*, by instruction). All screenshots below come from one harness run that is still to be done. Everything visual is tunable live through `mod/run/fx_override.json` (FxTune, re-read every 30 frames, no restart).

## 1. What exists now

| Step | Effect | Files | State |
|---|---|---|---|
| 3 | Shikai swarm (ids 3, 30, 31) | `SwarmFx`, `PetalSwarmRenderer`, `core/fx/SwarmMath` | WIP reviewed; two fixes; harness `swarm` / `swarmperf` unchanged |
| 4 | Bankai rows (id 4) | `RowsFx`, `BankaiBladeRenderer`, `core/fx/BladeRowLayout`, `BankaiCurves` | WIP reviewed; 5 fixes/additions (section 2); harness `rows` / `rowsfab` / `rowsperf`, new `rowsviews` |
| 5 | Petal storm (id 32) | `StormFx`, `StormRenderer`, `core/fx/StormMath`, `RowsPhase` | new |
| 8 | Shukei: Hakuteiken (id 33) | `HakuteikenFx`, `WingHaloRenderer`, `StormRenderer` (condensing petals), `core/fx/CondenseMath`, `HakuteikenCurves`, `RowsPhase` | new |

Shared files touched additively: `FxMesh.writeBlade(..., cutY)` overload, `FxGlowBatch.Emitter.plane(...)`, `ScreenFx.flashCustom(...)` and the `impactFrames` counter, `FxScene` (phase sync), `FxEvents`/`FxClient`/`FxTimelines` (routing for 32, 33). Rukia files untouched.

## 2. Review of steps 3 and 4 against the storyboard (fixes made)

- **Bug (rows): three `BufferBuilder`s of the shared `Tessellator` were open at once** in `BankaiBladeRenderer.drawExtras` (hilt, ripples, crack scars). One allocator hands out one contiguous region, so the builders interleave and each built buffer contains the others' vertices (hilt drawn with the crack layer, wrong vertex counts). Fixed with one `Tessellator` (own allocator) per extra buffer, and a separate one for the one-off static buffer build (it can run while the CPU path buffer of another state is open). Same rule respected in the new `WingHaloRenderer` (second allocator for the additive buffer).
- Night vignette defaults now follow 6.1: 0.45 while the night falls, 0.30 hold (were 0.32 / 0.24). `bankai.vignette`, `bankai.vignetteHold`.
- Others' night grade and vignette now use the distance to the **nearest row point** (inside the corridor = full night), not the distance to the caster (6.1 "Others"). Fixed at the moment of the release (a viewer who walks in later is not re-graded: UNVERIFIED limitation).
- Added the owner heartbeat `entity.warden.heartbeat` 0.7 0.15 every 1.5 s while the rows stand (6.1 "While BANKAI").
- Kept: fault-line speed 100 b/s (storyboard says 60, but at 60 the line would trail the eruption of the far blades, 0.012 s per block = 83 b/s; `bankai.faultSpeed`).
- Not done (low value, noted): swarm "emissive overlay 25 percent" on the near mesh, blade emissive overlay pass (tips glow through vertex light instead, as planned in 1.7.3).
- New harness views for step 4: `rowsviews` (series from 6 behind, from the side tower, N = 200 and 1000).

## 3. Server fields S1 to S5 (checked in code)

| | State |
|---|---|
| S1 anchor fields (kind, seed, startTime, ownerId, targetId, p0..p3, phaseKind, phaseTime) | existed (`FxAnchorEntity`) |
| S2 static ROWS anchor (p0 yaw, p1 feet y) | existed (`AnchorManager`) |
| S3 rows phase 32 / 33 + phaseTime on ids 32 / 33 | existed (`AnchorPlan`, `AnchorManager.onEffect`); client now reads it (`FxScene`, `RowsFx.setPhase`); added `ZanpakutoManager.rowsPhase(p)` for harness checks |
| S4 `effect_event` 33 params `[aim, size, lineLength]` | existed (`CastContext.effectParams`, lineLength = min(size, eye to aim)) |
| S5 HIT | `HIT_PETAL = 4` existed for the swarm envelope; **added** HIT_PETAL packets at the scatter tornado ticks and impact and at the Hakuteiken line and burst (`AbilityExecutor`). The client routes it to `SwarmFx.onHit` (pink glints and petals). `HIT = 3` stays the Rukia ice hit. |

Balance differs from the storyboard and the effects follow the event params, not the table: Hakuteiken length 28 (not 20), scatter impact radius 14, tornado radius 12 (event params `[aim, 14, 12]`), server phases 16/26/50 ticks and 30/36 ticks as in the table. The drawn tornado keeps the storyboard radius 5 (loose debris to 7); `storm.radiusScale` widens it if the 12 m damage radius should be visible.

## 4. Design decisions

**Storm (6.2).** Closed form of `(seed, petal j, t)` (`StormMath`), up to 3000 petals (15 per blade at N = 200, 3 at N = 1000). Petal j is born where the burning cut of its blade passes height `8 - 8 k/P` (`RowsPhase.Timing.cutPassesAt`) and is consumed where the regrowth passes the same height, so petals and blade edge meet exactly. Phases: corridor Bezier into the helix, tornado third stays, stream third (ranked by angle to the facing front, launch 1.5 to 1.8 s, flight min(0.6, L/40) so all arrive by 2.4), sphere r 5 to 2.2 to 0.8, burst at 2.5 s (impact at the server aim), drift, return Bezier (control 6 up) by 4.5 s. Rows: `RowsFx` phase 32 (shiver 0.04 at 18 Hz for 0.3 s, tip overdrive to white 0.25 s, blades cut from the tip with the tip quads collapsing into a glowing cap, horizontal flare and sparkle at the cut, regrowth bottom-up 4.5 to 5.7 s then back to the static buffer). Screen: FLASH 0.15 at the cut, owner shake 0.15 while the tornado stands, shake 0.6 and speed lines at the aim at 1.5 s, impact flash 0.30 and shake 0.9 by the distance to the impact point (caster himself never below 0.45, `storm.casterImpactMin`), HITSTOP 60 ms on the impact. No rows (late join beyond range): petals on a ring r 6 around the owner (stub, logged).

**Hakuteiken (6.3).** Rows phase 33 (dissolve in 0.3 s with 0.002 s per block, re-form from 3.0 s). Condensing petals (`CondenseMath`, 10 per blade = 2000 at N = 200) spiral two turns into the hand and turn white within 1.5 blocks; drawn as full-bright cards with the white texel of the emissive texture, vertex colour pink to white. Wings: the two exported sheets, unfurled by a **vertex deformation in polar coordinates about the root** (`HakuteikenCurves.fanVertex`: folded = all feathers at -12 degrees and 0.15 length, ease out back to the model's fan), feather ripple along the normal, dissolve as vertex alpha by radius, tips first; two passes (translucent emissive sheet + additive overglow, the same mesh 6 percent larger, tint `#F4F8FF` alpha 0.35, our own alpha-respecting additive layer `reiatsu_wing_glow`). Halo: mesh r 1.1 at head top + 0.35, 0.45 behind, scale 0.6 to 1 and alpha over 0.35 s, plus a PLANE bloom ring r 1.3 and a soft glow behind it. Blade: body mesh stretched along hand to line end (1 to 4 m, then to L in 0.08 s ease out expo, back to 1 m), tip mesh at its end, glow strip, sparks sliding along. Line: two `FxShapes.Strip`s (core 0.12 white, glow 0.6 `#F4F8FF` 0.5), ring every 2 blocks perpendicular to the line, 200 sparks, burst at 1.8 s with HITSTOP 50 ms, white sphere, ground ring, feathers. Screen: vignette 0.60, grade to `#3C4870`, `#2E3858`, darker at the held breath, back to the night colour over 1.3 s (it **drives the rows' own grade layer**, the grades multiply, so a second layer would be far too dark), FLASH 0.40 at the bloom, impact frame at 1.5 s (reduce motion / photosensitive: flash 0.40 short, counter `ScreenFx.impactFrames` stays), shake 1.2, burst flash 0.80 over 0.4 s by the distance to the burst.
- **Held breath** (1.4 to 1.5 s) is a stillness of the visual clock (`HakuteikenCurves.rippleTime`: ripple and blade pulse stand still), not a timeline hold, so the strike stays on the server tick 1.5 s. The 50 ms burst HITSTOP is real.
- Hand = right shoulder + look x 0.6 (`hak.handSide/Height/Forward`), wing root (0.12 out, 1.35 up, 0.15 behind), halo and head numbers are keys (`hak.rootX/Y/Back`, `hak.headTop`, `hak.haloUp/Back`).
- Not built: FOV_KICK, custom sounds (vanilla layering as in the storyboard), late-join reconstruction of the storm itself (the rows phase is reconstructed from the anchor).

## 5. Tunable keys (`mod/run/fx_override.json`, numbers unless a colour)

- **storm.\***: timing `cutStart` 0.30 `cutPerBlock` 0.008 `cutSpan` 0.40 `reformStart` 4.5 `reformPerBlock` 0.004 `reformSpan` 0.6 `corridorSpan` `corridorUp` `launchStart` 1.5 `launchSpan` 0.3 `speed` 40 `maxFlight` 0.6 `wobble` `shrinkStart` 2.0 `shrinkEnd` 2.44 `impact` 2.5 `returnStart` 3.0 `returnJitter` `returnSpan` `returnUp`; shape `helixRise` `helixHeight` `rBase` `rSpread` `rExp` `rNoise` `spin` `spinTaper` `radiusScale` `loose` `looseR0` `looseR1` `stream` `sphereR` `sphereR1` `sphereR2` `sphereRev` `burstR` `burstSpan` `burstFall`; look `petalScale` 1.8 `glintIntensity` `streakIntensity` `haloIntensity` `edgeGlow` `footRing` 5.5 `footRingIntensity` 0.25 `envelopSize` `envelopGlow` `coreSize` `ringSize` `shiver` `shiverTime` `overdriveTime`; screen `flashStart` `shake` `attackShake` `speedLines` `impactFlash` `impactShake` `hitstop` `vignette` `casterImpactMin`.
- **hak.\***: timing `wingStart` .5 `fanSpan` .45 `fanOvershoot` `haloSpan` `ignite` `bladeStart` `bladeSpan` `bladeMin` `bladeHeld` `breathStart` `breathSpan` `strike` `extend` `burst` `lineFade` `lineFadeSpan` `dissolveStart` `dissolveEnd` `life` `foldLength`, rows `cutStart` `cutPerBlock` `cutSpan` `reformStart` `reformPerBlock` `reformSpan`; condense `flightBase` `flightJitter` `turns` `spiralRadius` `whiteRadius` `absorb` `pull` `petalScale`; body `wingScale` (times the config `wingScale`) `rootX` `rootY` `rootBack` `ripple` `wingAlpha` `overglowScale` `overglowAlpha` `tint` `glowTint` `haloScale` `haloAlpha` `haloGlow` `haloSpin` `haloUp` `haloBack` `headTop` `bladeWidth` `handSide` `handHeight` `handForward`; glow `glowMul` `coreGlow` `coreSize` `flareSize` `ringBloom` `ringBloomR` `featherBloom` `featherBloomWidth` `bladeGlow` `bladeGlowWidth` `coreWidth` `glowWidth` `glowIntensity` `ringSize` `featherSize` `burstSize` `ringR`; screen `vignette` `bloomFlash` `strikeShake` `burstFlash` `burstShake` `hitstop`.
- **bankai.\*** (step 4): `night` `nightStrength` `vignette` `vignetteHold` `faultSpeed` `faultIntensity` `burstStreak` `completeShake` `hiltScale` `rippleAlpha` `tipGlow` `tipSize` `minBlock`. **swarm.\*** (step 3): `petalScale` `cardUp` `glintPower` `glintIntensity` `haloIntensity` `glowMul` `coreGlow` `attackShake`.
- Global keys of `FxConfig` (`glowIntensity`, `reduceMotion`, `wingScale`, `bankaiBladeCount`, `effectQuality`...) can be put in the same file.
- Defaults of keys without a literal in the table are the storyboard values in `StormMath.Params`, `CondenseMath.Params`, `HakuteikenCurves.Timing`.

## 6. Scenarios (one run for everything, later)

`cd mod` then `./gradlew runPhase6 -Phold=swarm,rows,rowsviews,storm,hakuteiken` (env `JAVA_HOME`, `GRADLE_USER_HOME` as usual). Perf: `-Phold=swarmperf,rowsperf,stormperf`. Fabulous: `-Phold=rowsfab`. Everything goes through the real keys (R, G, then U for the scatter, I for Hakuteiken), clock frozen at exact milliseconds, hard checks logged as `CHECK PASS/FAIL`. Screenshots in `mod/run/screenshots/`:

| Scenario | Files |
|---|---|
| `swarm` (step 3) | `p6_03_swarm_t{100,300,500}`, `_idle_t3000`, `_glint_g0..3`, `_front_idle`, `_side_idle`, `_night_idle`, `p6_03_attack_t{300,500,1000}`, `_attack_fp_*`, `p6_03_dome_t{300,600,1500,5000}`, `_dome_top_t1500` |
| `rows` (step 4) | `p6_04_rows_t{0,180,350,600,1000,1500,2200,2800}` (front), `_settled_front`, `p6_04_tower_settled(_night)`, `p6_04_tips_100_{night,noon}`, `p6_04_corridor_ground_{noon,night}`, `p6_04_rows_seal_t350`, `p6_04_rows1000_t*`, `p6_04_tower1000_t2800_live` |
| `rowsviews` (new) | `p6_04_rows_back_t{0,600,1500,2200,2800}`, `p6_04_rows_tower_t{500,1000,1500,2200,2800}`, `p6_04_rows1000_back_t*`, `p6_04_rows1000_tower_t*` |
| `storm` (step 5) | `p6_05_storm_t{300,800,1500,2000,2500,4800}` (behind), `p6_05_storm_side_t{1500,2000}`, `p6_05_storm_top_t{1000,1500}`, `p6_05_storm_night_t{800,2000,2500}` |
| `hakuteiken` (step 8) | `p6_08_hakuteiken_t{0,500,1000,1500,1530,1800,2600}_front` and `_threeq` (the owner turns 25 / 70 degrees right after the cast so the camera in front sees him, the line goes off to the side), `_back_t{1000,1530,1800}`, `_night_t{1000,1530}`, `p6_08_hakuteiken_reform_t3600`, `_rm_t1530` (reduce motion: no impact frame, checked), `_impactframe_t1530` (exactly one impact frame, checked) |

Hard checks (all in the log): storm state and petals drawn at 0.8 / 1.5 s, storm CPU <= 4 ms, every blade gone at 1.5 s, impact reached, regrowth at 4.8 s, S3 anchor phase 32 / 33 on the server, S5 hit feedback for a pig at the aim, condensing petals at 0.5 s, wings fan about 1 at 1.0 s, halo drawn, wing mesh cost <= 1 ms, strike strips drawn at 1.53 s, S4 line length <= 28, burst reached, impact frame counts (RM and normal), rows re-forming at 3.6 s, F2 storm and F5 Hakuteiken >= 60 fps mean and >= 45 1% low.

Live tuning without restarts: `-Phold=console`, then lines in `mod/run/p6_cmd.txt`: `rowsup [view]` (stand the rows up), `stm <ms> <name> [view]` (frozen storm shot, views `far` `farside` `fartop` `back` `front` `side` `top`), `hak <ms> <name> [turnDeg] [view]`, `stmwait`, plus the existing `swrel` `swatk` `swdome` `bk` `tp` `persp` `blades` `quality` `vbo` `sealv` `clearfx` `time` `view` `wait` `quit`. Edit `fx_override.json` between lines.

## 7. Tests added (core only, no Minecraft imports)

`RowsPhaseTest` (cut and regrowth times, birth/consume match the blade edge, anchor age), `StormMathTest` (counts, stream order and arrival by 2.4 s, birth at the cut, helix core and radius, no jump at impact and return, sphere radius table, burst reach, return and consumption, determinism, finite everywhere), `CondenseMathTest`, `HakuteikenCurvesTest` (fan with overshoot, polar fold to -12 degrees and 0.15, halo, dissolve by radius, feather table, blade and line, held breath, grade colours). Total 318 tests (was 289).

## 8. UNVERIFIED (nothing was run in the game)

- Additive alpha layer `reiatsu_wing_glow` (program `ENTITY_TRANSLUCENT_EMISSIVE_PROGRAM`, `LIGHTNING_TRANSPARENCY`, no culling, colour mask): constants verified in the Yarn sources, behaviour on screen not seen. Fallback if it looks wrong: `hak.overglowAlpha = 0`.
- Wing orientation: OBJ frame = player frame (+Z forward, +X left) from ART_BIBLE B12 (+Y behind in Blender, curve toward -Y) and the exporter axis map; root offsets are guesses (`hak.rootX/Y/Back`).
- White texel for the condensing cards: pixel (277, 239) of `byakuya_bankai_emissive.png` (a 6 x 6 opaque white block found by script; it lies inside the wing sheet area).
- Static row buffer draw (uniforms, fog) is the earlier WIP, still UNVERIFIED as noted in 1.7.3; the CPU path draws every blade while a phase runs.
- Translucent layers draw in write order (no per-quad sort): overlapping wing faces may blend in a fixed order.
- Impact strength for the caster (0.45 floor) and the grade distance for other players are decisions, not storyboard numbers.
- Brightness balance of the new glows (cut flares, wing bloom, ring) under the x1.3 glow boost while a grade is strong; first knobs: `storm.edgeGlow`, `hak.featherBloom`, `hak.ringBloom`, `hak.coreGlow`.
- Server tornado radius 12 vs drawn radius 5: visual choice (`storm.radiusScale`).
- Remote caster (`-Dreiatsu.fx.remote=true`, two clients) for ids 32 and 33 not run.
