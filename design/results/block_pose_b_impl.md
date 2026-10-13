# Block pose B ("raised chudan") implementation (2026-10-13)

Worktree `D:\MineBleach\.claude\worktrees\block-pose-b`, branch `feat/block-pose-b`, mod in `mod/`. Nothing committed, the game was not run.
Unit tests: `./gradlew test` in the worktree is GREEN (BUILD SUCCESSFUL, run 3).

## Files changed (all CRLF kept)

| File | What |
|---|---|
| `core/combat/TpPose.java` | class doc (state 4 "Block"), `GUARD_POS/TILT/FWD/LAT_R/LAT_L/C_L`, `guardFrame` doc. No solver code changed. |
| `core/combat/TwoHandMotion.java` | `GUARD` (first person, two handed) + docs. |
| `core/combat/GuardMotion.java` | `GUARD` (first person, one handed fallback) + docs. `SEALED_GUARD` untouched. |
| `test/.../TpPoseTest.java` | `blockCarriesTheSwordAcrossTheChest` replaced by `blockRaisesTheSwordDiagonallyInFrontOfTheBelly`; new `poseBHasTheBladeDirectionAndTheFistsOfTheRig`. |
| `test/.../TwoHandMotionTest.java` | `guardIsAcrossTheViewWithTheTipUpAndLeft` replaced by `guardIsTheRaisedDiagonalWithTheTipUpAndRight`; one assertion of `leftFistGripsTheHiltAndTheArmComesFromTheLowerLeft` re-based (see below). |
| `test/.../FpMotionTest.java` | `stanceIsRestAtZeroAndGuardAtOne`: tip direction assertions for the new one handed GUARD. |
| `test/.../BlockPoseBFpTest.java` | NEW: first person projection requirements (5 tests). |

`SealedGuardTest`, `FpCentreTest`, `FpClampTest` needed no change: they use the constants through the getters or pin geometry/safety that the new pose passes (FpCentre guard margin, "guard lower than the old one", near plane margin > 0.1).

## Third person

Pose B in Blockbench space (R = (1.8, 20.3, -8.2), L = (1.2, 17.7, -6.4), azimuth -20, elevation 55) -> body px (`x = -x_bb`, `y = 24 - y_bb`): R = (-1.8, 3.7, -8.2), L = (-1.2, 6.3, -6.4), tip direction (-0.19617, -0.81915, -0.53899).

| Constant | old (round3) | new |
|---|---|---|
| `GUARD_POS` (grip centre, px) | (0.5, 7.5, -6.0) | (-1.9569, 3.0447, -8.6312) = R + 0.8 * tip |
| `GUARD_TILT` | 40 | 55 |
| `GUARD_FWD` | 5 | 70 (azimuth 20 deg right: `tip = (-cos t cos f, -sin t, -cos t sin f)`) |
| `GUARD_LAT_R` / `GUARD_LAT_L` | 1.3 / -1.3 | 0 / 0 (both fists on the blade axis as in the rig) |
| `GUARD_C_L` | -2.4 | -4.0 (rig: L is 3.22 px behind R; right fist stays at C_R = -0.8) |

Torso yaw -7.2 deg is not part of TpPose (comment in the class doc). The blend from the hold frame, the sealed/scabbard path (scabbard blends to the same guard frame), the eased weight and left-hander mirroring are untouched code.

How checked (`TpPoseTest`):
- `poseBHasTheBladeDirectionAndTheFistsOfTheRig`: the expected direction is rebuilt from azimuth/elevation in Blockbench space and converted (not read from the constants); `guardFrame` and `solve()` tip direction (sealed p=0 and drawn p=1, right and left hander) equal it within 1e-6. Fists: right fist target < 0.01 px from R, left fist target < 0.1 px from L (L itself is 0.08 px beside the blade axis; fist is put on the axis), 3.2 +- 0.1 px apart along the blade, both on the axis (< 0.1 px), right fist to the right of the left one (arms not crossed), for classic and slim arms. Shoulder slide asserted < 4 px (limit `arm.max_shift` 6; hand by hand estimate about 1 px right arm, 2 px left arm).
- Existing `handsReachTheirPointsOnTheSword` (b = 1, all four hand/arm variants, 1e-6) proves the IK actually reaches both targets (so `max_shift` is not clipping), `heldSwordLandsOnItsFrame` the item correction, `leftHanderIsTheMirrorImage`, `blockEnterIsGradual`, `blockBlendIsContinuous`, `shouldersStayCloseToTheBody` (< 3.2 px) still pass with the new numbers.
- `blockRaisesTheSwordDiagonallyInFrontOfTheBelly` pins the new semantics instead of "blade covers the chest": tip x in (-0.3, -0.15), y < -0.75, z < -0.4; hands in front of the torso (z < -3.5), y 3..11, |x| < 3; pommel below the hands, tip above the shoulders; sealed scabbard travels with the sword, drawn scabbard stays on the hip.

## First person

Pose = `FpPose(x, y, z, rotX, rotY, rotZ)` in the item-renderer hand frame. Found by a numeric search (Nelder-Mead, 25 starts, a Python port of `FpRig` / `FpPose` / `FpGeometry` / `FpCentre`; the port reproduces the numbers written in the old code comments: old guard margin +0.128 at 16:9, stance fist ndc y -0.695) around the stance, then rounded and re-checked in Java by `BlockPoseBFpTest`. Blend weights/ramps unchanged.

