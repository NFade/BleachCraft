# Uryu U-FX 6: results note

Stage U-FX 6 of URYU_VFX_STORYBOARD (2.5 Blut Arterie release, 4.2 vein skin, section 5 row U-FX 6, harness rows p7_06_blut_*).

Branch `uryu-fx-wip`. The stage commit is `73d6f7e` (range `f6c97f6..73d6f7e` contains only this commit). The U-FX 6 code was first written in the WIP commit `d14a64c` ("stopped by the usage limit, may not compile, never run in game"); `73d6f7e` finishes it. Numbers and file:line refs are read from `73d6f7e` (HEAD of this branch at the time of writing). Nothing here was run in the game, and no gradle or test run was made for this note (see sections 4 and 5).

---

## 1. What was built

One line per file, paths relative to `mod/src/` unless noted. "In 73d6f7e" says whether that commit touched the file.

| File | In 73d6f7e | What it does |
|---|---|---|
| core/fx/UryuBlutTiming.java | changed (+39, lines 206-244) | Pure tables and curves of the release and the vein overlay: times, levels, pulse, fade, colour alphas, part scales, armor reach, state smoothing. |
| client/fx/UryuQuincyFeature.java | changed (hunks in section 3) | Per player vein state (`Q`), `onRelease`, `onFire`, `state()`, `vein()`, per tick hook, `render` (third person), `firstPersonArm` (first person), `drawParts` (two passes), `armored()`, the `addBankaiPass` hook (U-FX 7). |
| client/fx/UryuBlutFx.java | not changed (created in d14a64c) | The one shot timeline of effect 70: fists, ignition sparkles, burst ring, flash, shake, vignette, heartbeat sounds. Calls `UryuQuincyFeature.onRelease` (line 85). Reduce Motion read at line 86. Tier LOW halves the dashes (line 76). |
| client/fx/UryuAuraFx.java | not changed in 73d6f7e | `releaseShikai(e)` (lines 23-25) delegates to `UryuBlutFx.release`. The Focus and shunpo code of the same class is outside U-FX 6. |
| client/fx/UryuFx.java | not changed | `handles(70)` (line 25-26: `effectId == 70 || 72..78`) and the dispatch `case 70 -> UryuAuraFx.releaseShikai(e)` (line 95). |
| client/fx/UryuHand.java | not changed | Bow anchor positions in the world (third and first person). No reference to the release or the veins (grep of `client/`). Not part of the U-FX 6 path. |
| client/fx/UryuFlakes.java | not changed | Live cap for flake flechettes. No reference from the release path (grep of `client/`). Not part of the U-FX 6 path. |
| client/model/FirstPersonHand.java | not changed (overlay call sites from d14a64c) | Two render sites call `UryuQuincyFeature.firstPersonArm`: line 59 in `renderVanillaArm` (the arm of the empty hand) and line 179 in `render` (the arm of a held zanpakuto). Both sit after the vanilla `renderLeftArm` / `renderRightArm` call, in the same matrix stack. |
| tools/gen_uryu_textures.py | changed (6 lines) | Vein block "U-FX 6: the vein skin": walkers per arm belt 2 to 3 (lines 841, 843); pass 3 count `max(1, belt[6] // 2)` (line 923). Generates `uryu_vein_skin.png` and the stage files. |
| textures/fx/uryu_vein_skin.png | changed (binary, 551 to 631 bytes) | Finished vein skin, 64 x 64, player UV layout. Identical to `uryu_vein/stage_11.png`. |
| textures/fx/uryu_vein/stage_00.png to stage_11.png | changed (binary, 12 files) | Draw-on versions (`UryuBlutTiming.STAGES` = 12). Stage k shows every vein pixel whose gradient is at most (k+1)/12. |
| core/fx/UryuBlutTimingTest.java | changed (+39, lines 294-332) | Two new tests (section 4). |
| test core/fx/UryuVeinSkinTest.java | not changed (created in d14a64c) | Checks the vein skin PNGs (section 4). |
| client/dev/Phase7Blut.java | not changed (created in d14a64c) | Harness scenario `uryu_blut` (section 4 and 6). |

---

## 2. Timeline and numbers in force

Times are seconds after the release event reached the client (`t = 0`, the clock of `FxClock`). Constants are in `UryuBlutTiming` unless noted.

### 2.1 Release timeline (vein brightness, `releaseLevel`)

