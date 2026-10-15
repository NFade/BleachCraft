# U-FX 5: Seele Schneider combo (results note)

Commit `3f122a4` "Uryu U-FX 5: Seele Schneider combo finished" on branch `uryu-fx-wip`. Storyboard: `design/URYU_VFX_STORYBOARD.md` row 2.9 and the U-FX 5 row. Every number below was read from the commit or from the files at `3f122a4` (tree clean there). Nothing was run: no gradle, no unit test, no game.

## 1 What was built

| File | Lines at 3f122a4 | Change in 3f122a4 |
|---|---|---|
| `mod/src/client/java/dev/minebleach/reiatsutest/client/fx/UryuSeeleFx.java` | 973 | 102 lines (+/-): first person lunge and afterimage handling, tier counts, overlapped crescent ribbon with subdivision, steady tip glint, hitstop photosensitive gate |
| `mod/src/main/java/dev/minebleach/reiatsutest/core/fx/SeeleFxMath.java` | 424 | 33 lines: afterimage step and first person distance, `tierCount`, `subdivision`, `ribbonOverlap`, `windowStart`, `ribbonGain` |
| `mod/src/test/java/dev/minebleach/reiatsutest/core/fx/SeeleFxMathTest.java` | | 27 lines: one new test |

Harness `client/dev/Phase7Seele.java` (231 lines) was not changed by U-FX 5 (added in `d14a64c`).

## 2 Timeline and numbers in force

Times are seconds from the lunge event (effect 74). Server side (`UryuBalance`): lunge 1.5 blocks, 3 ticks at 0.36 blocks per tick; slashes at ticks 3, 8, 13 (`SeeleCombo.slashTick`: first 3, gap 5); reach 3.5; cone 100 degrees (half 50); damage 2.5; knockback 1.0 on the third slash only.

### Lunge (t 0 to 0.15)

| Item | Value |
|---|---|
| Hilt rise | hip to hand in 0.10 s (`DRAW_HAND`) |
| Blade grow | starts at 0.04 s, lasts 0.08 s, ease out expo (full at 0.12 s, computed) |
| Lunge strips | heights above the feet 0.35, 0.95, 1.55; each a body (STREAK, Q3, life 0.25) and a core (LINE, Q0, life 0.22); length 2.0 (1.5 + 0.5) |
| Dash stream | 12 DASH sprites behind the runner (Low: 6) |
| Start dashes at the hip | 8 (Low: 4) |
| Platform disc | size 1.8, life 0.5 s, Q2 |
| Afterimages | up to 3 (see section 3), 0.29 blocks apart, born 0.04 s apart, alpha 0.35, fade over 0.30 s |
| FOV kick | +3 degrees over 0.30 s, local caster only (`ScreenFx.shunpoRush`) |

### Three slashes

| Slash | t (s) | Plane tilt / sign | Reach | Crescent | Ribbon width max | Shake | Outer wave radius (0.85 x reach, computed) |
|---|---|---|---|---|---|---|---|
| 1 | 0.15 | +18 / +1 (right to left) | 3.50 | 1.0 | 0.55 | 0.2 deg, 0.15 s | 2.975 |
| 2 | 0.40 | -18 / -1 (back) | 3.675 (x1.05) | 1.0 | 0.55 | 0.2 deg, 0.15 s | 3.124 |
| 3 (chop) | 0.65 | 90 / +1 (overhead) | 3.50 | 1.4, plus a wider second crescent (size x1.35, radius x0.9) | 0.77 | 0.5 deg, 0.25 s | 2.975 |

All slashes: sweep 0.16 s, smoothstep; blade inner radius 1.8 (0.45 hilt + 0.9 x 1.5); outer wave lives 0.30 s; trail points 0.28 s; crescent echo 0.25 s; star4 flash at the hilt (size 0.9, 0.14 s); star4 at the tip once per segment (size 0.34); 3 arc flakes per slash (size 0.32, life argument 9).

### Third slash: knockback burst and ground crack

- Per target hit by slash 3: burst rays 1.4, star4 0.9, glow 2.4, 8 flechette flakes, 8 radial dashes (speed 9), a ground ring at the target (size 0.4 x 2.64, life 0.25).
- Ground impact when the chop reaches progress 0.85 (about 0.77 s, computed): crack decal radius 1.0 at 2.4 blocks in front of the pivot (`min(2.4, 0.7 x reach)`), `reveal(0.12)`, `fade(1.6, 0.8)`, `alpha(0.8)`; crack glow size 2.0, life 0.55; ring 0.4 x 4.4, life 0.25; 4 flakes; if no target was hit, 8 radial dashes at the ground.

