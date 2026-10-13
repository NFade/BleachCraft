# r8 fx1: straighter Byakuya blades, lower first person hands, pink melee trail (2026-10-13)

Worktree `.claude/worktrees/r8-fx1` (branch `r8-fx1`). Nothing was run in the game (no runClient): every number below comes from geometry and projection maths and
unit tests. Everything about the LOOK is UNVERIFIED in game. Build: `./gradlew build` (see the end).

## 1. Byakuya bankai blades: katana, not scimitar

Measured from `byakuya_bankai_blade.obj`: the centreline of the blade (middle of the Z extent of every ring of vertices) leaves the axis by 0.014 m at y 2.0, 0.07 at 3.5,
0.14 at 4.5, 0.24 at 5.3, 0.35 at 6.0, 0.43 at 6.5, 0.53 at 7.1, 0.76 at 7.84 and 0.826 m at the apex (y 8.0). The bankai rows scale the width axis (z) by
`kz = 1.5`, so the user saw a sori of 1.24 m on an 8 m blade (ART_BIBLE E14 says 0.75 m).

Change: when the mesh is loaded the client keeps the export and builds a SHEARED copy, `z' = z - (1 - curve) * spine(y)` (y, the length and the glow height 6.85 do not change,
x thickness untouched, normals tilted with the inverse transpose of the shear). `curve` = `bankai.bladeCurve` in `run/fx_override.json` (0 straight, 1 as exported, **default 0.2**),
read live every frame: the copies are rebuilt when the value changes (about 100 vertices) and the static row buffers are rebuilt (`State.vboCurve`). The LOD mesh is sheared with
the spine of the full mesh, so both end on the same apex.

| curve | tip offset (model, z) | with width 1.5 (m) |
|---|---|---|
| 1.0 (old) | 0.826 | 1.24 |
| 0.2 (new default) | 0.165 | 0.25 |
| 0 | 0 | 0 |

Kept consistent: `BankaiBladeRenderer.tipZ()` = exported apex z (`-0.826`, measured from the mesh spine) times `curve()`; used by `tipPosition()` (the tip glow billboards, the dissolve and regrowth cut
glow, `RowsFx` line about 653). The cut cap of the dissolve (`cutY`) collapses the sheared vertices, so it is in the same frame. The frustum boxes use `r = 0.9 * scale * max(1, kz, kx)`, still conservative (the tip
moves less now). The 3 degree lean toward the axis is unchanged: with the old curve the tip was 1.24 + 0.49 m inward, now 0.25 + 0.49 m; `BladeRowLayoutTest.laneStays...` still holds (it uses the old, larger curve, so a safe bound).
UNVERIFIED: whether 0.2 reads as katana in game; `bankai.bladeCurve` 0.1 .. 0.35 is the range worth trying. The slight hook of the last 0.9 m (kissaki) is kept at 20 percent.

Code: `core/fx/BladeStraighten.java` (pure maths), `client/fx/FxMesh.reshaped`, `client/fx/BankaiBladeRenderer` (curve(), tipZ(), refreshCurve()), `RowsFx.State.vboCurve`.
Test: `BladeStraightenTest` on the shipped OBJ files (spine = the 0.826 m, curve 1 identity, curve 0.2 gives 0.165, length/x untouched, LOD apex equal, normals unit and perpendicular to the sheared faces).

## 2. First person hands too high

### The rule

No part of an arm (the vanilla arm box 4 x 12 x 4 px, fist included; both arms) may project into the central **18 percent x 18 percent** of the picture (ndc |x| < 0.18, |y| < 0.18). The sword is not an arm
(its blade may cross the middle). Checked exactly (`core/combat/FpCentre`): the box is clipped at the near plane, projected with the same FOV 70 maths as `FpClamp`/`FpGeometry`,
the convex hull is tested against the rectangle by the separating axis test; margin > 0 = clear by that many ndc units.

Enforced at runtime, not only tuned: `FpClamp.Limits` got `centreX/centreY` (default 0.18, off with 0); the factors that already scaled a gesture down for the near plane now also keep both arms
out of the rectangle (bisection, falls at once, comes back over `release`). The empty hands of the Byakuya bankai get the same with `FpCentre.freeFactor` in `FpCast.renderFreeHands`.
The gestures also start smaller: `fp_cast.hands.scale` 0.75 (new, hand rows only, not the camera nudge), `fp_cast.hands.drop` 0 (new, blocks the hands work lower).