| t (s) | Constant | Value in force | What the overlay does |
|---|---|---|---|
| 0.00 | `T_FISTS` | 0.00 | Fists, pentagon at the feet (not the overlay). |
| 0.20 | `T_IGNITE` | 0.20 | Veins start. Stage 0 (fingertips). Brightness jumps from 0 to about 0.6 (`0.6 + 0.4 * sweep(t)`). |
| 0.20 to 0.55 | `SWEEP_TIME` | 0.35 (`ios` easing) | Draw-on sweep from the fingertips to the neck. Stage = `ceil(sweep * 12) - 1`. Brightness 0.6 to 1.0. |
| 0.55 | `T_BURST` | 0.55 | Stage 11 (all veins) from here on. Brightness 1.0, then eases to 0.55 until 0.90. |
| 0.55 to 0.90 | `RING_TIME` | 0.35 s | Shockwave: ground ring `RING_SOFT` from radius 0 to `RING_RADIUS` = 4.0 blocks. Flash `FLASH` 0.35; shake `SHAKE_AMPLITUDE` 0.5 for `SHAKE_TIME` 0.25 s; body flash 3.0 size, 0.12 s. |
| 0.90 | `T_HEART`, `BEAT_PERIOD` | 0.70 | Three beats at 0.90, 1.60, 2.30 (`BEATS` = 3). Each beat: rise to 1.00 in 15 percent of the period, exponential decay to 0.55 at its end. Sounds `entity.warden.heartbeat` at the same times. |
| 3.00 | `heartEnd()` | 3.00 | End of the heartbeat window. |
| 3.00 to 3.40 | `SETTLE_TIME` | 0.40 s (`ios`) | Brightness from 0.55 to the state level. |
| 3.40 on | | | State level holds. |
| 1.20 | `T_REST` | 1.20 | Defined and unit tested, but `releaseLevel` does not use it. The state level is reached at 3.40, not 1.20. |

### 2.2 Other levels and rules

| Item | Value in force | Source |
|---|---|---|
| State level (SHIKAI) | 0.5; 0.8 at Focus 3 | `STATE_LEVEL`, `FOCUS3_LEVEL`, `stateLevel(focus)` |
| Focus-3 smoothing | `approach(state, target, 0.3)` once per client tick; the state starts at the target on the first tick (NaN until then) | `UryuQuincyFeature.tick` lines 276-277; `approach` line 241 |
| Shot brightening | 0.8 peak for 0.30 s; rise over 0.045 s (15 percent), then ease out; peak = min(1, level + 0.3) | `FIRE_LEVEL`, `FIRE_TIME`, `fireEnvelope`, `withFire` |
| Fade out after SHIKAI ends | 0.40 s, level = last level times `fadeOut`, stage 11, then hidden | `FADE_OUT`, `vein()` lines 339-343 |
| Visible threshold | level below 0.02 is not drawn | `invisible` |
| Vein colour | solid alpha `0.30 + 1.1 * level` (0.85 at 0.5, 1.0 from 0.64); additive glow `1.4 * level` (0.7 at 0.5, 1.0 from 0.72) | `solidAlpha`, `glowGain` |
| View distance | 48 blocks | `VIEW_DISTANCE` |
| Tier | FULL (default): chest and both arms. ARMS (Low): arms only | `parts()`, `FxConfig.uryuVeinOverlay` (default FULL, FxConfig line 98) |
| Motes | 4 per second from the forearms, only while level >= 0.2 (0.4 with Reduce Motion) | `MOTES_PER_SECOND`, `motes()` lines 292-296 |

### 2.3 Reduce Motion

`rm = FxConfig.reduceMotion || FxConfig.photosensitiveSafe` (`vein()` line 320).

| Item | Value in force |
|---|---|
| Pulse | constant `RM_CONSTANT` = 0.70 from t = 0.20 to 3.00, then the settle 3.00 to 3.40 from 0.70 to the state level |
| Fire brightening | off |
| Draw-on stages | still run (the stage comes from the sweep, not from `rm`) |
| Flash | `FLASH_RM` 0.25, 0.07 s |
| Shake | none: `UryuBlutFx` line 130 calls `x.shake` without an rm check, and `ScreenFx.shake` returns early under `reduceMotion || photosensitiveSafe` (`ScreenFx.java` lines 271-272) |
| Vignette | `VIGNETTE_RM` 0.10 |

### 2.4 Armor reach (1.25 px)

`ARMOR_REACH_PX` = 1.25. Scale of a cuboid of size s px: `1 + 2 * reach / s` (`scaleFor`). Reach beyond the unscaled cuboid per side: `s * (scale - 1) / 2` (`reach`).