### Hit sparks (IMPACT kind 99, routed here)

| Item | Slash 1 and 2 | Slash 3 |
|---|---|---|
| Burst rays | 1.1 | 1.4 |
| Star4 | 0.7 | 0.9 |
| Glow | 1.8 | 2.4 |
| Flechette flakes | 6 | 8 |
| Radial dashes | 0 | 8 |
| Ground ring at target | no | yes |

Timing: the sparks wait until the blade crosses the target (`crossProgress`, `passTime`); a hit more than 0.3 s after the stroke start or outside reach + 2.5 finds no combo and spawns at once.

### Hitstop

40 ms on a slash 3 hit (`SeeleFxMath.screen`), on the combo timeline. 0 ms for slashes 1 and 2. Skipped under Reduce Motion or photosensitive safe (section 3).

### Sounds (`SeeleFxMath`, ids and pitch / volume as coded)

| Event | Cues |
|---|---|
| Lunge t 0 (line 390) | `item.trident.throw` 1.6 / 0.5; `entity.player.attack.sweep` 1.7 / 0.5 |
| Slash 1 (line 399) | `block.amethyst_block.chime` 1.9 / 0.4; `item.trident.throw` 2.0 / 0.2 |
| Slash 2 (line 400) | `block.amethyst_block.chime` 2.0 / 0.35; `item.trident.throw` 2.0 / 0.2 |
| Slash 3 (line 401) | `entity.player.attack.strong` 1.2 / 0.8; `item.mace.smash_air` 1.8 / 0.4 |
| Hit, any slash (line 407) | `entity.player.attack.crit` 1.4 / 0.6; `block.amethyst_block.hit` 1.8 / 0.5 |
| Dissolve (line 411) | `block.amethyst_block.break` 1.9 / 0.2 |

The vanilla sweep at each slash is played by the server, not here (comment at `SeeleFxMath.java:393-395`).

### Dissolve and lifetimes

- Blade dissolve starts at 0.85 s (slash 3 time + 0.20 s, `DISSOLVE_AT`) and takes 0.25 s (sine ease, from the tip). 8 flechette flakes, 12 dashes, a star4 at the hilt, the dissolve sound.
- A combo is removed at dissolve + 0.40 s, or when older than 3.0 s. `COMBO_LIFETIME` is 1.25 s for the timeline.

### Blade geometry

- Mesh `uryu_seele_long`, 0.9 m, scaled by `BLADE_SCALE` 1.5 (about 1.35 m).
- Pivot, third person: eye + 0.30 side, -0.30 down. First person: camera + 0.35 side, -0.25 up, +0.15 forward. Side is the off hand (left for a right hander).

## 3 Reduce Motion and quality tier behaviour

Tier is `FxConfig.tier().ordinal()` (LOW 0, MEDIUM 1, HIGH 2).

| Item | Low | Medium | High |
|---|---|---|---|
| Arc segments (`segments`) | 6 | 9 | 12 |
| Points drawn per arc (x `subdivision`) | 6 | 9 | 24 (12 x 2) |
| Ribbon overlap (`ribbonOverlap`) | 3 | 3 | 4 |
| Gain per streak, 1 / (overlap x 0.59), computed | 0.565 | 0.565 | 0.424 |
| Lunge dashes (`tierCount` of 12) | 6 | 12 | 12 |
| Start dashes (`tierCount` of 8) | 4 | 8 | 8 |
| Afterimages | 0 | 2 | 3 |

Afterimages come from `UryuFxTiers` rows (LOW 0, MEDIUM 2, HIGH 3) unless `fxTier` is CUSTOM, then the file key `uryu.afterimages` (default 3). `spawnImages` uses `min(3, FxConfig.uryuAfterimages)` (`UryuSeeleFx.java:333`).

