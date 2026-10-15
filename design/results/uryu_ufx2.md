# Uryu U-FX 2: results note

Stage U-FX 2 of URYU_VFX_STORYBOARD (2.1 bow forming, 2.2 draw light, 2.3 basic arrow, 2.4 Focus feedback, section 6 harness rows p7_02_*).

Branch `uryu-fx-wip`. Commits covered, in order:

| Commit | Subject (short) | Scope |
|---|---|---|
| d14a64c | WIP Uryu effects U-FX 2/4/5/6 (base, read here only where the stage depends on it) | pure core classes UryuBowForm, UryuBowGlow, UryuDrawLight, UryuArrowLook, UryuFocusLook, UryuEase; item-model hooks; first tests of these classes |
| 476896d | U-FX 2: UryuBowFx clock + UryuFocusFx bookkeeping so the tree compiles again | UryuBowFx, UryuFocusFx |
| ad27c7a | U-FX 2 step A: tree compiles and builds green | SeeleFxMath (2 lines) |
| d4ef88c | U-FX 2 step B: bow forming, draw light, Quincy arrow, Focus feedback, harness p7_02_* | 18 files, +2204 / -122 |

Merged into the branch by dad7b03 (poses branch). That merge touched none of the files listed in sections 1 and 3 (it touched ClientOptions, FpCast, FpMotion, UryuBowMotion, UryuBowPose, UryuTpLayer and the core combat pose classes).

Note: the numbers below are read from the committed code at d4ef88c. Nothing here was run in the game, and the test suite was not run for this note (see sections 4 and 5).

---

## 1. What was built

One line per file. "new" = added by these commits; "changed" = edited by these commits; "base" = added by d14a64c and only read here.

Client (`mod/src/client/java/dev/minebleach/reiatsutest/client/`):

| File | Status | What it does |
|---|---|---|
| fx/UryuBowFx.java | changed (476896d, d4ef88c) | Bow forming (2.1) and draw light (2.2) per frame: gather, wheel, limbs, string sweep, settle, reverse; draw nock, Reishi dashes, tips, ping, ghost wheel; owner FOV bias; muzzle flash term; `formAge` and `glowMultiplier` read by the item model. 513 lines at d4ef88c. |
| fx/UryuArrowFx.java | new | Muzzle (star, ring, dashes, sounds, owner recoil), hit (star, rays, dashes, flakes, Focus 3 ring and flash, glass sounds, floor scar), shooter lookup. |
| fx/UryuArrowRenderer.java | changed | Live arrows from the ARROWS packets, extrapolated on the FX clock; `uryu_fx/uryu_arrow` mesh at scale from draw power and Focus; glow strip, flechette trail, flake sparks, steering ring; calls UryuArrowFx; IMPACT matched to the ended arrow. |
| fx/UryuFocusFx.java | changed (476896d, d4ef88c) | Focus stacks of all Uryu players (own from `UryuFx.focus`, others in a map); pop, reset burst, hum; layers 1 to 3 (dashes, bow glow via UryuBowFx, ground ring, orbit flakes, cross, pentagon, rim glow, tip glints). 280 lines at d4ef88c. |
| fx/UryuAuraFx.java | changed | Focus part moved out: its stack map and imports are gone, `frame` calls `UryuFocusFx.cleanup` and `frame`, new `emitGlow` delegates to `UryuFocusFx.emitGlow`. |
| fx/UryuAnchors.java | new | Client side of UryuBowAnchor: camera basis, entity aim, hand (grip, nock, tips), display scale 1.4 third / 1.8 first, hand sign from `FxTune` key `uryu.bowHandSign`. |
| fx/UryuFxStats.java | changed | Counters for the harness: muzzles, hits, sparks, focus sprites, pops, losses. |
| fx/UryuFx.java | changed | `ensureInit()` (idempotent), hooks: `UryuBowFx.frame`, `UryuBowFx.emitGlow`, `UryuAuraFx.emitGlow`. |
| fx/FxClient.java | changed | One line: calls `UryuFx.ensureInit()` at client start. |
| fx/ScreenFx.java | changed | Steady FOV offset `fovBias` added to the kick; `setFovBias(double)`. |
| dev/Phase7Bow.java | new | Harness scenarios uryu_bow, uryu_draw, uryu_arrow, uryu_arrow_live, uryu_focus, uryu_f12 (461 lines). |
| dev/Phase7Uryu.java | changed | Dispatches to `Phase7Bow.steps()` when one of the six scenarios is wanted. |
| model/ObjItemBakedModel.java | base (read only) | Calls `UryuBowFx.formAge` and `glowMultiplier`; `emitBowForming` draws the forming bow on the item side. |

