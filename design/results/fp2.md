# fp2: first person animations, audit and rebuild (2026-10-12)

Branch `fp2` (worktree `.claude/worktrees/fp2`, from main 80b855d). Owner of `client/model/*`, `core/combat/*` poses, `pose_override.json`, the pose harness.
Everything below marked CHECKED was seen in a real game frame (harness screenshots), the rest is stated as UNVERIFIED.

## 1. Audit (game frames, before any change)

Method: `gradlew runPhase6 -Phold=console` plus the new console commands `fp ...` (`client/dev/Phase6Fp.java`, driven by `tools/fp_console.py`).
The first person clock of the animations (`client/model/FpClock`) can be switched to a fixed step clock, so a pose is captured at an exact time
of an animation (contact sheets, same layout every run). 1280x720, GUI scale 3, FOV 70, flat world, noon, HUD on. Sheets: `blender/renders/fp2/audit_*.png`.

Finding 0, the root cause (not a number problem): the arm is not placed by where the fist has to be. It is the vanilla arm chain turned about odd pivots
(`arm.shift/rot`, `roll`, `turnSealedLeftArm`, per call-site constants). In every pose that leaves the vanilla hold, the forearm becomes a plank lying across the
screen or a cube floating in the air, and the sword pivot (grip) keeps the blade 2.35 blocks long pointing out of the picture.

| State | What the frames show (CHECKED) | Verdict |
|---|---|---|
| SEALED idle (both) | scabbard held horizontally in both hands, black scabbard leaves the picture on the left (long pole), right fist is a big cube, hilt visible | readable, crude |
| SEALED block (RMB) | scabbard rises as a huge diagonal pole from the top left corner, only one fist box in the lower middle, left hand not seen | BROKEN (the screenshot of the user) |
| SEALED draw | tip pulled out to the left along the sori arc, hands visible, then the sword swings upright | good motion, final frames inherit the bad stance |
| BASE/SHIKAI/BANKAI idle (stance, `fp_two`) | blade upright leaving the top edge, right arm rotated into a horizontal plank across the lower right (`arm.rot` -15 30 35), tsuba hidden behind it, left hand barely visible | BROKEN |
| drawn block (RMB) | blade a thin diagonal line from the lower right to the upper left, one cube at the bottom, left hand missing, big pop when the block ends (arm box flips) | BROKEN |
| block ease in / out | `block_a` .. `unblock_b`: the arm box changes orientation in 2 frames (Euler interpolation of absolute poses, forearm turn applied apart from the sword) | pops |
| strike (Better Combat swing, live sampler) | blade leaves the picture or only a streak is visible, arm plank flips, returns to the plank stance | BROKEN |
| release / shikai ability gestures | forearm planks flail, left fist leaves the picture or hangs as a cube in mid air (Rukia Tsukishiro, Byakuya barrier), effect is fine | crude |
| bankai, Byakuya free hands | two forearm planks from below, symmetric, no hand shape | acceptable, crude |
| kido | left fist cube raised to the middle of the screen, right plank | crude |
| shunpo | no pose change at all (stance), only the FX | fine, nothing to fix |
| sheathe | reverse of the draw, good | ok |
| third person block (tp_block) | not re-checked in game here (see section 5) | UNVERIFIED |
| HUD text clipped at the top left of the user's screenshot | not an anchor bug: the hotbar in that screenshot is not centred (centre at 928 of 1961 px), so the image is cropped on the left by about 100 px; `ReiatsuHud.plateOrigin` anchors at x 6 | no change |

Also found by the audit: the harness hides the hands when `hudHidden` is set (vanilla hides the hand pass with F1), so the harness keeps the HUD on.

## Finish (round 7)

Branch finish-r7 (main checkout, uncommitted merge of origin/fp2). The coordinator forbade running the game this round (a game started at the beginning was closed within minutes, no frame of it was used), so everything below is calculation and unit tests. Anything about how it LOOKS is UNVERIFIED in game.