| Behaviour | Where | Detail |
|---|---|---|
| Tip glint | `UryuSeeleFx.java:745` | Steady 0.85 under Reduce Motion or photosensitive safe; otherwise 0.75 + 0.25 sin(40 t) |
| Hitstop 40 ms | `UryuSeeleFx.java:540` | Skipped under Reduce Motion or photosensitive safe (before: Reduce Motion only) |
| Blade glow clip | `UryuSeeleFx.java:741` | `nearClip` caps width at 0.5 near the camera; the blade glow is 0.30 wide, so this clip never changes it |
| Shake and FOV kick | `UryuSeeleFx.java:398`, `:328` | These calls pass no Reduce Motion flag in this file; `ScreenFx` internals were not read |

Stale text: the class javadoc (`UryuSeeleFx.java:45`) says a ribbon of 12 segments; High now draws 24 points per arc.

## 4 Shared-file edits (git diff `3f122a4^..3f122a4`)

No other file was touched (no `FxConfig`, `ScreenFx`, `FxClient`, storyboard). `SeeleFxMath` is in `core` (no Minecraft types) and is imported by `Phase7Seele.java:21`.

| File:line (at 3f122a4) | Change |
|---|---|
| `UryuSeeleFx.java:134` | `Image.local` field (new) |
| `UryuSeeleFx.java:283-289` | First person flag, tier, skip of the 1.55 strip in first person |
| `UryuSeeleFx.java:299-311` | Dash count via `tierCount`; first person dash placement (sides, low) |
| `UryuSeeleFx.java:316` | Start dashes via `tierCount` |
| `UryuSeeleFx.java:350` | `im.local = c.local` |
| `UryuSeeleFx.java:540` | Hitstop also skipped under photosensitive safe |
| `UryuSeeleFx.java:739-743` | Blade glow strip width through `clip` (first person) |
| `UryuSeeleFx.java:745` | Steady tip glint under Reduce Motion or photosensitive safe |
| `UryuSeeleFx.java:759-762` | Points = segments x subdivision; overlap; gain |
| `UryuSeeleFx.java:767-802` | Outer wave as overlapped streaks (`windowStart`, weight 0.7 / 0.3), core line per point |
| `UryuSeeleFx.java:897-899` | First person: copies within 1.6 blocks of the camera not drawn (local only) |
| `SeeleFxMath.java:63-66` | `AFTERIMAGE_STEP` 0.45 to 0.29; new `AFTERIMAGE_FP_MIN_DIST` 1.6 |
| `SeeleFxMath.java:245-271` | New `tierCount`, `subdivision`, `ribbonOverlap`, `windowStart`, `ribbonGain` |
| `SeeleFxMathTest.java:227-253` | New test |

## 5 Tests

Unit (`SeeleFxMathTest`, new in 3f122a4):

- `arcsAreFinerOnHighAndTheOverlappedRibbonKeepsItsBrightness`: subdivision 2 / 1 / 1 for tiers 2 / 1 / 0; segments x subdivision = 24 on HIGH; overlap 4 (HIGH) and 3 (LOW, MEDIUM); `windowStart` values; `ribbonGain(1)` = 1.0 and decreasing; `ribbonGain(o) x o x 0.59` = 1; `tierCount` halves on LOW (12 to 6, 8 to 4, 7 to 4), keeps 12 on MEDIUM and HIGH; `AFTERIMAGE_STEP` about 0.29, and equal to lunge speed x 20 x gap within 0.01.

Other tests of the class, unchanged by 3f122a4 (names only): `slashTimesAreThoseOfTheServerCombo`, `profilesAreRightToLeftBackAndAnOverheadChop`, `everyFrameIsOrthonormalAndWorksForAnyLook`, `trailsTaperFadeAndTheOuterWaveRunsOut`, `firstPersonRibbonsAreClippedNearTheCamera`, `groundCrackLiesAheadWithinTheReach`, `hitsAndScreenEffectsFollowTheTable`, `lungeTableMatchesTheStoryboard`, `soundCuesUseKnownVanillaIdsAndSaneLevels`.

Harness (`Phase7Seele`, run with `gradlew runPhase7 -Phold=uryu_seele`; not run here). Screenshots are `run/screenshots/p7_05_seele_*.png` (`Phase7Uryu.shot7`):