### Unit test over all states (`FpCentreTest`, aspect 4:3, 16:9, 21:9)

- drawn stance, two handed guard, sheathed rest, sheathed block, free hands idle (both arms): clear of the rectangle by the margins in the table.
- EVERY frame (60 Hz) of EVERY gesture of `CastGestures.defaults()` (15 with a sword, 4 with empty hands), through the shipped pipeline (`shape` then `FpClamp.factors`, `freeFactor`): margin >= 0 at every frame.
- the gestures are not silenced: the mean of the limiting factor over the 15 sword gestures is above 0.85 (measured 0.95 for the new stance at 16:9).
- projection maths: box in the middle / at the side / crossing the near plane / behind the camera, a diagonal hull against the rectangle.

Not covered by the test (stated, not hidden): the strike (drawn by Better Combat since round 7; our own profile strikes only with `fp_bc_mode 0`), the camera nudge of the gestures (pitch/yaw/roll/fov of `CastGesture.c`
shifts the whole picture by a few degrees), the left hand that holds the scabbard in the sheathed block (client code, `turnSealedLeftArm`; only the right arm is checked there),
the weight blend between the one handed rest and the stance. The earlier fp2 rework (arm turned about odd pivots, rolled back) was NOT redone: only the existing pose numbers moved.

### Table: pose family, old y, new y, why

Numbers are the projection (ndc, 16:9) of the right arm box: `top` = highest point of the arm, `fist` = centre of the fist; margin = distance to the 18 percent rectangle.

| pose family (key) | old y | new y | effect (old -> new) | why |
|---|---|---|---|---|
| drawn stance (`fp_two.pos`, hand frame blocks) | 0.105 | 0.005 | fist -0.56 -> -0.70, arm top -0.34 -> -0.46, margin 0.157 -> 0.277; left fist -0.92 -> -1.07 (at the bottom edge) | the user: hands too high; the forearm top was a third of the way to the middle |
| two handed block (`fp_two.guard.pos`) | 0.22 | 0.10 | fist -0.42 -> -0.57, margin -0.013 -> +0.128 | the forearm of the old block reached the crosshair |
| sheathed rest (`fp_guard.sealed.rest`, NEW, shifts scabbard, sword, both hands) | 0 | -0.04 | fist -0.91 -> -0.96, arm top -0.59 -> -0.65 | small global drop; it fades out while the sheathed block comes up |
| sheathed block (`fp_guard.sealed.pos`) | -0.15 | -0.15 (unchanged) | fist already at -1.11 (edge), margin 0.62 | lowering more would push the fist out of the picture |
| empty hands idle, right (`fp_cast.free.idle_r`, camera space y) | -0.27 | -0.39 | arm top -0.11 -> -0.32, margin -0.066 -> +0.142 | the raised forearm was inside the rectangle all the time in the bankai |
| empty hands idle, left | -0.47 | -0.47 | margin 0.42 | already low |
| gestures with a sword (`fp_cast.hands.scale`) | 1.0 | 0.75 (rows) | frames with an arm in the middle 0 to 85 percent (see below) -> 0 at every frame | cast gestures covered half of the screen |
| gestures, free hand rows | 1.0 | 0.75 + centre factor | same | same |

Frames of a gesture with an arm in the rectangle BEFORE this change (old stance, unscaled), measured at 60 Hz: rukia.shikai.release 48 percent, tsukishiro 7, hakuren 59, shirafune 77, rukia.bankai.release 28,
byakuya.shikai.release 62, mode_attack 64, mode_barrier 85, kido.byakurai 59, shakkaho 70, sai 49, steer 39, ice_spike 75, cage 76. AFTER: 0 at every frame (unit test). The price: the sustained holds
(mode_barrier, cage, shakkaho, hakuteiken, senkei) are scaled down to 50 to 85 percent at their widest point instead of crossing the middle.

### Runtime keys (`run/pose_override.json`, dev)