| Part | Plain scale (no armor) | Reach without armor | Armor scale | Reach with armor |
|---|---|---|---|---|
| Arm, wide (4 px), x | 1.16 | 0.32 px | 1.625 | 1.25 px |
| Arm, slim (3 px), x | 1.22 | 0.33 px | 1.8333 | 1.25 px |
| Arm, z (4 px) | 1.16 | 0.32 px | 1.625 | 1.25 px |
| Body, x (8 px) | 1.08 | 0.32 px | 1.3125 | 1.25 px |
| Body, z (4 px) | 1.16 | 0.32 px | 1.625 | 1.25 px |

The no-armor values are the constants `armScaleX`, `armScaleZ`, `bodyScaleX`, `bodyScaleZ`; the armor values and reach column are arithmetic from the formulas above. The pivot shift `armPivotShiftX` keeps the arm centre (c = +-1 wide, +-0.5 slim) at its place for any scale.

### 2.5 Event and attachment race

| Item | Value in force | Source |
|---|---|---|
| A record made by the release event is kept at least 2 s after the event, even when the attachment has not arrived | 2.0 s | `UryuQuincyFeature.tick` line 271 |
| An attachment that comes within 2 s of the event does not reset the start time | 2.0 s | line 248 (unchanged from d14a64c) |
| Player already in view gets an attachment without an event: veins start by themselves | after `PENDING_WAIT` = 0.15 s (set at `pendingAt`, lines 250-251) | `PENDING_WAIT` (line 72), `vein()` lines 324-325 |

---

## 3. Shared-file edits with file:line (git diff of 73d6f7e)

Line numbers are those of `73d6f7e`, which is the current HEAD.

### 3.1 client/fx/UryuQuincyFeature.java

| Lines | Change |
|---|---|
| 33-35 | Imports `EquipmentSlot`, `ArmorItem`, `ItemStack`. |
| 107-108 | `Q.state`: smoothed state level, NaN = not set. |
| 270-271 | Keeps a record 2 s past `eventAt` (comment line 270, condition line 271). |
| 276-277 | Target = `stateLevel(focus)`; `q.state` moves 0.3 of the gap per tick. |
| 331 | `releaseLevel` takes `q.state` (or the focus level while NaN). |
| 369 | `render` passes `armored(entity)` and the part list to `drawParts`. |
| 389-390 | `drawParts` signature gets `boolean armored`. |
| 407-408 | Body scale with `bodyScaleX(armored)`, `bodyScaleZ(armored)`. |
| 411-414 | Arm scale with `armScaleX(slim, armored)`, `armScaleZ(armored)`; pivot shift uses the scale `sx`. |
| 436-441 | New `armored(pl)`: a non-empty chest slot holding an `ArmorItem` of slot CHEST. |
| 467 | `firstPersonArm` passes `armored = false`. |

### 3.2 core/fx/UryuBlutTiming.java (new block, lines 206-244)

| Lines | Addition |
|---|---|
| 210 | `ARMOR_REACH_PX = 1.25` |
| 213 | `scaleFor(sizePx, reachPx)` |
| 218, 222, 226, 230 | `armScaleX(slim, armored)`, `armScaleZ(armored)`, `bodyScaleX(armored)`, `bodyScaleZ(armored)` |
| 235 | `armPivotShiftX(slim, left, scaleX)` |
| 241 | `approach(current, target, rate)` (rate clamped to 0..1) |

### 3.3 tools/gen_uryu_textures.py

| Line | Change |
|---|---|
| 841 | `r_arm` walkers 2 to 3 |
| 843 | `l_arm` walkers 2 to 3 |
| 923 | Pass 3 count: `max(1, belt[6] // 2)` instead of 1. With the walker counts of the belts this is 1 for each belt, so the count of pass 3 is unchanged. |

### 3.4 Not changed in 73d6f7e

| File | Where the U-FX 6 hook is |
|---|---|
| client/model/FirstPersonHand.java | Lines 59 and 179 (`firstPersonArm` calls), inserted in d14a64c. |
| client/fx/UryuFx.java | Lines 25-26 (`handles`), line 95 (dispatch), from earlier commits. |
| client/fx/UryuAuraFx.java | Lines 23-25 (`releaseShikai`), from d14a64c. |

---

## 4. Tests

No test was run for this note. Names below are read from the source.

**core/fx/UryuBlutTimingTest.java** (19 tests in the file; the last two are new in 73d6f7e):

