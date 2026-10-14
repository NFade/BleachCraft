# R12 task 2a: evaluation of the Uryu bow animation (placeholder)

Source: `D:/blockbench-mcp/plugin/player_game_rig_bow.bbmodel`, animation `senbon_slash_rl` (length 1.958 s, once). The user confirmed the
sword in the rig is a stand-in for the bow, so this file is judged as TIMING and POSE reference, not as the final prop. The second animation
`block_poses` (hold) is not part of the bow.

## What the file does (rightArm, pivot rotation x, degrees)

| t, s | rightArm x | event |
|---|---|---|
| 0.00 - 0.21 | 0 -> 25 | arm lifts |
| 0.25 - 0.42 | 42 -> 90 | arm straight forward, horizontal |
| 0.42 - 1.46 | 90 (hold, y 25 -> 7.5 -> 12.5) | ~1 s stiff hold at shoulder height |
| 1.58 | 17.5 | drop starts |
| 1.92 | 0 | rest |

`rightItem` (the stand-in) has 42 keys, a wrist swing to z -78 at 0.8 s, and no separate phase.

## Assessment against the bow requirements

1. **No draw.** The hand never returns to the face or cheek; the arm goes straight forward and stays. A bow draw is a pull back to the anchor.
2. **No tension build-up.** Power (BowDraw.power) rises over the draw time, but the animation has no progressive pull; it is a one-shot swing.
3. **Hold is 1 s of stillness at 90 degrees.** For a real aim the hold is short (0.2-0.4 s) and the arm is bent, not straight.
4. **No release.** The release is a plain drop 1.46 -> 1.92 s, with no snap forward; the bow must release fast (about 0.1 s).
5. **Stiff limbs.** The analyzer reports 1 segment per arm and leg: no elbow bend. A bow needs the elbow bent 60-90 degrees at full draw.
6. **Keyframe density.** rightItem has 42 keys in 1 s (every 0.05 s) with wobble in position (-1.5 to -2.7 on x); it will read as jitter.
7. **Arm offset.** rightArm position z goes 0 -> 5 at the hold: the shoulder is pushed forward, which the bow should not do.

## Proposed timing skeleton (to draw in the Blockbench project, for approval)

- 0.00 - 0.20 s: raise the hand to the bow (already close to this).
- 0.20 - 1.00 s: draw. Right elbow bends, the hand moves back to the anchor by the cheek (end pose at 1.00 s).
- 1.00 - 1.25 s: hold at full draw (25 ticks at 20 fps).
- 1.25 - 1.30 s: release. The hand snaps back about 0.1 s.
- 1.30 - 1.95 s: relax to rest.

Needs: split the forearm (second segment) for the elbow, drop the wobble keys on rightItem, and keep the bow itself in the left hand (the code uses the right hand only: UryuBowPose correction is for the right hand, so this needs a decision).

## Rig and code notes

- No playerAnimator JSON for the bow exists in the repo. The bow pose is driven by code (`client/model/UryuBowPose.java`: IDLE rotation -118/0/-180, DRAW -180/5.73/-180, `drawing()`, `push()`). An import would add a new JSON or replace the code pose.
- The bow mesh (uryu_bow.obj, uryu_bow_string.obj) and display transforms (uryu_cross_display.json) exist; the bow-fit task (2c) needs them.

## Status

Not verified in game. Blockbench analyzer run: 1 issue (stiff_limbs), verdict "fix the issues". No edits made to the Blockbench project yet.