- stance / block: `"fp_two": {"pos": [-0.47, 0.005, 0.06], "guard": {"pos": [-0.32, 0.10, -0.06]}}` (a flat `pos`/`guard.pos` replaces the whole vector).
- sheathed rest: `"fp_guard": {"sealed": {"rest": [0, -0.04, 0]}}`.
- gestures: `"fp_cast": {"hands.scale": 0.75, "hands.drop": 0.0, "clamp": {"centre_x": 0.18, "centre_y": 0.18, "screen_y_min": -1.2}, "free.idle_r": [0.20, -0.39, -0.85], "free.release": 0.15}`
  (`hands.*` and `free.*` are flat keys, `clamp` is an object; centre 0 switches the rule off; `screen_y_min` default was -1.05, the fists may sit at the bottom edge now).

Changed tests: `TwoHandMotionTest` unchanged (bands still hold), `FpClampTest` (old reach limits only, fist y floor -1.21), `FreeHandsTest` (right idle only 0.05 above the left), new `FpCentreTest`.

## 3. Byakuya melee trail, pink

New: `core/fx/MeleeTrailMath.java` (pure maths) + `client/fx/MeleeTrailFx.java` (registered in `FxClient.init`), test `MeleeTrailMathTest`.

- Trigger, local player: a SECOND listener on `BetterCombatClientEvents.ATTACK_START` (the one of `FpMotion` stays skipped while Better Combat draws the first person strike). It takes the animation name
  (`SwingMotion.profileFor`: horizontal_left/right, vertical_*, stab/thrust, uppercut, else the combo order) and the upswing ticks (same API as `FpMotion`), and starts the trail when the upswing ends (the hit moment), `FxDelayed`.
- Only Byakuya (`ZanpakutoItem.character() == BYAKUYA`) in SHIKAI or BANKAI with the main hand; Rukia unchanged.
- Other players: Better Combat has no event for them. The start of the vanilla swing animation (`handSwinging` and `handSwingTicks == 0`, main hand, polled each client tick, within `fx.meleeTrail.range` 48 blocks)
  stands in; the profile is the combo order of the katana by the swings in a row (30 ticks), the upswing a fixed 0.28 s (`fx.meleeTrail.upswing`). UNVERIFIED: whether Better Combat makes remote players swing the vanilla way.
  If it does not, remote players get no trail (local only).
- Path: tip track = spherical arc of radius `reach` (1.85 blocks from the shoulder) in the player's view frame, per profile (yaw/elevation of start and end: H_R 62 -> -62 / 6 -> -8, H_L mirrored, V_R 22 -> -4 / 72 -> -40, V_L mirrored,
  UPPER 26 -> -6 / -48 -> 66, STAB straight forward 0.5 -> 1.12). The swept blade is the radial line from 0.38 to 1.0 of the reach. Frozen in the world at the hit moment (it stays where the sword went).
- Look: 32 glow sprites (GLOW_SOFT, #F9C8F6 with every third #F3E4FF) along the tip edge and the blade, 24 `FxParticles` PETAL (#F9C8F6 fading to #F3E4FF, brightness 1.5, drifting along the sweep) over the swept blade, 2 STAR4 flares at the start and the end.
  **58 motes per swing, fixed budget 60** (`MeleeTrailMath.BUDGET`; counts do not change with the scale). The point at arc parameter u appears u * 0.10 s after the hit and everything is gone after 0.25 s, so the blade
  assembles along the path in a tenth of a second and fades. Deterministic scatter (hash of the seed, no Random).
- Switches (`run/fx_override.json`): `fx.meleeTrail` 1/0, `fx.meleeTrail.scale` (reach, sizes, drift; default 1), `fx.meleeTrail.reach` (1.85), `fx.meleeTrail.glow` (1), `fx.meleeTrail.upswing` (0.28, remote only), `fx.meleeTrail.range` (48).
  Photosensitive safe mode turns it off, reduce motion halves the glow. The effect quality / particle cap of `FxConfig` still apply to the petals and sprites.
- UNVERIFIED in game: the arcs against the real Better Combat animations (they are plausible tracks, not sampled), the size and brightness, the world position vs the camera in first person (origin = eye - 0.30 m).

## Verification

`./gradlew build --console=plain` in `mod/` (log /tmp/r8fx1.log): see the report. Tests added: `BladeStraightenTest`, `FpCentreTest`, `MeleeTrailMathTest`.