Core (`mod/src/main/java/dev/minebleach/reiatsutest/core/fx/`), all pure, no Minecraft types:

| File | Status | What it does |
|---|---|---|
| UryuBowForm.java | base, changed +21 (d4ef88c) | Forming timeline, gather counts, wheel, limb and notch curves, bend, flex index, string sweep, settle, reverse, cue table (sounds). |
| UryuBowGlow.java | base | Combines Focus, draw power and muzzle flash into one emissive multiplier (cap 2.6); recolours ARGB. |
| UryuDrawLight.java | base, changed +31 (d4ef88c) | Draw light curves in power p, FOV target and step, ping, breathing, nock and tip distances, scales, sounds. |
| UryuArrowLook.java | base, changed +48 (d4ef88c) | Arrow mesh scale, streak, trail, flake spacing, steering ring, muzzle and hit tables, hit level, sound cues, spark spacing, draw power from speed. |
| UryuFocusLook.java | base | Focus look per stack: dashes, bow glow, layers, ring, orbit, cross and pentagon numbers, pop and reset, hum, quality tiers. |
| UryuEase.java | base | Easing helpers (oc, ob, oe, iq, ios, smooth, lerp, clamp). |
| UryuBowAnchor.java | new (d4ef88c, 102 lines) | Grip (third person idle and drawn, first person), nock (pulled back along aim; first-person version kept in front of the camera), tips, `towards`. |
| SeeleFxMath.java | changed (ad27c7a) | Two lines: easeOutExpo near 1 uses a 1e-9 tolerance; Seele cue for case 1 pitch 2.1 changed to 2.0. Not part of U-FX 2 proper, but in its commits. |

---

## 2. Timings and numbers in force

All values are the constants in the core classes at d4ef88c.

### 2.1 Bow forming (UryuBowForm)

| Quantity | Value |
|---|---|
| Item draw animation (`DURATION`) | 0.45 s |
| World side (`TOTAL`) | 0.60 s |
| Reverse (J again, `REVERSE_DURATION`) | 0.35 s |
| Event times | gather 0.00, wheel 0.10, limbs 0.18, solid 0.30, string 0.38, settle 0.42, wheel gone 0.55 |
| Gather | 40 dashes, 24 motes, 12 specks; radius 3.0 m; life 0.30 s; pendant boost x1.6 over 0.10 s |
| Wheel | 0.15 to 0.90 m over 0.25 s (easeOutExpo) from 0.10; rotation 0.4 turn/s; first person capped at 0.55 m; settles to 0.25 m by 0.55 s |
| Limbs | 0 to 0.55 m each way over 0.20 s (easeOutExpo) from 0.18; core width 0.04, glow width 0.18 |
| Notches | 6 per limb, 0.02 s apart, life 0.10 s |
| Solid bow | alpha 0 at 0.30, 1 at 0.45 (`SOLID_FADE` 0.15 s); pendant hidden before 0.30 |
| Bend | 8 deg until 0.30, back to 0 at 0.45 (easeOutBack, c1 1.2); flex bows at 3, 6, 9 deg |
| String sweep | 0.07 s from 0.38 |
| Settle | 6 flakes, speed 2.0, life 14 ticks; ring 0.8 m, life 0.2 s |
| Reverse | wheel 0.15 m for 0.10 s; outline 0.10 s; 16 dashes, life 0.25 s |
| Remote players | sounds x0.6, glow x0.6, range 48 blocks |