- theTimesAreTheRowsOfTheTable
- theSweepRunsFromTheFingertipsToTheNeckInHalfAThirdOfASecond
- theStageNeverStepsBackAndVisitsEveryVersion
- theThumpStaysBetweenTheTwoLevelsOfTheStoryboard
- theReleaseLevelFollowsTheTable
- theReleaseLevelHasNoJumpAfterTheIgnition
- reduceMotionMakesThePulseAConstantSeventyPercent
- theStateLevelAndTheShotBrightening
- theOverlayFadesOutAfterTheState
- theColourOfTheTwoPassesReadsAtTheStateLevel
- lowTierDrawsTheArmsOnly
- theOverlayReachesBeyondTheOuterSkinLayerOnSlimAndClassicArmsAndTheChest
- thePivotShiftKeepsTheScaledArmWhereItWas
- theSoundsAreTheOnesOfTheStoryboard
- theCountsAndSizesAreTheStoryboardOnes
- theReleaseStaysInsideItsGlowBudgetAndTheRmCapsAreBelowTheNormalValues
- thePentagonOfTheFeetIsRegularAndDrawsOneEdgePerFortyMilliseconds
- armorMakesTheOverlayReachBeyondTheOneAndAQuarterPixelDilationOfAChestplate (new, 73d6f7e)
- theStateLevelApproachesItsTargetWithoutAJump (new, 73d6f7e)

**core/fx/UryuVeinSkinTest.java** (7 tests, from d14a64c):

- allVersionsExistAtSkinSizeAndTheLastOneIsTheFinishedSkin
- onlyArmsChestAndNeckArePaintedInTheColumnsOfBothArmLayouts
- theVeinsAreDenseOnBothArmsAndTheChest
- theColoursAreThePaletteOfTheQuincy
- everyStageContainsTheOneBeforeAndTheFingertipsLightFirst
- theBrightHeadOfTheSweepIsTheRimColourAndTheTailTheCyan
- theGeneratorBlockIsSeparatedAndNeverTouchesTheBlenderAtlas

Its density thresholds (for example arm vein pixels at least 35, chest at least 55) were not checked against the PNGs here.

**Harness (client/dev/Phase7Blut.java, scenario `uryu_blut`)**: not run. See section 6 of this note for what it captures.

---

## 5. UNVERIFIED in game

Nothing was run in the game, and no gradle build was run for this note. The commit `d14a64c` was marked as possibly not compiling; `73d6f7e` was not built here. Look at:

| Item | What to look at |
|---|---|
| Veins on slim and classic skins | Both arm widths: the overlay is wider than the arm (reach 0.32 to 0.33 px without armor), stays clear of the sleeve layer (OUTER_LAYER_PX 0.25 plus clearance 0.05 in the constants). |
| Noon and midnight | Contrast of the veins against a pale sky and at night (the additive pass is the bloom). |
| Over armor | With a chestplate the reach is 1.25 px; the value is a starting value, not seen. |
| First person | The arm overlay is drawn in two places (FirstPersonHand lines 59 and 179); check both the empty hand and the held zanpakuto. |
| Reduce Motion | Constant 0.70 pulse, no fire brightening, no shake, flash 0.25. The draw-on still runs. |
| Level step at 0.20 s | The brightness goes from 0 to about 0.6 in one step at the ignition; the fade-in is the draw-on mask only. Check that this looks right. |
| Focus-3 smoothing | 0.3 of the gap per tick (about 8 ticks to get within 0.02); check that the change of level does not look like a jump. |
| Race of 2 s | A release event that arrives before or after the attachment should not restart or lose the veins. |
| Density and colour | The vein PNGs were generated by the script; their look was not checked in game or on a sheet here. |
| Harness text | `Phase7Blut` javadoc names a shot `..._state` for the 4000 ms stand-in shots; the code names them `..._t4000`. The code is what is saved. |

---

## 6. Manual checklist for the user

1. Get the cross: `/give @s reiatsu_test:uryu_cross` (the harness uses `item replace entity @s hotbar.0 with reiatsu_test:uryu_cross`) and select it in the hotbar. Make the character Uryu (the harness uses `/reiatsu state base uryu`).
2. Press J to draw the cross (SEALED to BASE).
3. Press R to release into Blut Arterie (BASE to SHIKAI). Check the sequence: fists, veins from the fingertips up the arms and neck at about 0.2 s, the shockwave ring with flash and shake at about 0.55 s, three heartbeats at 0.9, 1.6 and 2.3 s, settle by about 3.4 s. Do it at noon and at night (`/time set midnight`).
4. Third person (F5): the veins glow on the arms, the neck and the chest.
5. First person: the vein overlay is on the arm.
6. Stand still until you have 3 Focus stacks: the overlay must not jump when the level rises to 0.8.
7. Put on an armor chestplate: the veins are still visible (not hidden under the armor).
8. A second player (within 48 blocks) sees the veins on the caster.
9. Seal with V: the veins fade out over about 0.4 s.

For each step, note what you see on slim and classic skins if you have both.

---

Files of this note: `design/results/uryu_ufx6.md` (only file added in this stage). Stage commit reviewed: `73d6f7e`.