| Pose | old | new |
|---|---|---|
| `TwoHandMotion.GUARD` (pivot `two.pivot_s`) | (-0.32, 0.10, -0.06, -14, -16, 54) | (-0.36, -0.25, -0.12, -41.5, 0.5, -5.3) |
| `GuardMotion.GUARD` (one handed fallback, pivot = grip) | (-0.22, 0.14, -0.04, -12, -22, 62) | (-0.317, -0.208, -0.337, -41.5, 0.5, -5.3) |

Measured for the two handed pose (Java test assertions in brackets):
- blade tip 17.6 deg above the horizon (15..20), 18.0 deg right of the middle; the line crosses the horizon 16.9 deg right (13..17.5); closest approach to the middle 16.9 deg (>= 16); tip leans right going up (tip az > horizon az + 0.5). In camera space the blade direction is azimuth 21.5 / elevation 54.7 deg, i.e. Pose B (20 / 55) within 3 / 2 deg (asserted in `TwoHandMotionTest`).
- blade vs the central keep-out rectangle (18 % x 18 % of ndc): margin +0.133 (4:3), +0.057 (16:9), +0.002 (21:9), all >= 0 (the old r8 guard was -0.019 / -0.044 / -0.066, i.e. the blade crossed it). During the blend from the stance (w = 0..1) the blade margin is > 0.03 at 4:3 and 16:9 and the closest approach > 16 deg. At 21:9 the margin is only +0.002 (the rectangle there is 16.4 deg wide in angle); the blade is a thin line in this check (no blade width).
- hands: right fist ndc (0.233, -0.878) at 16:9 (right of middle, near the bottom edge; stance was -0.695, old guard -0.571); left fist on the hilt (distance to the blade axis < 1e-4, between pommel and tsuba), below the picture (ndc y -1.22), a fist apart from the right one.
- arms (`FpCentre.margin`): right 0.49, left 0.81 for all aspects (old guard 0.097..0.154 at the three aspects), `FpClamp` near plane margin > 0.1 (existing test), pommel end below the picture.
- one handed fallback: same rotation, shift converted for the grip pivot and slid 0.10 blocks along the blade (keeps the fist on screen: ndc (0.23, -0.94)); tip 18.9 deg, closest approach 16.9 deg, blade rect margin +0.132 / +0.057 / +0.002. NOT fully fixed: the left hand supports the blade 0.72 blocks up the hilt (`guard.left.s` 0.30, layout not changed) so its arm still touches the keep-out rectangle (margin -0.026 at 16:9; the old pose had -0.34). This path is only used with `fp_two.enabled` 0 or no hand model.

## Test edits that were not pure renames (please look)

- `TwoHandMotionTest.leftFistGripsTheHiltAndTheArmComesFromTheLowerLeft`: the line `|g.x - rg.x + 0.1| > 0.02` ("the offset turns with the blade") only held because the old guard turned the sword 54 deg about z. Pose B turns it about x (the axis of the offset), so it is now: the carried offset equals `guard.applyDir(offset)` within 1e-5 and moves in y/z by > 0.005. The rigidity check is stricter, the "visible turn" threshold is smaller because the rotation is.
- `FpMotionTest`, `TwoHandMotionTest`: pins of "tip to the left / across the view" replaced by the Pose B direction.
- Nothing about FpCentre / FpClamp margins was weakened; `FpCentreTest` "guard lower than the old one" still passes (arm top ndc -0.67 against -0.19).

## UNVERIFIED in game / caveats

- Nothing was seen on screen. FP numbers rest on the Python/Java projection (vertical FOV 70, `FpGeometry`), which models the arm boxes and the item rig; the real display transform comes from the item JSON at runtime (same assumption as all earlier FP poses). Roll of the sword about its blade (edge direction) was not constrained by the references: kept near the stance (rotY 0.5, rotZ -5.3).
- **Dev override file shadows the new pose**: `D:\MineBleach\mod\run\pose_override.json` (read in dev only, not touched) has `tp.guard.pos`, `guard.tilt`, `guard.fwd`, `guard.edge`, `guard.lat_r`, `guard.lat_l`, `guard.c_l` and `fp_two.guard.pos` / `fp_two.guard.rot` with the OLD values, so a dev session would keep showing the old block until those keys are deleted (production uses the code defaults). The worktree has no `mod/run` yet; any `run` folder copied from the main checkout has the same problem.
- Sealed (sheathed) block: in third person the scabbard now follows Pose B too (same `guardFrame`); in FIRST person `GuardMotion.SEALED_GUARD` (scabbard across the view) is unchanged, so the two views differ for the sheathed block. Not asked for, flagged only.
- Pose B reads close to the stance in first person (tip 17.6 vs 28 deg above the horizon, hands 0.25 blocks lower and 0.18 forward); this follows from the required screen constraints, the block is a lowering/leaning forward of the sword, not a large motion.
- Third person: the sword tip ends about 20 px in front of the face on the right (x about -6 px at the tip height); the blade passes the right side of the head; the torso yaw of the rig (-7.2 deg) is not applied, so the pose is symmetric about the body axis plus the 20 deg tip azimuth. Arms are straight (no elbows), the fists coincide with the rig's grip points, fist cubes of the two arms overlap near the hilt (both on the axis, 3.2 px apart), not inspected visually.
- Stale comments left in `BcItemPose.java:123` and `BcAttackGate.java:32` ("across the chest").
- A stray log `mod/base_test.log` (baseline test run, gitignored) was left in the worktree; my attempt to delete it was blocked by the safety check.