| Scenario | View, time, zombies | Shots (ms) | Checks in code |
|---|---|---|---|
| `uryu_seele` setup | none | none | mesh `uryu_seele_long` loads with 8 or more quads |
| `back` | third person back, noon, 3 zombies | `05_seele_t0`, `t150`, `t210`, `t300`, `t400`, `t460`, `t650`, `t710`, `t850` | strokes played (0 / 1 / 2 / 3 by time band, one of tolerance at 150, 400, 650); blade drawn and length above 0.9 for 150 to 849 ms; glow sprites above 20 at 210, 300, 460, 710; hits routed from 300 ms |
| `front` | third person front, no zombies | `05_seele_front_t210`, `t300`, `t460`, `t710` | strokes and glow as above |
| `fp` | first person, 3 zombies | `05_seele_fp_t210`, `t300`, `t460`, `t710` | as `back` |
| `night` | third person back, midnight, 3 zombies | `05_seele_night_t300`, `t710` | as `back` |
| `uryu_seele_live` | third person back, noon, 3 zombies; state `shikai uryu`, key B (`ReiatsuKeys.SLOTS[2]`) | `05_seele_live_a`, `05_seele_live_b` (free run) | 1 or more combo, exactly 3 slashes, 3 or more hits |

The harness does not assert the first person strip, the first person dash placement, or the afterimage distance rule.

## 6 UNVERIFIED in game

Nothing here has been seen in game or in the screenshots.

| Item | Look for | Check |
|---|---|---|
| Crescent ribbon overlap (no saw-tooth) | an even band, no notches along the outer wave | `p7_05_seele_t300`, `t460`, `fp_t300` |
| First person lunge | no strip across the middle of the screen at head height; dashes only to the sides | `p7_05_seele_fp_t210`, own view |
| Afterimages hidden near the camera | no copy in the face of the owner; copies behind the runner, 0.29 blocks apart | `p7_05_seele_fp_t210`, `t300` |
| Blade glow in first person | the 0.30 glow strip is not clipped (see section 3) | `p7_05_seele_fp_t300` |
| Third slash ground crack and ring | crack about 2.4 blocks ahead at the end of the arc, ring 0.4 to 4.4 | `p7_05_seele_t650` and `t850` |
| Hitstop | 40 ms felt on the third hit only | in game |
| Sound pitch and volume | the cues in section 2 | in game |
| Tier counts | Low: 6 and 4 dashes, no afterimage; Medium: 2 copies; High: 24 points per arc | visual, per tier |

Deviations from storyboard row 2.9 (code as written, not checked against the row by eye):

| Storyboard row 2.9 | Code |
|---|---|
| Owner FOV +3 for 0.05 / 0.25 s | +3 over 0.30 s (`LUNGE_FOV_SECS`) |
| Platform disc 2.15 | 1.8 (`DISC_SIZE`), life 0.5 |
| Lunge streaks at hip, chest, head | heights 0.35, 0.95, 1.55 above the feet |
| Slash sounds include `entity.player.attack.sweep` 1.5 / 1.7 | not in the slash cues; the server plays the sweep |
| Crescent "life 0.25" | outer wave 0.30 s; echo 0.25 s |
| One ground ring 0.2 to 2.2 | a ring at the hit target (0.4 x 2.64) and a ground ring (0.4 x 4.4) |
| Reduce Motion: FOV none | the FOV kick is not gated in `UryuSeeleFx` |
| Crescent 12 segments | 12 on Medium, 24 on High |

## 7 Manual checklist

Note: the brief named "Blut Arterie" for this checklist. In the code, Blut Arterie is the separate SHIKAI release of U-FX 6 (`UryuGestures.blutArterie`). This list uses Seele Schneider on slot B in SHIKAI (`reiatsu state shikai uryu`). Confirm which state was meant.

- [ ] Mob: a zombie 2 blocks ahead in the cone. Press B. Lunge forward, three cyan copies behind, throw and sweep sounds, 3 degree FOV kick in your own view.
- [ ] Three slashes in third person (back) and in front: 1 right to left, 2 left to right, 3 overhead. Crescents in the right planes, ribbon smooth.
- [ ] Third slash knocks the mob back, a ground crack and ring at the end of the arc, 8 radial dashes, 40 ms hitstop on the hit.
- [ ] Player target (second account or client): lunge and slashes visible from the target's side.
- [ ] First person: no strip at head height, dashes to the sides, the sweep visible, no copy in your face.
- [ ] Reduce Motion on: tip glint steady, no hitstop on the third hit.
- [ ] Photosensitive safe on: same as Reduce Motion for glint and hitstop.
- [ ] Low, Medium, High: dashes halved on Low, afterimages 0 / 2 / 3, arcs smooth on High.
- [ ] Blade breaks up about 0.85 s after the lunge.
