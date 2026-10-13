# First person block: the user's own pose (2026-10-13)

Replaces Pose B (see block_pose_b_impl.md) in first person. Worktree `D:\MineBleach\.claude\worktrees\block-pose-b`, FIRST PERSON files only (`TpPose.java` and `TpPoseTest.java` still contain the earlier Pose B edits of the first task; they were not touched this round). Game not run, nothing committed, `./gradlew test` green (BUILD SUCCESSFUL).

## New numbers

`FpPose(x, y, z, rotX, rotY, rotZ)`, item-renderer hand frame, blocks / degrees.

| Constant | Pose B (previous) | user's pose (new) |
|---|---|---|
| `TwoHandMotion.GUARD` (pivot `two.pivot_s`) | (-0.36, -0.25, -0.12, -41.5, 0.5, -5.3) | **(-0.574, 0.158, 0.445, -89.3, -112.5, -80.3)** |
| `GuardMotion.GUARD` (one handed fallback, pivot = grip) | (-0.317, -0.208, -0.337, -41.5, 0.5, -5.3) | **(-0.802, 0.272, 0.418, -89.3, -112.5, -80.3)** |

Before Pose B (r8 / first version): two handed (-0.32, 0.10, -0.06, -14, -16, 54), one handed (-0.22, 0.14, -0.04, -12, -22, 62).

## How the pose was derived

- The user's target in camera space (Blockbench px, eye (0, 28.8, 0), camera = Blockbench minus the eye): blade d = (-0.508, 0.860, -0.037) (azimuth 85.8 left, elevation 59.4), cutting edge (0.861, 0.509, 0). I reproduced the user's own screen numbers with the projection port first: the blade line crosses the bottom edge at 1.9 deg right of the middle, the horizon at 20.3 deg left, the top at 37.6 deg left; at 10 px along the blade it is 10.4 deg left / 17.7 deg below the horizon; hilt 56 deg below the horizon; closest approach of their (TP size) blade to the crosshair 17.7 deg. All agree with the coordinator's numbers.
- **Roll**: the model axes were taken from the code and the OBJ: model +Y is the blade, model +Z the cutting edge (`TpPose.orient` convention; checked on `rukia_sealed_drawn.obj` / `byakuya_sealed_drawn.obj`: x extent of the blade is 0.007-0.0095, z extent 0.044-0.053, so the flats face +-x and the edge/spine lie along z; the SIGN of the edge on +Z is taken from `TpPose`, not verified on the mesh). The rotation R = W * M0^T (W = target axes, M0 = display rotation of the rig) is expressible by an `FpPose` rotation exactly: blade direction error 0.02 deg, edge error 0.03 deg, flats face forward/back (|z| 0.999). So the roll IS reproduced.
- **Euler triple**: R has several equivalent `Rx Ry Rz` triples and the block blends them LINEARLY from the stance, so the choice matters. Of 54 equivalents, (-89.3, -112.5, -80.3) turns the sword 123 deg in total (direct turn 103 deg), keeps both arms 0.2 blocks in front of the camera during the whole blend and never swings the sword around the world; the plain triple (90.7, -67.5, 99.7) pushes the arm to 0.1 blocks and in the one handed case behind the camera. Left-hander mirror (y, z negated) works for any triple.
- **Shift**: Nelder-Mead search (3 DOF, Python port of FpRig/FpPose/FpGeometry/FpCentre, the port reproduced the numbers of the old code comments) so that the blade line on the screen is the user's (bottom edge crossing and horizon crossing), arms 0.17+ in front of the camera and clear of the keep-out rectangle, right fist near the bottom edge, plus the blend terms (arm depth, arm margin). The user's literal position cannot be used: the first person sword is 1.5 times larger (display scale 2.4 against 1.6 px per unit) and the vanilla arm boxes attached to it ended behind the near plane (arm depth -0.045). The literal shift scaled to blocks would put the hilt 0.38 blocks in front of the eye.

## Results (rounded poses, projection of FpGeometry, vertical FOV 70)

Two handed `TwoHandMotion.GUARD`:

| Quantity | value | rule |
|---|---|---|
| blade direction vs target | 0.02 deg | 1: within 4 deg |
| edge direction vs target | 0.03 deg | 1 |
| crossing of the bottom edge | ndc x +0.028 at 16:9 (1.97 deg right; 0.037 at 4:3, 0.021 at 21:9) | enters at the bottom middle |
| crossing of the horizon | 20.2 deg left | as the user's |
| tip | 57 deg left, 48 deg up (leaves through the upper left) | |
| closest approach to the crosshair | 17.7 deg | 2: at least 8 deg |
| blade vs keep-out rectangle (18 % x 18 % ndc) | +0.098 (4:3), +0.045 (16:9), +0.001 (21:9) | blade may be slightly negative: it is not |
| arm margin to the keep-out rectangle (FpCentre) | right 0.288, left 0.620 (all aspects) | 3: at least 0 |
| nearest arm corner in front of the camera | 0.207 blocks (right arm), 0.425 (left) | 3: near plane as before (test: >= 0.15) |
| right fist (ndc) | (-0.045, -0.910) at 16:9: visible, at the bottom edge | |
| left fist | on the blade axis (distance < 1e-4), between pommel and tsuba, ndc (0.14, -1.35) below the picture | 5 |
| tsuba | ndc (-0.117, -0.554): the hilt itself is on screen at the bottom (in the user's smaller sword it is 56 deg below the horizon) | |

Blend from the stance (w = 0..1, `GuardMotion.pose`, linear in the pose after smoothstep):
- the sword turns 123 deg in total, at most 3.6 deg per 2.5 percent of the weight (test: 100 steps, < 4 deg per step, total < 140);
- arms: nearest corner 0.207 blocks from the camera, right arm margin >= 0.233, left >= 0.620 during the whole blend;
- **the blade passes over the crosshair** (inevitable: it sweeps from 16 deg right of the middle to 20 deg left; a single linear Euler lerp cannot avoid it): minimum closest approach during the blend **1.2 deg** (at about 82 percent of the smoothstep value); the r8 guard did 0.18 deg (0.35 with coarse sampling), Pose B 16.3 (stayed on the right). The blade is inside the central rectangle for that instant: blade margin minimum -0.178 at 16:9, the r8 pose had -0.18, i.e. not worse. It takes about 0.02 s of the 0.10 s `guard.in`.
- The alternative (a sword turning 340-500 deg to avoid the crosshair) was rejected.

One handed fallback `GuardMotion.GUARD`: same blade direction and roll (error 0.02 deg), crossing of the bottom edge +0.027 ndc, horizon 20.2 deg left, closest approach 17.8 deg, blade margins +0.096 / +0.045 / +0.002; right fist (-0.05, -0.90); left hand 0.72 blocks up the blade at the upper left (ndc -0.66, 0.97); arm margins right 0.184, left 0.617 (4:3), 0.504 (16:9), 0.39 (21:9); nearest corners 0.406 / 0.173 blocks; blend from the rest pose passes within 0.04 deg of the crosshair (the old one handed pose: 0.16 deg, the test allows 0.2 deg difference), arms >= 0.17 blocks in front of the camera and margin >= 0.137 during the blend.

## Tests

- `TwoHandMotionTest.guardIsTheCrossBodyDiagonalWithTheTipUpAndLeft` (was Pose B direction): blade, edge and flats of the two handed guard; the offset-rotation assertion of `leftFistGripsTheHiltAndTheArmComesFromTheLowerLeft` re-based earlier (exact rotation of the offset, no longer a threshold on Pose B's small turn).
- `FpMotionTest.stanceIsRestAtZeroAndGuardAtOne`: the one handed GUARD axis (-0.508, 0.860) +- 0.07/0.05, in the frontal plane.
- `BlockPoseFpTest` (renamed from `BlockPoseBFpTest`, 5 tests): rule 1 direction/edge/flats (both poses); rule 2 closest approach >= 8 deg, horizon crossing, crossing of the bottom edge near the middle, blade vs keep-out rectangle >= 0 at 4:3 / 16:9 / 21:9 (both poses); rule 3 right fist near the bottom edge, FpCentre arm margin >= 0.2, arms >= 0.15 blocks in front of the camera, one handed arms; rule 4 blend smoothness (total turn < 140 deg, step < 4 deg), arm depth and margin during the blend, closest approach not below the r8 pose; rule 5 left fist on the hilt.
- Unchanged and still passing: `FpCentreTest` (guard margin > 0.08, "lower than the old one"), `FpClampTest` (guard near-plane margin > 0.1), `SealedGuardTest`, `TwoHandMotionTest.leftHandRidesOnTheHiltRigidly`, `guardPoseBlendsFromTheStanceByTheWeight`. No arm/near-plane/keep-out threshold was weakened.

## UNVERIFIED / caveats

- Nothing seen in game. The sign of the cutting edge on model +Z comes from `TpPose` (not from the mesh); if it is the opposite the roll is 180 deg off (edge down-left); change = add 180 to rotZ and recompute.
- The first person arm is a rigid body with the sword: with this roll the right forearm leaves the fist toward the lower left/rear (below the picture); only the fist is expected to show at the bottom edge. Not looked at on screen.
- The hilt is on screen at the bottom (tsuba at ndc y -0.55) instead of 56 deg below the horizon as in the user's calculation, because the FP sword is larger and sits farther from the eye; the blade line on the screen is the user's.
- Sheathed (SEALED) first person block unchanged. The dev override file `D:\MineBleach\mod\run\pose_override.json` still has `fp_two.guard.*` (old r8 pose) and would hide this pose in a dev session.
- `GuardMotion` / `TwoHandMotion` doc comments updated; stale comments elsewhere ("across the chest", `BcItemPose.java:123`, `BcAttackGate.java:32`) untouched.