### Found by reading and calculating
- `mod/run/pose_override.json` (git-ignored) still carried the round 5/6 numbers `fp_two.pos/rot/guard/swing/arm`, `fp_two.left.arm` and `fp_guard.sealed`. They override the new Blender-authored defaults of `TwoHandMotion` / `GuardMotion`, so a game run from this checkout would still show the OLD stance, block and strikes. Removed (backup `mod/run/pose_override.json.bak_fp_r7`); `fp_swing.live` (live strike, enabled 1), `fp_cast` and `fp_two.left.*` stay.
- Calculated with the production maths (stance, guard, sealed idle and guard, every strike key, every non free hand gesture sampled each 0.02 s with the reach limit): fists are in the picture, no arm ends in the air (the far end of the arm box is outside the picture), stretch of an arm 1.0 .. 1.8 (cap), nothing cut by the near plane. Now locked in `FpViewTest`.
- Two real flip sources in `ArmIk.place` (squaring the fist to the blade): the nearest of the four fist faces changes at 45 degrees (a 90 degree roll of the arm in one frame, texture swap, 1 px width jump of the slim arm) and the roll is ill defined when the blade runs along the forearm (the two handed hold: sine 0.28 in the stance). Measured before: the arm turned 40.7 degrees in one of six frames of the drawn block blend. Now `squareWeight` (full to 20 degrees, nothing at 45) and `parallelWeight` (nothing below sine 0.10, full from 0.30): 19.4 degrees per frame, continuous across the border (`ArmIkTest.squaringTheFistFadesOutWhereItWouldFlipTheArm`).

### Code changes
- `ArmIk`: the two weights above.
- `GuardMotion.SEALED_LEFT_S` 0.12 -> 0.16: the two fists were 0.29 blocks apart on the scabbard (barely one fist apart, left fist almost over the right one in the block); now 0.38 blocks, left fist left of the right one on the screen.
- `TwoHandMotion`: small forward arc (z -0.07, zero at wind and hit) for the two horizontal cuts H_R / H_L; the Blender keys had none (straight line in position).
- `FpMotion.limitStrike`: the strike (live animation or profile) is moved back towards the stance by one factor, found with `FpClamp.margin` (fists inside the picture, nothing cut by the near plane), falls at once, comes back over `fp_cast.clamp.release`; switch `fp_swing.clamp.enabled` 0. Before, only gestures were limited and the live strike (third person bones, up to 150 degrees and 0.55 blocks) was not.

### Failing tests (3) and why
- `SealedGuardTest.bothHandsAreInFrontOfTheBodyAndTheWholeScabbardStaysInView`: code fix (`SEALED_LEFT_S` 0.16), test unchanged except a comment.
- `TwoHandMotionTest.horizontalCutsSweep...`: TEST changed, the design changed the invariant. The authored wind of the horizontal cuts is the blade cocked up beside the head (direction x 0.25, tip at ndc +-0.64, 1.46), the old threshold 0.3 belonged to the old low wind; now the sideways direction must exceed 0.2 and the wind tip must be on its own side of the screen (>0.3). The overhead chops end diagonally down and across (direction z -0.48, y -0.40, tip in the picture at the hit): a blade pointing straight forward (z < -0.7) is a foreshortened streak, the thing goal 4 (blade stays in view) rules out. Chops: z in -0.9 .. -0.4, pointing down, tip in the picture. The stab keeps the old condition.
- `TwoHandMotionTest.arcBendsThePathOnlyBetweenWindAndHit`: CODE fix (arc for H_R / H_L), test unchanged.

### Tests added
`FpViewTest` (fists in the picture, no arm ends in the air for sealed idle/guard, stance, guard, every profile strike, every non free hand gesture; no jump of an arm or a fist between frames in the sealed and drawn block blend and in every strike at 60 Hz). Measured per frame: drawn block blend arm turn 19.4 degrees, fist move 0.055 blocks; strikes at most 43.7 degrees (V chops, the fastest cut), fist 0.33 blocks (H cuts).

### UNVERIFIED (not seen in a game frame)
Everything visual: that the sealed block reads as a guard with both hands, tsuba visible in the stance, the look of the live strike with the limiter (limit values are the gesture limits), the elongated fist at stretch 1.4..1.8 (kido, hakuteiken: the whole arm box is scaled along its axis), the new H arcs, the squaring change, the draw and sheathe (not touched), Byakuya free hands (not touched, `FreeHands` path), left hander, slim arm. The shoulder point (`ArmIk.SHOULDER_DIST`) and `arm.shoulder_offset` are not tuned. The pose harness (`fp ...`) is untouched and ready for the next game run; suggested commands: sealed idle, `fp use down` + series, `fp key draw` + series, `fp attack` + series, per state sheets with `tools/fp_sheet.py`, compared with `blender/renders/fp2/audit_*.png`.