Sound cues of the forming (time, vanilla id, pitch, volume; owner's volume, remote x0.6):

| t | id | pitch | volume |
|---|---|---|---|
| 0.00 | block.amethyst_block.resonate | 1.6 | 0.35 |
| 0.10 | block.beacon.power_select | 1.9 | 0.40 |
| 0.18 | entity.illusioner.cast_spell | 1.8 | 0.30 |
| 0.30 | block.amethyst_block.chime | 1.4 | 0.45 |
| 0.38 | entity.arrow.shoot | 2.0 | 0.15 |

Reverse cue: block.amethyst_block.resonate, pitch 1.2, volume 0.25.

### 2.2 Draw light (UryuDrawLight, UryuBowGlow, UryuBowAnchor)

Power curve: `p = (u^2 + 2u) / 3`, `u = ticks / 20` (`BowDraw.powerAt`, `UryuBalance.BOW_FULL_DRAW_TICKS` = 20). The shot's launch speed is `2.2 * (0.3 + 0.7 p) * focus factor` (`UryuBalance.ARROW_SPEED`, `BOW_MIN_SPEED_SHARE` 0.3, `BowDraw.speedFactor`).

Ticks to reach a power (`UryuDrawLight.ticksForPower`, computed from the formula):

| p | ticks |
|---|---|
| 0.10 | 2.8 |
| 0.25 | 6.5 |
| 0.50 | 11.6 |
| 0.75 | 16.1 |
| 1.00 | 20.0 |

Layers as functions of p:

| Layer | Value |
|---|---|
| String brightens (Q2 to Q1) | full at p 0.1 (`STRING_FULL_AT`) |
| Nock pinpoint | size 0.12 (x1.6 in draw code), full at p 0.1 |
| Reishi dashes into the nock | 6 + 26 p per second (6/s to 32/s), sphere radius 1.2, life 0.18 s, cap 4 per frame |
| Glow strip along the arrow | 0.15 + 0.45 p m |
| Bow tips glint | from p 0.5; twinkle 6 Hz, size 0.22 |
| Ghost wheel on the grip | size 0.35, alpha 0.12, from p 0.5 |
| Full-draw ping | ring 0.2 to 0.7 over 0.18 s (easeOutCubic); star size 0.5 for 0.10 s |
| Breathing glow at full draw | 0.85 to 1.0 at 3 Hz; constant 0.92 with Reduce Motion |
| Owner FOV | target -1.5 deg at full draw, moved at the rate that covers 1.5 deg in 0.35 s; 0 with Reduce Motion |
| Shot recoil (owner) | pitch +0.4 deg for 0.10 s; 0 with Reduce Motion |
| Chime at p 0.5 / full draw | pitch 1.3 vol 0.2 / pitch 1.7 vol 0.35 (`block.amethyst_block.chime`) |
| Display scale | 1.4 third person, 1.8 first person |
| Nock distance | scale x (0.105 + 0.04 + 0.26 p) behind the grip; tip distance scale x 0.55 |

Emissive multiplier of the bow in the hand (`UryuBowGlow.bow`): `min(2.6, focus_factor + 0.25 p + flash)`, where the Focus factor comes from UryuFocusLook (table in 2.4) and the flash is up to 1.0 for 0.08 s after a shot. The result is multiplied by the breathing factor.

### 2.3 Quincy arrow (UryuArrowLook, UryuArrowRenderer)

| Quantity | Value |
|---|---|
| Mesh scale | 1.4 + 0.8 p, +0.4 per Focus stack, max 3.0; Z arrow 2.6 |
| Glow strip | length 0.9 + 1.2 p m, alpha 0.5, width 0.14 |
| Head pinpoint | 0.3 m, alpha 0.6 |
| Trail | last 8 packet positions (Z 14, rain 3); width 0.16 (Z 0.28, rain 0.10); alpha 0.7 at head falling linearly to 0 at tail; width to a quarter at the tail |
| Flake sparks | one every 2.0 blocks at Focus 2 and 3 (Z 1.2), life 8 ticks, size 0.32 |
| Steering ring | size 0.6 (Z 1.0), 0.5 m behind the head, alpha 0.35, one turn per second |
| Whoosh | `entity.phantom.flap` pitch 2.0 vol 0.05 every 6 ticks within 12 blocks |
| Muzzle star | 0.5 + 0.7 p (Z 1.2), life 0.10 s |
| Muzzle ring | 0.3 to 1.2 m over 0.15 s (Z to 2.0) |
| Muzzle dashes | 6, speed 10 blocks/s, life 0.15 s |
| Muzzle sounds | `entity.arrow.shoot` pitch 1.6 + 0.4 p, vol 0.5; `block.amethyst_block.chime` 1.8, 0.3 |
| Bow flash | emissive +1.0 for 0.08 s |
| Hit star | 0.9 + 0.5 level, life 0.18 s |
| Hit rays | 1.0 + 0.6 level, life 0.14 s |
| Hit dashes | 10 + 6 level, speed 6 blocks/s, life 0.18 s |
| Hit flakes | 2 |
| Focus 3 hit ring | 0.5 to 3.0 m over 0.3 s; flash peak 0.12 for the shooter |
| Hit level | Focus stacks; Z arrow one higher (max 3) |
| Hit sounds | `block.amethyst_block.hit` 1.6 / 0.6, `entity.arrow.hit` 1.8 / 0.35; Focus 3 adds `block.amethyst_block.break` 1.4 / 0.4 |
| Block hit | star 0.45, 6 dashes, 4 flakes, `block.amethyst_block.hit` 1.9 / 0.3; scar radius 0.25 for 0.6 s if the hit height is within 0.06 of a whole block |
| Matching | an IMPACT packet is matched to the arrow that just ended within 4.5 blocks; an arrow starting within 6.0 blocks of the eye is the local player's |

Draw power of a flying arrow is read back from its speed (`UryuArrowLook.drawPower`); other kinds fly at fixed speed.

### 2.4 Focus feedback (UryuFocusLook, UryuFocusFx)

Per Focus stack (quality 1.0 = high; medium 0.5 has 3 layers and dashes x0.75; low 0.25 has layers 1 and 2 only and dashes x0.5):

| Stacks | Dashes per second onto the hand | Bow glow (emissive x) | Layers drawn | Hum (every) | Gain chime pitch |
|---|---|---|---|---|---|
| 0 | 0 | 1.0 | none | none | n/a |
| 1 | 6 | 1.3 | 1 (Reishi to the grip) | none | 1.2 |
| 2 | 10 | 1.45 | 1 and 2 (ground ring, 3 orbit flakes) | `block.beacon.ambient` 1.8, vol 0.08, every 2.0 s | 1.5 |
| 3 | 14 | 1.6 | 1, 2 and 3 (cross, pentagon, rim glow, tip glints) | `block.beacon.ambient` 2.0, vol 0.12, every 1.4 s | 1.9 |

Other numbers: layer fade-in 0.2 s; gain pop star 0.5 for 0.15 s and 8 dashes from radius 1.2; gain chime volume 0.4; reset burst 12 dashes from radius 1.6 (life 0.15 s), `block.amethyst_block.break` pitch 1.9 vol 0.15; ground ring size 1.8, alpha 0.35, breathing 0.8 to 1.0 at 1 Hz; orbit flakes radius 0.9, height 0.5 to 1.6, 0.12 turn/s; cross size 0.9 (first person 0.5, alpha 0.35), 0.2 turn/s; pentagon size 2.8, alpha 0.45, -0.15 turn/s; rim glow size 2.4, alpha 0.18, height 1.0 (third person only); tip glints 0.22 at 6 Hz. Reduce Motion: no orbit flakes, no rotation. Hum range 24 blocks.

---

## 3. Shared-file edits with file:line

Line numbers are at d4ef88c (the state after the three commits).

| File | Lines | Commit | Edit |
|---|---|---|---|
| client/fx/FxClient.java | 62 | d4ef88c | `UryuFx.ensureInit();` added after `UryuArrowRenderer.init();` in the client start path. |
| client/fx/UryuFx.java | 42 to 45 | d4ef88c | New `public static void ensureInit()` calling `init()`. |
| client/fx/UryuFx.java | 186 | d4ef88c | `UryuBowFx.frame(now);` in the per-frame method. |
| client/fx/UryuFx.java | 193 to 194 | d4ef88c | `UryuBowFx.emitGlow(e, now);` and `UryuAuraFx.emitGlow(e, now);` in the glow source. |
| client/fx/UryuFx.java | 54, 78 | base (d14a64c) | `UryuBowFx.init()` and `UryuBowFx.onDraw(...)`, not changed here. |
| client/fx/ScreenFx.java | 351, 355 | d4ef88c | The two "return 0" branches now return `fovBias`. |
| client/fx/ScreenFx.java | 359 | d4ef88c | `return rushFov * k + fovBias;` (was `rushFov * k`). |
| client/fx/ScreenFx.java | 363 to 366 | d4ef88c | New `private static volatile double fovBias` and `public static void setFovBias(double degrees)`. |
| client/fx/UryuAuraFx.java | 38 to 41 | d4ef88c | `frame()` calls `UryuFocusFx.cleanup()` and `UryuFocusFx.frame(FxClock.now)`. |
| client/fx/UryuAuraFx.java | 44 to 46 | d4ef88c | New `emitGlow` delegating to `UryuFocusFx.emitGlow`. |
| client/fx/UryuAuraFx.java | old lines 3 to 6 and 16 to 18 | d4ef88c | Imports and the stack map removed (moved to UryuFocusFx). |
| client/dev/Phase7Uryu.java | 45 to 47 | d4ef88c | If one of the six scenarios is wanted, `Phase7Bow.steps()` is called. |
| core/fx/SeeleFxMath.java | 177 | ad27c7a | `u >= 1 - 1e-9` in easeOutExpo. |
| core/fx/SeeleFxMath.java | 369 | ad27c7a | Seele case 1 cue pitch 2.1 changed to 2.0. |
| client/model/ObjItemBakedModel.java | 154, 158, 162, 273, 282 | base (d14a64c), read only | Item-model hooks: `formAge`, `emitBowForming`, `glowMultiplier`. Not changed by these commits. |
| client/fx/UryuBowFx.java and UryuFocusFx.java | (whole files) | 476896d | Clock, `formAge`, `glowMultiplier` placeholder, Focus bookkeeping, so the tree compiles. |

---

## 4. Tests added

Added by these commits:

| Class | Tests | Lines | Covers |
|---|---|---|---|
| core/fx/UryuBowAnchorTest | 11 | 181 | yaw convention, idle and drawn grip, first-person grip, nock travel and the first-person nock staying in front of the camera, `towards`, forming cue order, draw sounds and FOV hook, arrow cue table, spark spacing with carry |
| core/fx/UryuBowAssetsTest | 5 | 124 | `zanpakuto/uryu_cross.json` parses, every named object exists in its model folder, textures exist, arrow along +Y, 0.5 m long, bow 1.1 m along Y with limbs bending to -Z |

Present from the base commit d14a64c for the same stage (not changed by these commits):

| Class | Tests |
|---|---|
| core/fx/UryuBowFormTest | 11 |
| core/fx/UryuBowGlowTest | 5 |
| core/fx/UryuDrawLightTest | 7 |
| core/fx/UryuFocusLookTest | 9 |
| core/fx/UryuArrowLookTest | 8 |

UryuEase has no test. The ad27c7a commit message says "tree compiles and builds green"; that claim was not re-run for this note.

---

## 5. UNVERIFIED in game

Nothing in this stage was run in the game. The Phase7Bow javadoc says the same ("Nothing here was run by the author"). The points below need a look in the game.

1. **Bow hand and nock and grip positions** are the storyboard's numbers (UryuBowAnchor javadoc). Third person idle: 0.35 side, 1.10 up, 0.45 forward; drawn: shoulder 0.31 side, 1.38 up, arm 0.70. First person: 0.30 right, -0.28 up, 0.55 forward. The p7_02_* shots show how far off they are.
2. **Option `uryu.bowHand` is not wired in this branch.** ClientOptions reads `uryu.bowHand` (ClientOptions.java:87 to 88, from the poses merge dad7b03). UryuAnchors.handSign still reads the FxTune key `uryu.bowHandSign` (UryuAnchors.java:22), so a left-handed setting does not mirror the FX. The comment at UryuAnchors.java:20 says the mapping is to be done when the poses branch is merged; the merge is in, the mapping is not.
3. **Sound ids unchecked.** All ids in the cue tables (section 2) are vanilla ids as written in the code; none were checked against the game's sound registry here. The UryuBowForm comment says "confirmed in U-FX 0"; that was not re-read here.
4. **Owner FOV narrowing** happens only in first person (UryuBowFx.java:194 to 195 gate the target on `UryuAnchors.first`); third person gets no FOV change in the code. Whether that is the intended behaviour was not checked in game.
5. **Numbers from the storyboard that the code notes as off**: the p 0.5 timing (11.6 ticks, not the "8 t" in the storyboard table; UryuDrawLight javadoc).
6. **Focus stacks on other players** depend on the server sending FOCUS to trackers (S-U2 in b230202); not checked in game.
7. **Frame cost and FPS (F12)**: not measured. The 4 ms and 60/45 FPS lines are the storyboard's pass lines, not results.
8. **Item model** (ObjItemBakedModel, base commit): emissive multiplier and the forming bow are drawn through the item model; not seen in game.
9. **Arrow mesh** `uryu_fx/uryu_arrow` and texture `uryu_fx_diffuse.png`: loaded by UryuArrowRenderer; not seen in game.

---

## 6. Manual checklist for the user

Run the game yourself (the author did not). For the harness, the scenario names are in Phase7Bow's javadoc (`gradlew runPhase7 -Phold=<scenario>`); the list below is for play.

1. **Forming.** Hold the Uryu cross (item `uryu_cross`). Press J. Watch the bow form from Reishi: gather dashes into the hand, the web wheel opens, two outline limbs shoot out, the solid bow fades in with a chime, a bright line runs the string, flakes and a ring at the hand. Press J again: the reverse (wheel, outline, dashes into the hand). Check from third person and first person; the wheel must stay off the crosshair in first person.
2. **Draw light.** Hold right click. Check: a nocked arrow with a pinpoint at the nock; Reishi dashes into the nock; the string brightens; the bow brightens. Tips glint from about half draw. At full draw: a ring and star ping with a chime, the glow breathes, and the view narrows slightly (first person only, about 1.5 degrees). With Reduce Motion on: no narrowing, no breathing.
3. **Release.** The arrow flies with a glow strip and a trail behind it; a muzzle flash and ring at the nock, a shot sound and a small recoil (first person). Hit a mob: star, rays, dashes, flakes, sound; at Focus 3 a ring grows and a glass break sound. Hit the floor: a small scar.
4. **Focus feedback.** Stand still or sneak-walk to get Focus stacks (as the Focus rules allow). At 1 stack: dashes into the hand and a brighter bow. At 2: a ground ring, three orbit flakes, a hum. At 3: the cross behind the bow, the pentagon on the ground, a rim glow, tip glints, a faster hum. Check the same on another player (they must be in tracking range, within 48 blocks). Losing a stack: a burst of dashes and a glass tick.
5. **Settings.** Check Reduce Motion (no rotation, no orbit flakes, no FOV change). Check a low graphics tier (only layers 1 and 2 at Focus 3). Note anything that looks wrong and send the p7_02 shots if you took them.
